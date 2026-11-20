package com.likhtar.home.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.likhtar.home.R
import com.likhtar.home.core.catalog.Profile
import com.likhtar.home.core.catalog.ProfileRepository
import com.likhtar.home.core.catalog.profileChipName
import com.likhtar.home.core.designsystem.FocusableSurface
import com.likhtar.home.core.designsystem.LikhtarColors
import com.likhtar.home.core.designsystem.LikhtarDialog
import com.likhtar.home.core.designsystem.LikhtarType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesState(val list: List<Profile> = emptyList(), val activeId: String = "default") {
    val chipName: String? get() = profileChipName(list, activeId)
}

/** «Хто дивиться»: профілі з сервера, активний — на приставці (як у Налаштуваннях). */
@HiltViewModel
class ProfilesViewModel @Inject constructor(private val repository: ProfileRepository) : ViewModel() {
    private val list = MutableStateFlow<List<Profile>>(emptyList())

    val state: StateFlow<ProfilesState> = combine(list, repository.activeId, ::ProfilesState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfilesState())

    init { refresh() }

    /** Новий профіль могли створити в Налаштуваннях — перечитуємо, коли відкривають панель. */
    fun refresh() {
        viewModelScope.launch { list.value = repository.all() }
    }

    fun select(id: String) {
        viewModelScope.launch { repository.setActive(id) }
    }
}

/** Бічна панель профілів: OK — перемкнути й закрити; фокус — на активному. */
@Composable
fun ProfilesPanel(state: ProfilesState, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val active = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { active.requestFocus() } }
    LikhtarDialog(onDismiss = onDismiss, width = 480.dp, side = true, armed = true) {
        Text(stringResource(R.string.profiles_title), style = LikhtarType.PageTitle, color = LikhtarColors.TextPrimary)
        Text(stringResource(R.string.profiles_hint), style = LikhtarType.Body1, color = LikhtarColors.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        val current = state.list.firstOrNull { it.id == state.activeId } ?: state.list.firstOrNull { it.id == "default" } ?: state.list.firstOrNull()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.list.forEach { profile ->
                FocusableSurface(
                    onClick = { onSelect(profile.id) },
                    modifier = Modifier.fillMaxWidth().then(if (profile == current) Modifier.focusRequester(active) else Modifier),
                    shape = RoundedCornerShape(12.dp),
                    background = if (profile == current) LikhtarColors.GlassActive.copy(alpha = 0.35f) else LikhtarColors.Surface,
                    focusedBackground = LikhtarColors.GlassActive,
                    focusedScale = 1.02f,
                ) { focused ->
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(profile.name, style = LikhtarType.TitleLead, color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextPrimary)
                        if (profile.kids) {
                            Text(stringResource(R.string.profiles_kids), style = LikhtarType.Body1, color = if (focused) LikhtarColors.OnGlassActive else LikhtarColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
