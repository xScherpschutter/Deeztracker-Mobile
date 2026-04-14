package com.crowstar.deeztrackermobile

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.preview.PreviewPlayer
import com.crowstar.deeztrackermobile.navigation.AppNavigation
import com.crowstar.deeztrackermobile.ui.utils.LocaleHelper
import com.crowstar.deeztrackermobile.ui.theme.DeeztrackerMobileTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerController: PlayerController

    @Inject
    lateinit var previewPlayer: PreviewPlayer

    private var currentBoost by mutableStateOf(1.0f)
    private var showVolumeSlider by mutableStateOf(false)
    private var lastVolumeChangeTime by mutableStateOf(0L)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContent {
            DeeztrackerMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation()
                        
                        VolumeOverlay(
                            isVisible = showVolumeSlider,
                            boostLevel = currentBoost,
                            systemVolumeRatio = getSystemVolumeRatio()
                        )
                    }
                }
            }
            
            LaunchedEffect(lastVolumeChangeTime) {
                if (showVolumeSlider) {
                    delay(3000)
                    showVolumeSlider = false
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (action == KeyEvent.ACTION_DOWN) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                if (currentVol < maxVol) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                    showVolumeUI()
                } else {
                    if (currentBoost < 2.0f) {
                        updateBoost(currentBoost + 0.25f)
                    }
                    showVolumeUI()
                }
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (currentBoost > 1.01f) {
                    updateBoost(currentBoost - 0.25f)
                    showVolumeUI()
                } else {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                    showVolumeUI()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun updateBoost(newBoost: Float) {
        val clamped = newBoost.coerceIn(1.0f, 2.0f)
        currentBoost = clamped
        playerController.setVolume(clamped)
    }

    private fun showVolumeUI() {
        showVolumeSlider = true
        lastVolumeChangeTime = System.currentTimeMillis()
    }

    private fun getSystemVolumeRatio(): Float {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) current.toFloat() / max else 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer.release()
    }
}

@Composable
fun VolumeOverlay(
    isVisible: Boolean,
    boostLevel: Float,
    systemVolumeRatio: Float
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF2C2C2C).copy(alpha = 0.9f))
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val effectivePercent = if (boostLevel > 1.01f) {
                    (boostLevel * 100).toInt()
                } else {
                    (systemVolumeRatio * 100).toInt()
                }

                Text(
                    text = "$effectivePercent%",
                    color = Color.White,
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = (effectivePercent / 200f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(if (boostLevel > 1.01f) Color(0xFF00E5FF) else Color.White)
                    )
                }
            }
        }
    }
}
