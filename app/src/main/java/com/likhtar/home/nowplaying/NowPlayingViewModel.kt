package com.likhtar.home.nowplaying

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Плашка «Що грає» у верхньому меню — над [NowPlayingSessions]. */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(private val sessions: NowPlayingSessions) : ViewModel() {
    val state = sessions.state
    fun toggle() = sessions.toggle()
    fun open() = sessions.open()
}
