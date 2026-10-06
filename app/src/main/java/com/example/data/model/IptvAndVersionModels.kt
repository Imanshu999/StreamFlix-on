package com.example.data.model

data class MediaVersion(
    val id: String = "",
    val label: String = "",
    val languageCode: String = "",
    val isDub: Boolean = false,
    val isOriginal: Boolean = false,
    val streamUrl: String = ""
)

data class LiveChannel(
    val id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val category: String = "General",
    val streamUrl: String = "",
    val resolution: String = "",
    val country: String = "IN",
    val language: String = "Hindi"
)
