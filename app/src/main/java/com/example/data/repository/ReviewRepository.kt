package com.example.data.repository

import com.example.data.local.ReviewDao
import com.example.data.local.ReviewEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ReviewRepository(private val reviewDao: ReviewDao) {

    fun getReviewsForMedia(mediaId: String): Flow<List<ReviewEntity>> {
        return reviewDao.getReviewsForMedia(mediaId)
    }

    val allReviews: Flow<List<ReviewEntity>> = reviewDao.getAllReviews()

    suspend fun addReview(
        mediaId: String,
        userEmail: String,
        userName: String,
        userAvatarUrl: String,
        rating: Int,
        reviewText: String,
        isVerifiedUser: Boolean = true
    ) {
        val review = ReviewEntity(
            id = "rev_" + UUID.randomUUID().toString().take(8),
            mediaId = mediaId,
            userEmail = userEmail,
            userName = userName,
            userAvatarUrl = userAvatarUrl,
            rating = rating.coerceIn(1, 5),
            reviewText = reviewText.trim(),
            timestamp = System.currentTimeMillis(),
            isVerifiedUser = isVerifiedUser
        )
        reviewDao.insertReview(review)
    }

    suspend fun deleteReview(id: String) {
        reviewDao.deleteReview(id)
    }

    suspend fun purgeSeedReviews() {
        try {
            reviewDao.deleteSeedReviews()
        } catch (_: Exception) {}
    }
}
