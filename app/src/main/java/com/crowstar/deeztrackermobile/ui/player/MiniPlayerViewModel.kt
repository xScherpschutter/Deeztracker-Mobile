package com.crowstar.deeztrackermobile.ui.player

import androidx.lifecycle.ViewModel
import com.crowstar.deeztrackermobile.features.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    val playerController: PlayerController
) : ViewModel()
