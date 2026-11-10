package com.likhtar.home.core.tv

import android.content.Context
import com.likhtar.home.core.data.AppsRepository
import com.likhtar.home.core.data.PhoneServer
import com.likhtar.home.core.data.escapeHtml
import com.likhtar.home.core.data.phonePageHtml
import com.likhtar.home.core.model.Distribution
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Редактор каналів ТБ Ефіру з телефона: показати / сховати, порядок, своя назва, яке джерело
 * відкривати, склеїти канали, які автомат не впізнав (дати одному назву іншого). Перебирати
 * сотні каналів пультом — мука, тож QR — у меню ТБ Ефіру й на сторінці «З телефона».
 *
 * Адреси потоків (у них токен провайдера) на сторінку не йдуть: джерела — номерами в знімку
 * [rows], зробленому при відкритті сторінки.
 */
@Singleton
class ChannelsPhoneLink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tvSource: TvSource,
    private val catalog: TvCatalogRepository,
    private val groups: TvGroupsRepository,
    private val apps: AppsRepository,
) {
    enum class Status { Waiting, Opened, Saved }

    private val _status = MutableStateFlow(Status.Waiting)
    val status: StateFlow<Status> = _status

    private class Row(
        val key: String,
        val group: String,
        /** Група без правки людини. */
        val autoGroup: String,
        /** Назва без правок людини — те, що показав би автомат. */
        val auto: String,
        val autoUrl: String,
        /** Назва вибраного джерела, без своєї назви людини. */
        val picked: String,
        val name: String,
        val sources: List<ChannelLink>,
        val chosen: Int,
        val hidden: Boolean,
        /** Приклеєні до цього каналу ключі → їхні назви. */
        val joined: List<Pair<String, String>>,
    )

    @Volatile private var rows: List<Row> = emptyList()
    @Volatile private var groupNames: List<String> = emptyList()

    private val server = PhoneServer("channels-link", PORTS, maxBody = MAX_BODY) { method, route, fields ->
        when {
            route == "" && method == "GET" -> runBlocking { _status.value = Status.Opened; page() }
            route == "save" && method == "POST" && fields.isNotEmpty() -> runBlocking {
                save(fields)
                _status.value = Status.Saved
                page(saved = true)
            }
            else -> null
        }
    }

    /** Адреса для QR; `null` — приставка не в домашній мережі. */
    fun start(): String? {
        _status.value = Status.Waiting
        return server.start()
    }

    private suspend fun load(edits: ChannelEdits): List<Row> {
        val hiddenUrls = groups.hiddenChannels.first()
        val own = runCatching { tvSource.channels() }.getOrDefault(emptyList()).filter { !it.url.contains("/video/") }
        val tv = if (Distribution.store) null else withTimeoutOrNull(CATALOG_TIMEOUT_MS) { runCatching { catalog.catalog() }.getOrNull() }
        val usable = { p: String -> p.isBlank() || apps.isInstalled(p) }
        val all = channelSources(
            own.filter { usable(it.packageName) },
            TvCatalog(tv?.channels.orEmpty().filter { usable(it.link.packageName) }, tv?.groups.orEmpty()),
            edits,
        )
        val rows = all.map { (key, links) ->
            val auto = autoPick(links)
            val pick = links.firstOrNull { it.url == edits.source[key] } ?: auto
            Row(
                key = key,
                group = (edits.groups[key] ?: pick.category).ifBlank { OTHER },
                autoGroup = pick.category.ifBlank { OTHER },
                auto = auto.name,
                autoUrl = auto.url,
                picked = pick.name,
                name = edits.names[key] ?: pick.name,
                sources = links,
                chosen = links.indexOf(pick),
                hidden = key in edits.hidden || links.any { it.url in hiddenUrls },
                joined = edits.joins.filterValues { it == key }.keys.map { alias ->
                    alias to (links.firstOrNull { channelKey(it.name) == alias }?.name ?: alias)
                },
            )
        }
        val groupRank = { g: String -> if (g == OTHER) Int.MAX_VALUE else tv?.groups?.indexOf(g)?.takeIf { it >= 0 } ?: (Int.MAX_VALUE - 1) }
        val index = edits.order.withIndex().associate { (i, k) -> k to i }
        return rows.sortedWith(compareBy({ groupRank(it.group) }))
            .sortedBy { index[it.key] ?: Int.MAX_VALUE }
            .groupBy { it.group }.values.flatten()
    }

    private suspend fun page(saved: Boolean = false): String {
        val edits = groups.channelEdits.first()
        val rows = load(edits).also { rows = it }
        val title = str(R.string.tv_phone_title)
        if (rows.isEmpty()) return phonePageHtml(title, "<h1>$title</h1><p>${str(R.string.tv_phone_empty)}</p>")
        val byGroup = rows.withIndex().groupBy { it.value.group }
        // Усі групи — і ті, що є лише в каталозі: канал можна переставити й у порожню.
        val groupNames = (byGroup.keys + rows.map { it.autoGroup }).distinct().also { this.groupNames = it }
        val playlist = str(R.string.tv_phone_source_playlist)
        fun sourceLabel(l: ChannelLink) = escapeHtml(l.name) + " · " +
            if (l.packageName.isBlank()) playlist else escapeHtml(appLabel(l.packageName))
        fun groupTitle(g: String) = if (g == OTHER) str(R.string.tv_phone_other) else escapeHtml(g)
        val moves = "<button type=\"button\" class=\"mv\" data-d=\"u\">↑</button><button type=\"button\" class=\"mv\" data-d=\"d\">↓</button>"
        val regroup = "<button type=\"button\" class=\"mv rg\" aria-label=\"${str(R.string.tv_phone_move)}\">⇄</button>"
        val groupList = if (groupNames.size < 2) "" else "<h2>${str(R.string.tv_phone_groups)}</h2>" +
            "<ul class=\"ord\" data-out=\"gorder\">" + groupNames.withIndex().joinToString("") { (gi, g) ->
                "<li data-k=\"$gi\"><label><span class=\"nb\"></span>${groupTitle(g)}</label>$moves</li>"
            } + "</ul>"
        val lists = groupNames.withIndex().joinToString("") { (gi, g) ->
            "<h2>${groupTitle(g)}</h2><input type=\"hidden\" name=\"order_$gi\"><ul class=\"ord ch\" data-out=\"order_$gi\" data-g=\"$gi\">" +
                byGroup[g].orEmpty().joinToString("") { (i, r) ->
                    val source = if (r.sources.size < 2) "" else "<select name=\"src_$i\">" + r.sources.withIndex().joinToString("") { (j, l) ->
                        "<option value=\"$j\"${if (j == r.chosen) " selected" else ""}>${sourceLabel(l)}</option>"
                    } + "</select>"
                    val joined = r.joined.withIndex().joinToString("") { (n, a) ->
                        "<label class=\"al\"><input type=\"checkbox\" name=\"un_${i}_$n\">${str(R.string.tv_phone_unjoin, a.second)}</label>"
                    }
                    "<li data-k=\"$i\" data-n=\"${escapeHtml(r.name.lowercase())}\">" +
                        "<input type=\"checkbox\" name=\"on_$i\"${if (r.hidden) "" else " checked"}>" +
                        "<input type=\"text\" name=\"name_$i\" value=\"${escapeHtml(r.name)}\" placeholder=\"${escapeHtml(r.auto)}\" list=\"names\" " +
                        "autocapitalize=\"off\" autocorrect=\"off\" spellcheck=\"false\">$regroup$moves$source$joined</li>"
                } + "</ul>"
        }
        val picker = "<dialog id=\"gd\"><p>${str(R.string.tv_phone_move)}</p>" + groupNames.withIndex().joinToString("") { (gi, g) ->
            "<button type=\"button\" class=\"btn\" data-g=\"$gi\">${groupTitle(g)}</button>"
        } + "<button type=\"button\" class=\"btn\" data-g=\"\">${str(R.string.tv_phone_cancel)}</button></dialog>" +
            "<script>(()=>{const d=document.getElementById('gd');let li=null;" +
            "document.addEventListener('click',e=>{const b=e.target.closest('.rg');if(b){li=b.closest('li');d.showModal()}});" +
            "d.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;d.close();if(!li||b.dataset.g==='')return;" +
            "const ul=document.querySelector('ul.ch[data-g=\"'+b.dataset.g+'\"]');if(ul&&ul!==li.parentNode){ul.prepend(li);li.scrollIntoView({block:'center'})}})})()</script>"
        // Великий плейлист — десятки тисяч рядків: на сервер ідуть лише змінені рядки й порядок,
        // якщо його пересували (diff=1, rows=…). Без JS форма шле все, і сервер це розуміє.
        val diff = "<input type=\"hidden\" name=\"diff\" value=\"1\"><input type=\"hidden\" name=\"rows\">" +
            "<script>(()=>{const f=document.querySelector('form');const ord=()=>[...document.querySelectorAll('.ord')].map(u=>[...u.children].map(l=>l.dataset.k).join(',')).join(';');" +
            "const start=ord();f.addEventListener('submit',()=>{const ch=[];document.querySelectorAll('.ch li').forEach(li=>{const c=[...li.querySelectorAll('input,select')];" +
            "const moved=c.some(e=>e.type==='checkbox'?e.checked!==e.defaultChecked:e.tagName==='SELECT'?[...e.options].some(o=>o.selected!==o.defaultSelected):e.value!==e.defaultValue);" +
            "if(moved)ch.push(li.dataset.k);else c.forEach(e=>e.disabled=true)});f.elements.rows.value=ch.join(',');" +
            "if(ord()===start)[...f.elements].forEach(e=>{if(e.name==='gorder'||e.name.startsWith('order_'))e.disabled=true})})})()</script>"
        val names = "<datalist id=\"names\">" + rows.joinToString("") { "<option value=\"${escapeHtml(it.name)}\">" } + "</datalist>"
        val search = "<input id=\"q\" type=\"search\" placeholder=\"${str(R.string.tv_phone_search)}\" autocomplete=\"off\">" +
            "<script>document.getElementById('q').addEventListener('input',e=>{const q=e.target.value.trim().toLowerCase();" +
            "document.querySelectorAll('.ch li').forEach(li=>li.style.display=!q||li.dataset.n.includes(q)?'':'none')})</script>"
        return phonePageHtml(
            title,
            "<h1>$title</h1><p>${str(R.string.tv_phone_intro)}</p>" +
                (if (saved) "<p class=\"ok\">✓ ${str(R.string.tv_phone_saved)}</p>" else "") +
                "<form action=\"save\" method=\"post\"><input type=\"hidden\" name=\"gorder\" value=\"${groupNames.indices.joinToString(",")}\">" +
                groupList + (if (edits.order.isEmpty()) "" else "<label class=\"sw\"><input type=\"checkbox\" name=\"auto_order\">${str(R.string.tv_phone_auto_order)}</label>") +
                "<h2>${str(R.string.tv_phone_channels)}</h2>" + search + lists + names + picker + diff +
                "<button>${str(R.string.tv_phone_save)}</button></form>",
            extraCss = CSS,
        )
    }

    private suspend fun save(f: Map<String, String>) {
        val rows = rows.takeIf { it.isNotEmpty() } ?: return
        val old = groups.channelEdits.first()
        val onPage = rows.map { it.key }.toSet()
        // Рядки, які людина чіпала; решта лишаються як були (і сховані колись за адресою — тепер за ключем).
        val touched = if (f["diff"] == "1") f["rows"].orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet() else rows.indices.toSet()
        var hidden = old.hidden - onPage + rows.withIndex().filter { (i, r) -> if (i in touched) f["on_$i"] == null else r.hidden }.map { it.value.key }
        val source = old.source.toMutableMap()
        val names = old.names.toMutableMap()
        val joins = old.joins.toMutableMap()
        rows.forEachIndexed { i, r ->
            // Автоматичний вибір не запам'ятовуємо: з'явиться HD — автомат візьме його.
            f["src_$i"]?.toIntOrNull()?.let { r.sources.getOrNull(it) }?.let { if (it.url == r.autoUrl) source.remove(r.key) else source[r.key] = it.url }
            r.joined.forEachIndexed { n, (alias, _) -> if (f["un_${i}_$n"] != null) joins.remove(alias) }
        }
        rows.forEachIndexed { i, r ->
            val typed = f["name_$i"]?.trim() ?: return@forEachIndexed
            val target = channelKey(typed).takeIf { it != r.key && it in onPage }
            when {
                // Назва іншого каналу — склеїти з ним (і все, що вже було приклеєно до цього).
                target != null -> {
                    joins.replaceAll { _, v -> if (v == r.key) target else v }
                    joins[r.key] = target
                    names.remove(r.key)
                    source.remove(r.key)
                    hidden = hidden - r.key
                }
                typed.isEmpty() || typed == r.auto || typed == r.picked -> names.remove(r.key)
                else -> names[r.key] = typed
            }
        }
        val placed = f["gorder"].orEmpty().split(',').mapNotNull { it.toIntOrNull() }.flatMap { gi ->
            f["order_$gi"].orEmpty().split(',').mapNotNull { it.toIntOrNull()?.let(rows::getOrNull) }.map { it to groupNames.getOrNull(gi) }
        }
        val pageOrder = placed.map { it.first.key }
        // Група — та, у якій канал стоїть на сторінці; збіглась з автоматичною — правки немає.
        val regroup = old.groups.toMutableMap()
        placed.forEach { (r, g) ->
            when {
                g == null -> Unit
                g == r.autoGroup -> regroup.remove(r.key)
                else -> regroup[r.key] = if (g == OTHER) "" else g
            }
        }
        // Нічого не пересували — порядок лишається автоматичним (і новий порядок груп сервера діє).
        val order = when {
            f["auto_order"] != null -> emptyList()
            f["gorder"] == null -> old.order
            pageOrder == rows.map { it.key } -> old.order
            else -> pageOrder + old.order.filter { it !in onPage }
        }
        groups.setChannelEdits(ChannelEdits(order, hidden, source, names, joins, regroup))
        // Канали, сховані колись довгим OK (за адресою), тепер живуть у правках — за ключем.
        val urls = rows.flatMap { r -> r.sources.map { it.url } }.toSet()
        groups.setHiddenChannels(groups.hiddenChannels.first() - urls)
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun str(id: Int, vararg args: Any) = escapeHtml(context.getString(id, *args))

    private companion object {
        val PORTS = 8791..8795
        /** 20 тис. каналів, усі змінені, — близько 2 МБ; із запасом. */
        const val MAX_BODY = 8 * 1024 * 1024
        const val CATALOG_TIMEOUT_MS = 8_000L
        /** Та сама назва групи без жанру, що в ТБ Ефірі. */
        const val OTHER = "Інші канали"
        const val CSS = ".ch li{flex-wrap:wrap}.ch input[type=text]{flex:1;width:auto;min-width:0;height:auto;margin:0;padding:10px;font-size:16px}" +
            ".ch select{flex-basis:100%;width:calc(100% - 32px);margin:4px 0 0 32px;padding:10px;font-size:15px}" +
            ".ch .rg{font-size:18px}dialog{background:#27231E;color:#F2EDE4;border:1px solid #6b5a3a;border-radius:16px;width:min(360px,90vw);max-height:80vh;overflow:auto}" +
            "dialog .btn{margin-top:8px}dialog::backdrop{background:rgba(0,0,0,.6)}" +
            ".ch .al{flex:0 0 100%;margin:4px 0 0 32px;font-size:14px;color:#BFB6A8}#q{margin-top:8px}"
    }
}
