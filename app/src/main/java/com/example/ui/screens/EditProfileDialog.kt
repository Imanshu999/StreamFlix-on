package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.AccountPreferences
import com.example.data.model.UserProfile
import com.example.ui.components.ALL_AVATAR_STYLES
import com.example.ui.components.AppUserAvatar
import com.example.ui.components.MinimalVectorAvatar
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSave: (displayName: String, avatarUrl: String, preferences: AccountPreferences) -> Unit,
    onSaveCameraBitmap: (Bitmap) -> String
) {
    val context = LocalContext.current

    var draftDisplayName by remember { mutableStateOf(user.displayName) }
    var draftAvatarUrl by remember { mutableStateOf(user.avatarUrl) }
    var draftPreferences by remember { mutableStateOf(user.preferences) }
    var cameraPermissionNotice by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedPath = onSaveCameraBitmap(bitmap)
            if (savedPath.isNotEmpty()) {
                draftAvatarUrl = savedPath
                cameraPermissionNotice = null
            }
        }
    }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraPermissionNotice = null
            cameraLauncher.launch(null)
        } else {
            cameraPermissionNotice = "Camera access is needed to capture a profile picture. Please enable camera permission."
        }
    }

    // Photo picker (Gallery) launcher - Google Play zero-permission compliant
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            draftAvatarUrl = uri.toString()
            cameraPermissionNotice = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .testTag("edit_profile_dialog"),
            color = NetflixCardSurface,
            border = BorderStroke(1.dp, NetflixCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Bar with Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(NetflixRed.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Edit Profile & Preferences",
                                color = NetflixWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manage identity, camera avatar, and stream playback",
                                color = NetflixLightGrey,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_edit_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NetflixLightGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Profile Picture Studio
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NetflixBlack),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NetflixCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Profile Picture",
                                color = NetflixWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Large preview circle
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(2.5.dp, NetflixRed, CircleShape)
                                    .padding(2.dp)
                                    .testTag("preview_profile_avatar"),
                                contentAlignment = Alignment.Center
                            ) {
                                AppUserAvatar(
                                    avatarUrl = draftAvatarUrl,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons: Take Photo & Gallery
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            cameraPermissionNotice = null
                                            cameraLauncher.launch(null)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("camera_take_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Camera",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixWhite),
                                    border = BorderStroke(1.dp, NetflixCardBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("gallery_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Gallery",
                                        tint = NetflixLightGrey,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Gallery",
                                        color = NetflixWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Camera Permission feedback if denied
                            if (cameraPermissionNotice != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = DangerRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = DangerRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = cameraPermissionNotice ?: "",
                                            color = DangerRed,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Preset Avatars Selector
                            Text(
                                text = "Or select a preset avatar:",
                                color = NetflixLightGrey,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(ALL_AVATAR_STYLES, key = { it.id }) { style ->
                                    val isSelected = draftAvatarUrl == style.id
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { draftAvatarUrl = style.id }
                                            .testTag("edit_avatar_${style.id}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) NetflixRed else NetflixCardBorder,
                                                    shape = CircleShape
                                                )
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            MinimalVectorAvatar(
                                                avatarId = style.id,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = style.label.split(" ").firstOrNull() ?: "",
                                            color = if (isSelected) NetflixWhite else NetflixLightGrey,
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Display Name Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NetflixBlack),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NetflixCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Display Name",
                                color = NetflixWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This name appears across profiles, reviews, and player interfaces.",
                                color = NetflixLightGrey,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = draftDisplayName,
                                onValueChange = {
                                    draftDisplayName = it
                                    if (nameError != null && it.isNotBlank()) nameError = null
                                },
                                label = { Text("Your Name", color = NetflixLightGrey) },
                                singleLine = true,
                                isError = nameError != null,
                                supportingText = {
                                    if (nameError != null) {
                                        Text(nameError ?: "", color = DangerRed)
                                    } else {
                                        Text("${draftDisplayName.length}/30 characters", color = NetflixLightGrey)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = NetflixLightGrey
                                    )
                                },
                                trailingIcon = {
                                    if (draftDisplayName.isNotEmpty()) {
                                        IconButton(onClick = { draftDisplayName = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = NetflixLightGrey
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = NetflixWhite,
                                    unfocusedTextColor = NetflixWhite,
                                    focusedContainerColor = Color(0xFF141414),
                                    unfocusedContainerColor = Color(0xFF141414),
                                    focusedBorderColor = NetflixRed,
                                    unfocusedBorderColor = NetflixCardBorder,
                                    errorBorderColor = DangerRed
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_display_name_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Account Preferences Management
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NetflixBlack),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NetflixCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Account & Playback Preferences",
                                color = NetflixWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Customize streaming quality, subtitle options, data usage, and sync",
                                color = NetflixLightGrey,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // A. Streaming Video Quality
                            Text(
                                text = "Streaming Quality",
                                color = NetflixWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val qualityOptions = listOf(
                                "Auto (Recommended)",
                                "Ultra HD 4K",
                                "Full HD 1080p",
                                "Data Saver (480p)"
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                qualityOptions.forEach { quality ->
                                    val isSelected = draftPreferences.streamingQuality == quality
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            draftPreferences = draftPreferences.copy(streamingQuality = quality)
                                        },
                                        label = {
                                            Text(
                                                text = quality,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NetflixRed,
                                            selectedLabelColor = Color.White,
                                            containerColor = NetflixCardSurface,
                                            labelColor = NetflixLightGrey
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) NetflixRed else NetflixCardBorder
                                        ),
                                        modifier = Modifier.testTag("pref_quality_${quality.take(4)}")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // B. Preferred Audio Language
                            Text(
                                text = "Preferred Audio Language",
                                color = NetflixWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val audioOptions = listOf(
                                "English (Original)",
                                "Spanish",
                                "French",
                                "German",
                                "Japanese",
                                "Hindi"
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                audioOptions.forEach { lang ->
                                    val isSelected = draftPreferences.audioLanguage == lang
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            draftPreferences = draftPreferences.copy(audioLanguage = lang)
                                        },
                                        label = {
                                            Text(
                                                text = lang,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NetflixRed,
                                            selectedLabelColor = Color.White,
                                            containerColor = NetflixCardSurface,
                                            labelColor = NetflixLightGrey
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) NetflixRed else NetflixCardBorder
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = NetflixCardBorder)
                            Spacer(modifier = Modifier.height(14.dp))

                            // C. Subtitle Preferences Switch & Language
                            PreferenceToggleRow(
                                title = "Subtitles & Closed Captions",
                                subtitle = "Display subtitles on media player playback",
                                icon = Icons.Default.ClosedCaption,
                                isChecked = draftPreferences.subtitlesEnabled,
                                onCheckedChange = { checked ->
                                    draftPreferences = draftPreferences.copy(subtitlesEnabled = checked)
                                },
                                testTag = "toggle_subtitles"
                            )

                            if (draftPreferences.subtitlesEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Subtitle Language",
                                    color = NetflixLightGrey,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("English [CC]", "Spanish", "French", "German").forEach { subLang ->
                                        val isSelected = draftPreferences.subtitleLanguage == subLang
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                draftPreferences = draftPreferences.copy(subtitleLanguage = subLang)
                                            },
                                            label = { Text(subLang, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NetflixRed,
                                                selectedLabelColor = Color.White,
                                                containerColor = NetflixCardSurface,
                                                labelColor = NetflixLightGrey
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) NetflixRed else NetflixCardBorder
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // D. Playback Preferences
                            PreferenceToggleRow(
                                title = "Autoplay Next Episode",
                                subtitle = "Automatically queue following episode in series",
                                icon = Icons.Default.PlayCircle,
                                isChecked = draftPreferences.autoplayNextEpisode,
                                onCheckedChange = { checked ->
                                    draftPreferences = draftPreferences.copy(autoplayNextEpisode = checked)
                                },
                                testTag = "toggle_autoplay"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // E. Data & Download Preferences
                            PreferenceToggleRow(
                                title = "Download on Wi-Fi Only",
                                subtitle = "Save mobile data plan when saving offline media",
                                icon = Icons.Default.Wifi,
                                isChecked = draftPreferences.wifiOnlyDownloads,
                                onCheckedChange = { checked ->
                                    draftPreferences = draftPreferences.copy(wifiOnlyDownloads = checked)
                                },
                                testTag = "toggle_wifi_only"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PreferenceToggleRow(
                                title = "Allow Cellular Streaming",
                                subtitle = "Stream video when connected via mobile carrier data",
                                icon = Icons.Default.SignalCellularAlt,
                                isChecked = draftPreferences.cellularStreamingAllowed,
                                onCheckedChange = { checked ->
                                    draftPreferences = draftPreferences.copy(cellularStreamingAllowed = checked)
                                },
                                testTag = "toggle_cellular_stream"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // F. Cloud Sync & Notifications
                            PreferenceToggleRow(
                                title = "Cloud Watch Progress Sync",
                                subtitle = "Synchronize resumes points with Firebase Firestore",
                                icon = Icons.Default.CloudSync,
                                isChecked = draftPreferences.cloudSyncEnabled,
                                onCheckedChange = { checked ->
                                    draftPreferences = draftPreferences.copy(cloudSyncEnabled = checked)
                                },
                                testTag = "toggle_cloud_sync"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            PreferenceToggleRow(
                                title = "Push Notifications",
                                subtitle = "Get notified about new releases and finished downloads",
                                icon = Icons.Default.Notifications,
                                isChecked = draftPreferences.pushNotificationsEnabled,
                                onCheckedChange = { checked ->
                                    draftPreferences = draftPreferences.copy(pushNotificationsEnabled = checked)
                                },
                                testTag = "toggle_notifications"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Save & Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NetflixLightGrey),
                        border = BorderStroke(1.dp, NetflixCardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("cancel_edit_profile_button")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val trimmedName = draftDisplayName.trim()
                            if (trimmedName.isEmpty()) {
                                nameError = "Display name cannot be empty"
                                return@Button
                            }
                            onSave(trimmedName, draftAvatarUrl, draftPreferences)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp)
                            .testTag("save_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Changes",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isChecked) NetflixRed else NetflixLightGrey,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NetflixWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = NetflixLightGrey,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NetflixRed,
                uncheckedThumbColor = NetflixLightGrey,
                uncheckedTrackColor = NetflixCardBorder
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
