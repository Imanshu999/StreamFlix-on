package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.ui.theme.*

@Composable
fun StreamFlixTopBar(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    userAvatarUrl: String,
    isGuest: Boolean,
    isAdmin: Boolean = false,
    availableGenres: List<String> = emptyList(),
    allMedia: List<MediaItem> = emptyList(),
    trendingSuggestions: List<String> = emptyList(),
    onMediaSelected: (MediaItem) -> Unit = {},
    onProfileClick: () -> Unit,
    onAdminClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Live search suggestions filtered by title or genre
    val activeSuggestions = remember(searchQuery, allMedia, trendingSuggestions) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim()
            allMedia.filter { item ->
                item.title.contains(q, ignoreCase = true) ||
                item.genres.any { it.contains(q, ignoreCase = true) } ||
                item.availableLanguages.any { it.contains(q, ignoreCase = true) }
            }.take(6)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.65f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("brand_logo_row")
            ) {
                Text(
                    text = "Stream",
                    color = Color(0xFFFFD700),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Flix",
                    color = NetflixRed,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }

            // Action Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { isSearchExpanded = !isSearchExpanded },
                    modifier = Modifier.testTag("search_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = NetflixWhite
                    )
                }

                if (isAdmin) {
                    IconButton(
                        onClick = onAdminClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(alpha = 0.2f))
                            .testTag("admin_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin CMS",
                            tint = AccentGlow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, NetflixCardBorder, CircleShape)
                        .clickable(onClick = onProfileClick)
                        .testTag("user_avatar_button"),
                    contentAlignment = Alignment.Center
                ) {
                    AppUserAvatar(
                        avatarUrl = userAvatarUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Expandable Smart Search Bar
        if (isSearchExpanded || searchQuery.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search titles, genres, Hindi/English...", color = NetflixLightGrey, fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = NetflixRed,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = NetflixLightGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NetflixWhite,
                    unfocusedTextColor = NetflixWhite,
                    focusedContainerColor = NetflixCardSurface,
                    unfocusedContainerColor = NetflixCardSurface,
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = NetflixCardBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input_field")
            )

            // When searching, display smart live dropdown
            if (activeSuggestions.isNotEmpty()) {
                Surface(
                    color = Color(0xFF1E2129),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NetflixCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        activeSuggestions.forEach { item ->
                            val versionLabel = item.availableVersions.firstOrNull()?.label
                                ?: if (item.countryName.equals("India", ignoreCase = true)) "Hindi Version" else "English Version"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSearchQueryChange("")
                                        isSearchExpanded = false
                                        onMediaSelected(item)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.posterUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp, 50.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = NetflixWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.releaseYear.toString(),
                                            color = NetflixLightGrey,
                                            fontSize = 11.sp
                                        )
                                        Surface(
                                            color = Color(0xFFFF9800).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(2.dp)
                                        ) {
                                            Text(
                                                text = versionLabel,
                                                color = Color(0xFFFFB74D),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Divider(color = NetflixCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            } else if (searchQuery.isBlank() && trendingSuggestions.isNotEmpty()) {
                // Pre-typing Trending Suggestions Bar
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Trending",
                        tint = NetflixRed,
                        modifier = Modifier.size(16.dp)
                    )
                    trendingSuggestions.take(6).forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NetflixCardSurface.copy(alpha = 0.7f))
                                .border(0.5.dp, NetflixCardBorder, RoundedCornerShape(12.dp))
                                .clickable { onSearchQueryChange(suggestion) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = suggestion,
                                color = NetflixWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (availableGenres.isNotEmpty()) {
                // Quick Genre Chips
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableGenres.take(6).forEach { genreKeyword ->
                        val isCurrent = searchQuery.equals(genreKeyword, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) NetflixRed.copy(alpha = 0.3f) else NetflixCardSurface.copy(alpha = 0.6f))
                                .clickable {
                                    if (isCurrent) onSearchQueryChange("") else onSearchQueryChange(genreKeyword)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "#$genreKeyword",
                                color = if (isCurrent) NetflixRed else NetflixLightGrey,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        Spacer(modifier = Modifier.height(6.dp))
        val categories = listOf("All", "Movies", "TV Shows", "Anime", "Cartoon", "Trailers", "Hindi", "English", "Trending", "My List")
        val categoryScrollState = rememberScrollState()

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(categoryScrollState)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) NetflixRed else Color.Black.copy(alpha = 0.45f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NetflixRed else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onCategorySelected(category) }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                            .testTag("category_chip_$category")
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) Color.White else NetflixWhite.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(28.dp))
            }
        }
    }
}
