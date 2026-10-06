package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mediaItems: List<MediaItem>,
    top10Items: List<MediaItem>,
    featuredMedia: MediaItem?,
    watchHistory: List<WatchHistoryEntity>,
    favoriteIds: Set<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchSuggestions: List<String> = emptyList(),
    userAvatarUrl: String,
    isGuest: Boolean,
    isAdmin: Boolean = false,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onResumeHistory: (WatchHistoryEntity) -> Unit,
    onMyListToggle: (String) -> Unit,
    onProfileClick: () -> Unit,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableGenres = remember(mediaItems) {
        mediaItems.flatMap { it.genres }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val filteredItems = remember(mediaItems, selectedCategory, searchQuery) {
        var list = mediaItems
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true) ||
                it.genres.any { g -> g.contains(q, ignoreCase = true) } ||
                it.cast.any { c -> c.contains(q, ignoreCase = true) } ||
                it.availableLanguages.any { l -> l.contains(q, ignoreCase = true) } ||
                it.availableVersions.any { v -> v.label.contains(q, ignoreCase = true) } ||
                it.category.contains(q, ignoreCase = true) ||
                (it.badgeLabel?.contains(q, ignoreCase = true) == true)
            }
        } else {
            when (selectedCategory) {
                "TV Shows" -> list = list.filter { it.type == MediaType.SERIES }
                "Movies" -> list = list.filter { it.type == MediaType.MOVIE }
                "Anime" -> list = list.filter { item ->
                    item.category.contains("anime", ignoreCase = true) ||
                    item.genres.any { it.contains("anime", ignoreCase = true) }
                }
                "Cartoon" -> list = list.filter { item ->
                    item.category.contains("cartoon", ignoreCase = true) ||
                    item.genres.any { it.contains("cartoon", ignoreCase = true) }
                }
                "Trailers" -> list = list.filter { it.badgeLabel?.contains("trailer", ignoreCase = true) == true }
                "Hindi" -> list = list.filter { item ->
                    item.availableLanguages.any { it.contains("hindi", ignoreCase = true) } ||
                    item.availableVersions.any { it.label.contains("hindi", ignoreCase = true) }
                }
                "English" -> list = list.filter { item ->
                    item.availableLanguages.any { it.contains("english", ignoreCase = true) } ||
                    item.availableVersions.any { it.label.contains("english", ignoreCase = true) }
                }
                "Trending" -> list = list.filter { it.category == "Trending Now" || it.isTop10 }
                "My List" -> list = list.filter { favoriteIds.contains(it.id) }
            }
        }
        list
    }

    val dynamicCategories = remember(mediaItems) {
        mediaItems.map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Trending Now", ignoreCase = true) && !it.equals("Featured Banner", ignoreCase = true) }
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
                    Spacer(modifier = Modifier.height(110.dp))
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
                                    "No matching media found in the catalog."
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
                // Skeleton loading or empty state
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
                                if (isLoading) {
                                    CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Loading fresh catalog...", color = NetflixWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                } else {
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
                                        text = "Check back soon for new movies, TV shows, and originals.",
                                        color = NetflixLightGrey,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Refresh Catalog", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // FEATURE 1: FEATURED TOP 10 HERO CAROUSEL
                val top10Display = if (top10Items.isNotEmpty()) top10Items else mediaItems.take(10)
                item {
                    Top10HeroCarousel(
                        items = top10Display,
                        onItemClick = onMediaClick,
                        onPlayClick = onPlayClick
                    )
                }

                // Dynamic Refresh Bar (Displays rotating fresh indicator)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ Real-time Dynamic Feed",
                            color = NetflixLightGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Content",
                                tint = if (isLoading) NetflixRed else NetflixLightGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Continue Watching Section
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

                // FEATURE 1: Dedicated Top 10 in India / World Horizontal Shelf
                if (top10Display.isNotEmpty()) {
                    item {
                        MediaCarouselRow(
                            title = "🏆 Top 10 Today",
                            items = top10Display,
                            onItemClick = onMediaClick
                        )
                    }
                }

                // Category filter view
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
                    val trending = mediaItems.filter { it.isTop10 || it.category == "Trending Now" }
                    if (trending.isNotEmpty()) {
                        item {
                            MediaCarouselRow(
                                title = "Trending Now",
                                items = trending,
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
                    // "All" view: Dynamically render rows for existing API categories
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

                    // Fallbacks by Type
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

        // Floating Top Bar with Smart Search & Dropdown
        StreamFlixTopBar(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            userAvatarUrl = userAvatarUrl,
            isGuest = isGuest,
            isAdmin = isAdmin,
            availableGenres = availableGenres,
            allMedia = mediaItems,
            trendingSuggestions = searchSuggestions,
            onMediaSelected = onMediaClick,
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
            text = "There are currently no titles in this category. Browse other genres or tap refresh.",
            color = NetflixLightGrey,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
