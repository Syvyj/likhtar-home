package com.likhtar.home.core.data

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * ADB самого телевізора — з самого телевізора (127.0.0.1:5555, відкритий, коли ввімкнено
 * налагодження). Так лаунчер стає домашнім екраном без комп'ютера: першого разу телевізор
 * питає «Дозволити налагодження?», людина відповідає «Завжди». Користується лише
 * [HomeTakeover] і лише своїми сталими командами — нічого ззовні.
 *
 * Перенесено з Free My TV (github.com/simbasjungles/free-my-tv, `Adb.java`, MIT,
 * © simbasjungles) — див. NOTICE.md. Протокол:
 * https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/protocol.txt
 */
class LocalAdb private constructor(private val socket: Socket) : Closeable {
    private val input = DataInputStream(socket.getInputStream())
    private val output: OutputStream = socket.getOutputStream()
    private var nextLocalId = 1

    private class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /** Чому не вийшло — коротко, для людини. */
    class Failure(val reason: Reason) : IOException(reason.name)

    enum class Reason { DEBUGGING_OFF, NO_RESPONSE, NOT_APPROVED, WIRELESS_TLS, REJECTED, PROTOCOL }

    /** Виконує команду в shell телевізора; stdout і stderr разом. */
    fun shell(command: String): String {
        val local = nextLocalId++
        send(OPEN, local, 0, "shell:$command\u0000".toByteArray(Charsets.UTF_8))
        var remote = 0
        val result = ByteArrayOutputStream()
        while (true) {
            val m = read()
            if (m.arg1 != local) continue
            when (m.command) {
                OKAY -> remote = m.arg0
                WRTE -> {
                    result.write(m.data)
                    send(OKAY, local, m.arg0, null)
                }
                CLSE -> {
                    if (remote == 0 && m.arg0 == 0) throw Failure(Reason.PROTOCOL)
                    send(CLSE, local, m.arg0, null)
                    return result.toString("UTF-8")
                }
            }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun read(): Message {
        val header = ByteArray(24)
        input.readFully(header)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = b.int
        val arg0 = b.int
        val arg1 = b.int
        val length = b.int
        b.int // контрольна сума — новий adbd її не перевіряє
        val magic = b.int
        if (command xor -1 != magic || length < 0 || length > 1024 * 1024) throw Failure(Reason.PROTOCOL)
        val data = ByteArray(length)
        input.readFully(data)
        return Message(command, arg0, arg1, data)
    }

    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray?) {
        val length = data?.size ?: 0
        var sum = 0
        data?.forEach { sum += it.toInt() and 0xff }
        val b = ByteBuffer.allocate(24 + length).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(length).putInt(sum).putInt(command xor -1)
        data?.let(b::put)
        output.write(b.array())
        output.flush()
    }

    companion object {
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val STLS = 0x534c5453
        private const val VERSION = 0x01000001
        private const val MAX_DATA = 256 * 1024
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3

        /** Початок PKCS#1-блоку підпису SHA-1: adbd вважає токен готовим дайджестом. */
        private val SHA1_PREFIX = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

        /**
         * З'єднання й автентифікація. Якщо телевізор ще не знає нашого ключа — чекає до
         * [approvalMs], поки людина погодиться на екрані; [onApproval] — саме тоді.
         */
        fun connect(context: Context, approvalMs: Int, onApproval: () -> Unit = {}): LocalAdb {
            val keys = keys(context)
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("127.0.0.1", 5555), 3000)
            } catch (e: IOException) {
                socket.close()
                throw Failure(Reason.DEBUGGING_OFF)
            }
            val adb = LocalAdb(socket)
            try {
                socket.soTimeout = 10_000
                adb.send(CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray(Charsets.US_ASCII))
                var signed = false
                var offeredKey = false
                while (true) {
                    val m = try {
                        adb.read()
                    } catch (e: SocketTimeoutException) {
                        throw Failure(if (offeredKey) Reason.NOT_APPROVED else Reason.NO_RESPONSE)
                    }
                    when {
                        m.command == CNXN -> {
                            socket.soTimeout = 20_000
                            return adb
                        }
                        m.command == STLS -> throw Failure(Reason.WIRELESS_TLS)
                        m.command != AUTH || m.arg0 != AUTH_TOKEN -> throw Failure(Reason.PROTOCOL)
                        !signed -> {
                            adb.send(AUTH, AUTH_SIGNATURE, 0, sign(keys.private, m.data))
                            signed = true
                        }
                        !offeredKey -> {
                            // Ключ незнайомий: шлемо відкритий — телевізор питає «Дозволити налагодження?».
                            adb.send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob(keys.public))
                            offeredKey = true
                            onApproval()
                            socket.soTimeout = approvalMs
                        }
                        else -> throw Failure(Reason.REJECTED)
                    }
                }
            } catch (e: Exception) {
                adb.close()
                throw if (e is Failure) e else Failure(Reason.NO_RESPONSE)
            }
        }

        /** Ключ застосунку — у приватному сховищі, у журнал ніколи не йде. */
        @Synchronized
        private fun keys(context: Context): KeyPair {
            val privateFile = File(context.filesDir, "adbkey")
            val publicFile = File(context.filesDir, "adbkey.pub")
            val factory = KeyFactory.getInstance("RSA")
            if (privateFile.exists() && publicFile.exists()) {
                try {
                    return KeyPair(
                        factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())),
                        factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())),
                    )
                } catch (e: GeneralSecurityException) {
                    // Файл пошкоджено (зникло живлення посеред запису): новий ключ, телевізор спитає знову.
                }
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            writeAtomically(privateFile, pair.private.encoded)
            writeAtomically(publicFile, pair.public.encoded)
            return pair
        }

        private fun sign(key: PrivateKey, token: ByteArray): ByteArray {
            val block = ByteArray(256)
            block[1] = 1
            val separator = block.size - SHA1_PREFIX.size - token.size - 1
            for (i in 2 until separator) block[i] = 0xff.toByte()
            System.arraycopy(SHA1_PREFIX, 0, block, separator + 1, SHA1_PREFIX.size)
            System.arraycopy(token, 0, block, separator + 1 + SHA1_PREFIX.size, token.size)
            return Cipher.getInstance("RSA/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(block)
        }

        /** Відкритий ключ у форматі adbd: структура RSAPublicKey у base64 плюс ім'я. */
        private fun publicKeyBlob(key: PublicKey): ByteArray {
            val rsa = key as RSAPublicKey
            val n = rsa.modulus
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
            val rr = BigInteger.ONE.shiftLeft(4096).mod(n)
            val b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(64)
            b.putInt(n0inv.toInt())
            b.put(littleEndian(n, 256))
            b.put(littleEndian(rr, 256))
            b.putInt(rsa.publicExponent.toInt())
            return (Base64.encodeToString(b.array(), Base64.NO_WRAP) + " likhtar-home@tv\u0000").toByteArray(Charsets.US_ASCII)
        }

        private fun littleEndian(value: BigInteger, size: Int): ByteArray {
            val big = value.toByteArray()
            return ByteArray(size) { i -> if (i < big.size) big[big.size - 1 - i] else 0 }
        }

        /** Усе або нічого: у файл поруч, на диск, і лише тоді перейменувати. */
        private fun writeAtomically(file: File, data: ByteArray) {
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().use {
                it.write(data)
                it.fd.sync()
            }
            if (!tmp.renameTo(file)) throw IOException("could not save ${file.name}")
        }
    }
}
