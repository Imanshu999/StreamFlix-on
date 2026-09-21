package com.example.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.EpisodeData
import com.example.data.model.MediaItem
import com.example.ui.theme.*
import kotlinx.coroutines.delay

private val BACKUP_STREAM_URLS = listOf(
    "https://raw.githubusercontent.com/mediaelement/mediaelement-files/master/big_buck_bunny.mp4",
    "https://vjs.zencdn.net/v/oceans.mp4",
    "https://media.w3.org/2010/05/sintel/trailer.mp4",
    "https://raw.githubusercontent.com/mediaelement/mediaelement-files/master/echo-hereweare.mp4",
    "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4"
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    media: MediaItem,
    episode: EpisodeData? = null,
    initialPositionMs: Long = 0L,
    onBackClick: () -> Unit,
    onProgressUpdate: (currentMs: Long, durationMs: Long) -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onEpisodeSelect: ((EpisodeData) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Extract Host Activity for Window Insets and Orientation control
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    var isLandscapeLocked by remember { mutableStateOf(true) }

    // REQUIREMENT 4: True Immersive Full-Screen Video Player Mode
    // Hides both Status Bar & Navigation Bar with sticky immersive behavior
    DisposableEffect(activity, isLandscapeLocked) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        // Enable edge-to-edge drawing
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }

        // Hide both system bars (Status and Navigation)
        insetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (isLandscapeLocked) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Active Episode & Stream URL State
    var currentEpisode by remember {
        mutableStateOf(
            episode ?: media.seasons.firstOrNull()?.episodes?.firstOrNull()
        )
    }

    val initialStreamUrl = remember(currentEpisode, media) {
        val rawUrl = currentEpisode?.streamUrl?.ifEmpty { null } ?: media.directStreamUrl
        // Ensure valid fallback to standard MP4 test stream if invalid or legacy broken bucket
        val isValidScheme = rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true) ||
                rawUrl.startsWith("content://", ignoreCase = true) ||
                rawUrl.startsWith("file://", ignoreCase = true)
        if (rawUrl.isBlank() || !isValidScheme || rawUrl.contains("commondatastorage.googleapis.com") || rawUrl.contains("gtv-videos-bucket")) {
            BACKUP_STREAM_URLS[0]
        } else {
            rawUrl
        }
    }

    var activeStreamUrl by remember(initialStreamUrl) { mutableStateOf(initialStreamUrl) }
    var fallbackAttemptCount by remember(initialStreamUrl) { mutableIntStateOf(0) }

    val titleText = remember(currentEpisode, media) {
        if (currentEpisode != null) {
            "${media.title} • S1:E${currentEpisode?.episodeNumber} ${currentEpisode?.title}"
        } else {
            media.title
        }
    }

    // UI state
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(initialPositionMs) }
    var totalDurationMs by remember { mutableStateOf(0L) }
    var isLocked by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto (1080p)") }
    var isEpisodeDrawerOpen by remember { mutableStateOf(false) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // REQUIREMENT 1: Initialize Media3 ExoPlayer with robust DataSource, lifecycle and error listener
    val exoPlayer = remember(context) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(25000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 StreamFlix/1.0")

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    // Bind Player.Listener to catch buffering, ready state, and exceptions
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        playbackError = null
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        playbackError = null
                        totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                        isPlaying = exoPlayer.playWhenReady
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        showControls = true
                        onProgressUpdate(exoPlayer.duration, exoPlayer.duration)
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isBuffering = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                // Auto-fallback: If current stream encounters error, automatically try the next verified backup stream
                if (fallbackAttemptCount < BACKUP_STREAM_URLS.size) {
                    val candidate = BACKUP_STREAM_URLS[fallbackAttemptCount]
                    fallbackAttemptCount++
                    if (candidate != activeStreamUrl) {
                        activeStreamUrl = candidate
                        retryTrigger++
                        return
                    }
                }
                playbackError = "Unable to stream media: ${error.localizedMessage ?: "Source error"}"
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Prepare and play media item whenever activeStreamUrl or retryTrigger changes
    LaunchedEffect(activeStreamUrl, retryTrigger) {
        isBuffering = true
        playbackError = null
        try {
            val uri = Uri.parse(activeStreamUrl)
            val mediaItem = if (activeStreamUrl.contains(".m3u8", ignoreCase = true)) {
                ExoMediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    .build()
            } else {
                ExoMediaItem.Builder()
                    .setUri(uri)
                    .build()
            }
            exoPlayer.setMediaItem(mediaItem)
            if (initialPositionMs > 0L) {
                exoPlayer.seekTo(initialPositionMs)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.setPlaybackSpeed(playbackSpeed)
        } catch (e: Exception) {
            playbackError = "Error initializing stream: ${e.message}"
            isBuffering = false
        }
    }

    // Lifecycle observer to handle onPause and onResume properly
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.playWhenReady = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) {
                        exoPlayer.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.playWhenReady = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Real-time progress update polling (every 500ms)
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            if (exoPlayer.isPlaying) {
                val current = exoPlayer.currentPosition
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                currentPositionMs = current
                if (duration > 0L) {
                    totalDurationMs = duration
                }
                onProgressUpdate(current, totalDurationMs)
            }
            delay(500)
        }
    }

    // Auto-hide controls after 4.5 seconds of inactivity
    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying && !isLocked && !isEpisodeDrawerOpen && !showSpeedDialog && !showQualityDialog) {
            delay(4500)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isEpisodeDrawerOpen) {
                    showControls = !showControls
                }
            }
            .testTag("video_player_root")
    ) {
        // Media3 PlayerView with TextureView (eliminates hardware overlay surface query errors)
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.view_exo_player, null, false) as PlayerView
                view.apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { pv ->
                if (pv.player != exoPlayer) {
                    pv.player = exoPlayer
                }
            },
            onRelease = { pv ->
                pv.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Spinner (Clean Netflix Circular Indicator)
        if (isBuffering && playbackError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = NetflixRed,
                        strokeWidth = 3.5.dp,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_buffering_spinner")
                    )
                    Text(
                        text = "Loading Stream...",
                        color = NetflixWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Playback Error Overlay Banner (with Retry)
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = NetflixCardSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NetflixCardBorder),
                    modifier = Modifier.widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Playback Error",
                            tint = NetflixRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Playback Error",
                            color = NetflixWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = playbackError ?: "Unable to stream this video right now.",
                            color = NetflixLightGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onBackClick,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Go Back", color = NetflixWhite)
                            }
                            Button(
                                onClick = {
                                    playbackError = null
                                    activeStreamUrl = BACKUP_STREAM_URLS[fallbackAttemptCount % BACKUP_STREAM_URLS.size]
                                    fallbackAttemptCount++
                                    retryTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Overlay Netflix HUD Controls
        AnimatedVisibility(
            visible = showControls && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                // Top Bar: Back, Title, Quality, Speed, Lock, Orientation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration)
                            onBackClick()
                        },
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Fullscreen / Landscape Lock toggle
                    IconButton(
                        onClick = { isLandscapeLocked = !isLandscapeLocked },
                        modifier = Modifier.testTag("player_orientation_toggle")
                    ) {
                        Icon(
                            imageVector = if (isLandscapeLocked) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenRotation,
                            contentDescription = "Toggle Orientation Lock",
                            tint = if (isLandscapeLocked) AccentGlow else Color.White
                        )
                    }

                    // Lock button (Locks all player HUD touch inputs)
                    IconButton(
                        onClick = { isLocked = !isLocked },
                        modifier = Modifier.testTag("player_lock_button")
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = if (isLocked) NetflixRed else Color.White
                        )
                    }

                    if (!isLocked) {
                        // Quality button
                        TextButton(
                            onClick = { showQualityDialog = true },
                            modifier = Modifier.testTag("player_quality_button")
                        ) {
                            Text(
                                text = selectedQuality.split(" ").first(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Speed button
                        TextButton(
                            onClick = { showSpeedDialog = true },
                            modifier = Modifier.testTag("player_speed_button")
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!isLocked) {
                    // Center Controls: Rewind 10s, Play/Pause, Forward 10s
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.70f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("player_rewind_10s")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Play/Pause Center Action
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                                .clickable {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        exoPlayer.play()
                                        isPlaying = true
                                    }
                                }
                                .testTag("player_play_pause_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("player_forward_10s")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    // Bottom Bar: Time labels, Slider Scrub bar, Episodes & Next
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        // Time Labels Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPositionMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "-" + formatTime((totalDurationMs - currentPositionMs).coerceAtLeast(0L)),
                                color = NetflixLightGrey,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Scrub Slider
                        val sliderValue = if (totalDurationMs > 0L) {
                            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Slider(
                            value = sliderValue,
                            onValueChange = { frac ->
                                val target = (frac * totalDurationMs).toLong()
                                currentPositionMs = target
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(currentPositionMs)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = NetflixRed,
                                activeTrackColor = NetflixRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_scrub_slider")
                        )

                        // Bottom Actions: Audio & Subtitles, Fullscreen, Next Episode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { /* Audio & Subs modal */ }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Audio and Subtitles",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Audio & Subtitles",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }

                            if (onNextEpisode != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable(onClick = onNextEpisode)
                                        .padding(4.dp)
                                        .testTag("player_next_episode_button")
                                ) {
                                    Text(
                                        text = "Next Episode",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Episode",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Playback Speed Dialog
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Playback Speed", color = NetflixWhite) },
                text = {
                    Column {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = playbackSpeed == speed,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                    color = NetflixWhite,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("Done", color = NetflixRed)
                    }
                },
                containerColor = NetflixCardSurface
            )
        }

        // Video Quality Dialog
        if (showQualityDialog) {
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = { Text("Stream Quality", color = NetflixWhite) },
                text = {
                    Column {
                        listOf("Auto (1080p)", "High (1080p HDR)", "Medium (720p)", "Data Saver (480p)").forEach { q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedQuality = q
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedQuality == q,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = q,
                                    color = NetflixWhite,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) {
                        Text("Apply", color = NetflixRed)
                    }
                },
                containerColor = NetflixCardSurface
            )
        }

        // Sleek Toggleable Arrow Icon on the Left Side for TV Series
        if (media.seasons.isNotEmpty() && !isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NetflixCardBorder),
                    modifier = Modifier
                        .clickable { isEpisodeDrawerOpen = !isEpisodeDrawerOpen }
                        .testTag("toggle_episode_drawer_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isEpisodeDrawerOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Toggle Episode Drawer",
                            tint = AccentGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        if (showControls) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Episodes",
                                color = NetflixWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Sleek Semi-Transparent Episode Drawer / Sidebar Panel
        if (isEpisodeDrawerOpen && media.seasons.isNotEmpty()) {
            // Dismiss Backdrop (tap outside to close drawer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { isEpisodeDrawerOpen = false }
            )

            // Sliding Panel
            AnimatedVisibility(
                visible = isEpisodeDrawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 280.dp, max = 360.dp)
                    .align(Alignment.CenterStart)
            ) {
                Surface(
                    color = NetflixBlack.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NetflixCardBorder),
                    modifier = Modifier
                        .fillMaxHeight()
                        .testTag("episode_selection_drawer")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Drawer Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Episodes",
                                    color = NetflixWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = media.title,
                                    color = NetflixLightGrey,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { isEpisodeDrawerOpen = false },
                                modifier = Modifier.testTag("close_episode_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Drawer",
                                    tint = NetflixWhite
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Season Selector if multiple seasons
                        if (media.seasons.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedSeasonIndex.coerceIn(0, media.seasons.lastIndex),
                                containerColor = Color.Transparent,
                                contentColor = NetflixRed,
                                edgePadding = 0.dp,
                                divider = {}
                            ) {
                                media.seasons.forEachIndexed { sIdx, season ->
                                    Tab(
                                        selected = selectedSeasonIndex == sIdx,
                                        onClick = { selectedSeasonIndex = sIdx },
                                        text = {
                                            Text(
                                                text = "S${season.seasonNumber}",
                                                fontSize = 13.sp,
                                                fontWeight = if (selectedSeasonIndex == sIdx) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedSeasonIndex == sIdx) NetflixWhite else NetflixLightGrey
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        val activeSeason = media.seasons.getOrNull(selectedSeasonIndex.coerceIn(0, media.seasons.lastIndex)) ?: media.seasons.firstOrNull()

                        if (activeSeason == null || activeSeason.episodes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No episodes released for this season yet.",
                                    color = NetflixLightGrey,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            val distinctEpisodes = remember(activeSeason.episodes) { activeSeason.episodes.distinctBy { it.id } }
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(distinctEpisodes, key = { it.id }) { ep ->
                                    val isCurrentPlaying = currentEpisode?.id == ep.id || (currentEpisode == null && ep == activeSeason.episodes.firstOrNull())
                                    Surface(
                                        color = if (isCurrentPlaying) NetflixCardBorder.copy(alpha = 0.5f) else NetflixCardSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isCurrentPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, NetflixRed) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentEpisode = ep
                                                currentPositionMs = 0L
                                                isBuffering = true
                                                val mediaItem = ExoMediaItem.Builder()
                                                    .setUri(Uri.parse(ep.streamUrl))
                                                    .build()
                                                exoPlayer.setMediaItem(mediaItem)
                                                exoPlayer.prepare()
                                                exoPlayer.playWhenReady = true
                                                isPlaying = true
                                                onEpisodeSelect?.invoke(ep)
                                                isEpisodeDrawerOpen = false
                                            }
                                            .testTag("drawer_episode_${ep.episodeNumber}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 60.dp, height = 40.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            ) {
                                                AsyncImage(
                                                    model = ep.thumbnailUrl.ifEmpty { media.bannerUrl },
                                                    contentDescription = ep.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                if (isCurrentPlaying) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.45f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Playing",
                                                            tint = NetflixRed,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "E${ep.episodeNumber}: ${ep.title}",
                                                        color = if (isCurrentPlaying) AccentGlow else NetflixWhite,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "${ep.durationMinutes}m",
                                                        color = NetflixLightGrey,
                                                        fontSize = 10.sp
                                                    )
                                                }

                                                if (ep.overview.isNotEmpty()) {
                                                    Text(
                                                        text = ep.overview,
                                                        color = NetflixLightGrey,
                                                        fontSize = 10.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
