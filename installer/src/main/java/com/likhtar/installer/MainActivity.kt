package com.likhtar.installer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.likhtar.installer.InstallerViewModel.Phase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = Palette) {
                Surface(Modifier.fillMaxSize()) { InstallerScreen() }
            }
        }
    }
}

// Палітра лаунчера: теплий графіт і бурштин (core/designsystem, Color.kt).
private val Palette = darkColorScheme(
    primary = Color(0xFFF2A93B),
    onPrimary = Color(0xFF27231E),
    background = Color(0xFF27231E),
    surface = Color(0xFF27231E),
    surfaceVariant = Color(0xFF343029),
    secondaryContainer = Color(0xFF6B5A3C),
)

@Composable
private fun InstallerScreen(vm: InstallerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.prepare_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.prepare_steps), style = MaterialTheme.typography.bodyMedium)

        OutlinedButton(onClick = vm::scan, enabled = !state.scanning && !state.busy) {
            Text(stringResource(if (state.scanning) R.string.scanning else R.string.scan))
        }
        if (state.found.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.found.forEach { ip ->
                    FilterChip(selected = ip == state.host, onClick = { vm.setHost(ip) }, label = { Text(ip) })
                }
            }
        }
        state.scanNote?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }

        OutlinedTextField(
            value = state.host,
            onValueChange = vm::setHost,
            label = { Text(stringResource(R.string.address)) },
            placeholder = { Text("192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = vm::install, enabled = state.host.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.phase == Phase.DONE) R.string.install_again else R.string.install))
        }

        when (state.phase) {
            Phase.IDLE -> Unit
            Phase.CONNECTING -> Status(R.string.status_connecting, null)
            Phase.APPROVE -> Status(R.string.status_approve, null)
            Phase.DOWNLOADING -> Status(R.string.status_downloading, state.progress)
            Phase.INSTALLING -> Status(R.string.status_installing, state.progress)
            Phase.DONE -> Text(stringResource(R.string.status_done), color = MaterialTheme.colorScheme.primary)
            Phase.FAILED -> {
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                state.errorDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun Status(text: Int, progress: Float?) {
    Text(stringResource(text))
    if (progress == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}
