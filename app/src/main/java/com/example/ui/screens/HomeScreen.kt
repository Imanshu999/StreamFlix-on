package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    mediaItems: List<MediaItem>,
    featuredMedia: MediaItem?,
    watchHistory: List<WatchHistoryEntity>,
    favoriteIds: Set<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    userAvatarUrl: String,
    isGuest: Boolean,
    isAdmin: Boolean = false,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onResumeHistory: (WatchHistoryEntity) -> Unit,
    onMyListToggle: (String) -> Unit,
    onProfileClick: () -> Unit,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic genre extraction strictly from live Firestore items
    val availableGenres = remember(mediaItems) {
        mediaItems.flatMap { it.genres }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    // Filter items dynamically based on live Firestore data
    val filteredItems = remember(mediaItems, selectedCategory, searchQuery) {
        var list = mediaItems
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true) ||
                it.genres.any { g -> g.contains(q, ignoreCase = true) } ||
                it.cast.any { c -> c.contains(q, ignoreCase = true) } ||
                it.category.contains(q, ignoreCase = true) ||
                (it.badgeLabel?.contains(q, ignoreCase = true) == true)
            }
        } else {
            when (selectedCategory) {
                "TV Shows" -> list = list.filter { it.type == MediaType.SERIES }
                "Movies" -> list = list.filter { it.type == MediaType.MOVIE }
                "Trending" -> list = list.filter { it.category == "Trending Now" || it.isTop10 }
                "My List" -> list = list.filter { favoriteIds.contains(it.id) }
            }
        }
        list
    }

    // Dynamic grouping directly from live Firestore documents
    val trendingItems = remember(mediaItems) {
        mediaItems.filter { it.isTop10 || it.category.equals("Trending Now", ignoreCase = true) }
    }

    val dynamicCategories = remember(mediaItems) {
        mediaItems.map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Trending Now", ignoreCase = true) }
            .distinctBy { it.lowercase() }
    }

    val liveMovies = remember(mediaItems) {
        mediaItems.filter { it.type == MediaType.MOVIE }
    }

    val liveSeries = remember(mediaItems) {
        mediaItems.filter { it.type == MediaType.SERIES }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("home_screen_root")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Spacer in Search or My List mode so results clear floating top bar
            if (searchQuery.isNotBlank() || selectedCategory == "My List") {
                item {
                    Spacer(modifier = Modifier.height(95.dp))
                }
            }

            // Case 1: Search Query active or My List view active
            if (searchQuery.isNotBlank() || selectedCategory == "My List") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Results for \"$searchQuery\" (${filteredItems.size})" else "My List (${filteredItems.size})",
                            color = NetflixWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (searchQuery.isNotBlank()) {
                            TextButton(onClick = { onSearchQueryChange("") }) {
                                Text("Clear", color = NetflixRed, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (filteredItems.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No titles found matching \"$searchQuery\"" else "Your list is empty.",
                                color = NetflixWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "No matching media found in the Firestore catalog."
                                } else {
                                    "Browse titles and tap '+ My List' to save them here."
                                },
                                color = NetflixLightGrey,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            if (searchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { onSearchQueryChange("") },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                                ) {
                                    Text("Clear Search", color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            filteredItems.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { media ->
                                        MediaPosterCard(
                                            media = media,
                                            onClick = { onMediaClick(media) },
                                            modifier = Modifier.weight(1f),
                                            height = 160.dp
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (mediaItems.isEmpty()) {
                // Case 2: EMPTY INITIAL STATE / FALLBACK HANDLING
                // When Firestore database is empty, display clean elegant placeholder
                item {
                    Spacer(modifier = Modifier.height(130.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = NetflixCardSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, NetflixCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_catalog_placeholder")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(NetflixRed.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "Cloud Catalog Empty",
                                        tint = NetflixRed,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "No content available",
                                    color = NetflixWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (isAdmin) {
                                        "Catalog database is currently empty. As administrator, you can upload and publish titles directly via the Admin Panel."
                                    } else {
                                        "Check back soon for new movies, TV shows, and originals."
                                    },
                                    color = NetflixLightGrey,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center
                                )

                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(24.dp))

                                    Button(
                                        onClick = onAdminClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .testTag("empty_state_add_content_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Open Admin Panel",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Case 3: LIVE FIRESTORE CATALOG RENDERING
                // Featured Hero Banner dynamically from Firestore
                val heroMedia = featuredMedia ?: mediaItems.firstOrNull()
                if (heroMedia != null) {
                    item {
                        HeroBanner(
                            media = heroMedia,
                            isFavorite = favoriteIds.contains(heroMedia.id),
                            onPlayClick = { onPlayClick(heroMedia) },
                            onMyListToggle = { onMyListToggle(heroMedia.id) },
                            onInfoClick = { onMediaClick(heroMedia) }
                        )
                    }
                }

                // Continue Watching Section (only rendered if user has real watch history)
                if (watchHistory.isNotEmpty()) {
                    item {
                        ContinueWatchingCarousel(
                            title = "Continue Watching",
                            historyItems = watchHistory,
                            onItemClick = { item ->
                                val media = mediaItems.find { it.id == item.mediaId }
                                if (media != null) onMediaClick(media)
                            },
                            onResumeClick = onResumeHistory
                        )
                    }
                }

                // Category filter view (e.g. user selected "TV Shows" or "Movies" chip)
                if (selectedCategory == "TV Shows") {
                    if (liveSeries.isNotEmpty()) {
                        item {
                            MediaCarouselRow(
                                title = "TV Series (${liveSeries.size})",
                                items = liveSeries,
                                onItemClick = onMediaClick
                            )
                        }
                    } else {
                        item {
                            EmptyCategoryPlaceholder(
                                categoryName = "TV Shows",
                                isAdmin = isAdmin,
                                onAdminClick = onAdminClick
                            )
                        }
                    }
                } else if (selectedCategory == "Movies") {
                    if (liveMovies.isNotEmpty()) {
                        item {
                            MediaCarouselRow(
                                title = "Movies (${liveMovies.size})",
                                items = liveMovies,
                                onItemClick = onMediaClick
                            )
                        }
                    } else {
                        item {
                            EmptyCategoryPlaceholder(
                                categoryName = "Movies",
                                isAdmin = isAdmin,
                                onAdminClick = onAdminClick
                            )
                        }
                    }
                } else if (selectedCategory == "Trending") {
                    if (trendingItems.isNotEmpty()) {
                        item {
                            MediaCarouselRow(
                                title = "Trending Now",
                                items = trendingItems,
                                onItemClick = onMediaClick
                            )
                        }
                    } else {
                        item {
                            EmptyCategoryPlaceholder(
                                categoryName = "Trending",
                                isAdmin = isAdmin,
                                onAdminClick = onAdminClick
                            )
                        }
                    }
                } else {
                    // "All" view: Dynamically render rows for existing Firestore categories only
                    if (trendingItems.isNotEmpty()) {
                        item {
                            MediaCarouselRow(
                                title = "Trending Now",
                                items = trendingItems,
                                onItemClick = onMediaClick
                            )
                        }
                    }

                    // Dynamically render each category created in Firestore
                    dynamicCategories.forEach { categoryName ->
                        val categoryItems = mediaItems.filter { it.category.equals(categoryName, ignoreCase = true) }
                        if (categoryItems.isNotEmpty()) {
                            item {
                                MediaCarouselRow(
                                    title = categoryName,
                                    items = categoryItems,
                                    onItemClick = onMediaClick
                                )
                            }
                        }
                    }

                    // If no explicit categories exist besides trending, group by Type
                    if (dynamicCategories.isEmpty()) {
                        if (liveMovies.isNotEmpty()) {
                            item {
                                MediaCarouselRow(
                                    title = "Movies",
                                    items = liveMovies,
                                    onItemClick = onMediaClick
                                )
                            }
                        }
                        if (liveSeries.isNotEmpty()) {
                            item {
                                MediaCarouselRow(
                                    title = "TV Shows",
                                    items = liveSeries,
                                    onItemClick = onMediaClick
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Netflix Top Bar anchored cleanly below status bar
        StreamFlixTopBar(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            userAvatarUrl = userAvatarUrl,
            isGuest = isGuest,
            isAdmin = isAdmin,
            availableGenres = availableGenres,
            onProfileClick = onProfileClick,
            onAdminClick = onAdminClick,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun EmptyCategoryPlaceholder(
    categoryName: String,
    isAdmin: Boolean = false,
    onAdminClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No $categoryName Available",
            color = NetflixWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isAdmin) {
                "No titles found in category '$categoryName'. Add content via Admin Panel."
            } else {
                "There are currently no titles in this category. Browse other genres or check back later."
            },
            color = NetflixLightGrey,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        if (isAdmin) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAdminClick,
                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add $categoryName in Admin Panel", color = Color.White)
            }
        }
    }
}
