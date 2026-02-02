package com.crowstar.deeztrackermobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crowstar.deeztrackermobile.ui.theme.BackgroundDark
import com.crowstar.deeztrackermobile.ui.theme.Primary
import com.crowstar.deeztrackermobile.ui.theme.SurfaceDark
import com.crowstar.deeztrackermobile.ui.theme.TextGray
import com.crowstar.deeztrackermobile.ui.utils.LanguageHelper
import com.crowstar.deeztrackermobile.ui.utils.PermissionHelper
import com.crowstar.deeztrackermobile.R
import uniffi.rusteer.DownloadQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val audioQuality by viewModel.audioQuality.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current

    var showQualityDropdown by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showLocationDropdown by remember { mutableStateOf(false) }

    val downloadLocation by viewModel.downloadLocation.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            
            // Audio Quality Setting
            Text(
                text = stringResource(R.string.settings_audio_header),
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingItem(
                title = stringResource(R.string.settings_audio_quality_title),
                value = audioQuality.name,
                onClick = { showQualityDropdown = true }
            ) {
                DropdownMenu(
                    expanded = showQualityDropdown,
                    onDismissRequest = { showQualityDropdown = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    DownloadQuality.values().forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality.name, color = Color.White) },
                            onClick = {
                                viewModel.setAudioQuality(quality)
                                showQualityDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Premium Warning
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.settings_premium_warning),
                    color = TextGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Language Setting
            Text(
                text = stringResource(R.string.settings_general_header),
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingItem(
                title = stringResource(R.string.settings_language_title),
                value = language,
                onClick = { showLanguageDropdown = true }
            ) {
                DropdownMenu(
                    expanded = showLanguageDropdown,
                    onDismissRequest = { showLanguageDropdown = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    LanguageHelper.getAllDisplayNames().forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, color = Color.White) },
                            onClick = {
                                viewModel.setLanguage(lang)
                                showLanguageDropdown = false
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Storage Setting
            Text(
                text = stringResource(R.string.settings_storage_header),
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingItem(
                title = stringResource(R.string.settings_download_location_title),
                value = if (downloadLocation == "MUSIC") stringResource(R.string.settings_location_music) else stringResource(R.string.settings_location_downloads),
                onClick = { showLocationDropdown = true }
            ) {
                DropdownMenu(
                    expanded = showLocationDropdown,
                    onDismissRequest = { showLocationDropdown = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_location_music), color = Color.White) },
                        onClick = {
                            viewModel.setDownloadLocation("MUSIC")
                            showLocationDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_location_downloads), color = Color.White) },
                        onClick = {
                            viewModel.setDownloadLocation("DOWNLOADS")
                            showLocationDropdown = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Permissions Setting
            Text(
                text = stringResource(R.string.settings_permissions_header),
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            PermissionSettingItem()

            Spacer(modifier = Modifier.weight(1f))

            // Logout Button
            Button(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFCF6679) // Reddish color for logout
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_logout), color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    value: String,
    onClick: () -> Unit,
    dropdownContent: @Composable () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = TextGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray)
            }
        }
        dropdownContent()
    }
}

@Composable
fun PermissionSettingItem() {
    val context = LocalContext.current
    val hasPermission = remember { mutableStateOf(PermissionHelper.hasAllFilesAccess()) }
    
    // Update permission status when app resumes
    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission.value = PermissionHelper.hasAllFilesAccess()
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(listener)
        onDispose {
            lifecycle?.removeObserver(listener)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_all_files_access),
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasPermission.value) {
                    stringResource(R.string.settings_permission_granted)
                } else {
                    stringResource(R.string.settings_permission_not_granted)
                },
                color = if (hasPermission.value) androidx.compose.ui.graphics.Color(0xFF4CAF50) else TextGray,
                fontSize = 12.sp
            )
        }
        
        if (!hasPermission.value) {
            Button(
                onClick = { PermissionHelper.requestAllFilesAccess(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_grant_permission),
                    fontSize = 14.sp
                )
            }
        }
    }
}
