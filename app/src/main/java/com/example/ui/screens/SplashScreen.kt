package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Curated high-resolution, cinema-grade movie & TV show posters for dynamic moving wall
private val POSTER_COLUMN_1 = listOf(
    "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&q=80", // Stranger Cinema
    "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?w=500&q=80", // Cyberpunk Neon
    "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80", // Sci-Fi Horizon
    "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=500&q=80", // Interstellar Cosmos
    "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80", // Action Hero
    "https://images.unsplash.com/photo-1514306191717-452ec28c7814?w=500&q=80", // Dark Knight City
    "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80", // Anime / Fantasy
    "https://images.unsplash.com/photo-1563089145-599997674d42?w=500&q=80"  // Neon Odyssey
)

private val POSTER_COLUMN_2 = listOf(
    "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=500&q=80", // Detective Noir
    "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500&q=80", // Cinema Velvet
    "https://images.unsplash.com/photo-1542204165-65bf26472b9b?w=500&q=80", // Deep Forest Mystery
    "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80", // Northern Lights Saga
    "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=500&q=80", // Vintage Film Noir
    "https://images.unsplash.com/photo-1512070679279-8988d32161be?w=500&q=80", // Midnight City Lights
    "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=500&q=80", // Action Thriller
    "https://images.unsplash.com/photo-1524985069026-dd778a71c7b4?w=500&q=80"  // Cinema Projector Beam
)

private val POSTER_COLUMN_3 = listOf(
    "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&q=80", // Movie Theater Hall
    "https://images.unsplash.com/photo-1574267432553-4b4628081c31?w=500&q=80", // Cinema Neon Pop
    "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=500&q=80", // Film Clapper
    "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=500&q=80", // Hacker Code Matrix
    "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80", // Desert Dune Horizon
    "https://images.unsplash.com/photo-1515260268569-9271009adfdb?w=500&q=80", // Cyberpunk Street
    "https://images.unsplash.com/photo-1505686994434-e3cc5abf1330?w=500&q=80", // Dramatic Silhouette
    "https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?w=500&q=80"  // Adventure Mountain
)

private val POSTER_COLUMN_4 = listOf(
    "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?w=500&q=80",
    "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&q=80",
    "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=500&q=80",
    "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
    "https://images.unsplash.com/photo-1512070679279-8988d32161be?w=500&q=80",
    "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=500&q=80",
    "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
    "https://images.unsplash.com/photo-1542204165-65bf26472b9b?w=500&q=80"
)

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hasUserInteracted by remember { mutableStateOf(false) }

    fun finishSplash() {
        if (!hasUserInteracted) {
            hasUserInteracted = true
            onSplashFinished()
        }
    }

    // Auto-advance after 4.2 seconds if user doesn't tap "Get Started"
    LaunchedEffect(Unit) {
        delay(4200)
        finishSplash()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .clickable { finishSplash() }
            .testTag("splash_screen")
    ) {
        // PART 1: DYNAMIC MOVING POSTER WALL (Rotated Diagonally ~16 degrees)
        DynamicPosterWallBackground()

        // PART 1.3: CINEMATIC GRADIENT OVERLAY (SCRIM)
        // Multi-layered scrim dimming and blurring background posters for 100% typography contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.82f),
                            NetflixBlack.copy(alpha = 0.96f)
                        ),
                        radius = 1200f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NetflixBlack.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.70f),
                            NetflixBlack.copy(alpha = 0.98f)
                        )
                    )
                )
        )

        // Subtle ambient radial glow behind brand typography
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NetflixRed.copy(alpha = 0.45f),
                            Color(0xFFFFD700).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
                .blur(50.dp)
        )

        // FOREGROUND: Central "StreamFlix" Brand, Tagline & "Get Started" Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            // Main Logo Typography
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFFFFD700), // Rich Golden Yellow
                            fontWeight = FontWeight.Black
                        )
                    ) {
                        append("Stream")
                    }
                    withStyle(
                        style = SpanStyle(
                            color = NetflixRed, // Iconic Netflix Red
                            fontWeight = FontWeight.Black
                        )
                    ) {
                        append("Flix")
                    }
                },
                fontSize = 46.sp,
                letterSpacing = 2.5.sp,
                modifier = Modifier.testTag("splash_logo_text")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle Tagline
            Text(
                text = "STREAM THE BEST IN CINEMA & TV",
                color = Color(0xFFFFD700).copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Unlimited movies, TV shows, and personalized streaming.",
                color = NetflixWhite.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // High-Impact "Get Started" Action Button
            Button(
                onClick = { finishSplash() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetflixRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier.testTag("splash_get_started_button")
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Bottom Progress Bar Indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            CircularProgressIndicator(
                color = NetflixRed,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Loading catalog...",
                color = NetflixLightGrey,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Multi-column diagonally rotated infinite moving poster wall.
 * Alternating columns continuously move in opposite directions (Column 1 UP, Column 2 DOWN, etc.) at 60 FPS.
 */
@Composable
private fun DynamicPosterWallBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "infinite_poster_wall")

    val itemHeight = 175.dp
    val itemSpacing = 12.dp
    val singleCycleHeight = (itemHeight + itemSpacing) * 8
    val density = LocalDensity.current
    val singleCycleHeightPx = with(density) { singleCycleHeight.toPx() }

    // Column 1 & 3 move UP (from 0 to -singleCycleHeightPx)
    val offsetUp by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -singleCycleHeightPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "poster_col_up"
    )

    // Column 2 & 4 move DOWN (from -singleCycleHeightPx to 0)
    val offsetDown by infiniteTransition.animateFloat(
        initialValue = -singleCycleHeightPx,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "poster_col_down"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Diagonally tilted Netflix perspective
                rotationZ = -16f
                scaleX = 1.45f
                scaleY = 1.45f
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1 (UP)
            PosterColumn(
                posters = POSTER_COLUMN_1,
                offsetY = offsetUp,
                modifier = Modifier.weight(1f)
            )

            // Column 2 (DOWN)
            PosterColumn(
                posters = POSTER_COLUMN_2,
                offsetY = offsetDown,
                modifier = Modifier.weight(1f)
            )

            // Column 3 (UP)
            PosterColumn(
                posters = POSTER_COLUMN_3,
                offsetY = offsetUp,
                modifier = Modifier.weight(1f)
            )

            // Column 4 (DOWN)
            PosterColumn(
                posters = POSTER_COLUMN_4,
                offsetY = offsetDown,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PosterColumn(
    posters: List<String>,
    offsetY: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Duplicate list 3 times to ensure zero gap during infinite wrap-around
    val loopedPosters = remember(posters) { posters + posters + posters }

    Column(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        loopedPosters.forEachIndexed { index, url ->
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1A1A))
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
