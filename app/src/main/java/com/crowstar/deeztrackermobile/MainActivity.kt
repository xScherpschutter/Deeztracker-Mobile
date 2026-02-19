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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowstar.deeztrackermobile.features.player.PlayerController
import com.crowstar.deeztrackermobile.features.preview.PreviewPlayer
import com.crowstar.deeztrackermobile.navigation.AppNavigation
import com.crowstar.deeztrackermobile.ui.utils.LocaleHelper
import com.crowstar.deeztrackermobile.ui.theme.DeeztrackerMobileTheme
import com.crowstar.deeztrackermobile.ui.theme.Primary
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var currentBoost by mutableStateOf(1.0f)
    private var showVolumeSlider by mutableStateOf(false)
    // We'll use a timestamp to auto-hide
    private var lastVolumeChangeTime by mutableStateOf(0L)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        PreviewPlayer.init(this)
        setContent {
            DeeztrackerMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation()
                        
                        // Volume Overlay
                        VolumeOverlay(
                            isVisible = showVolumeSlider,
                            boostLevel = currentBoost,
                            systemVolumeRatio = getSystemVolumeRatio()
                        )
                    }
                }
            }
            
            // Auto-hide logic
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
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0) // 0 flags to hide system UI?
                    showVolumeUI()
                } else {
                    // System maxed, increase boost
                    if (currentBoost < 2.0f) {
                        // Increase by 25% (0.25)
                        updateBoost(currentBoost + 0.25f)
                    }
                    showVolumeUI()
                }
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (currentBoost > 1.01f) {
                    // Decrease boost first
                    // Decrease by 25% (0.25)
                    updateBoost(currentBoost - 0.25f)
                    showVolumeUI()
                } else {
                    // Handle system volume
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
        PlayerController.getInstance(this).setVolume(clamped)
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
        PreviewPlayer.release()
    }
}

@Composable
fun VolumeOverlay(
    isVisible: Boolean,
    boostLevel: Float, // 1.0 to 2.0
    systemVolumeRatio: Float // 0.0 to 1.0
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
                .padding(end = 16.dp), // Check side
            contentAlignment = Alignment.CenterEnd
        ) {
            // Slider Container
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
                // Percentage Text
                // Calc total percentage.
                // If boost > 1.0, we are at 100% system + boost.
                // Logic: 
                // If system < 100%, percent = system * 100
                // If boost > 1.0, percent = 100 * boost
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

                // Bar
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
                            .background(if (boostLevel > 1.01f) Color(0xFF00E5FF) else Color.White) // Cyan for boost
                    )
                }
            }
        }
    }
}
