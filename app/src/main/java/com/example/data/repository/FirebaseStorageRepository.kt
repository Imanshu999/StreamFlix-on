package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseStorageRepository(private val context: Context) {

    private val storage: FirebaseStorage? = try {
        FirebaseStorage.getInstance()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize FirebaseStorage", e)
        null
    }

    /**
     * Uploads a file from a content Uri (image or video) directly to Firebase Cloud Storage.
     * Reports upload progress from 0.0f to 1.0f via onProgress callback.
     * Returns permanent public downloadUrl on success.
     */
    suspend fun uploadFile(
        uri: Uri,
        folder: String, // e.g., "posters", "banners", "videos", "episodes"
        mimeTypeHint: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        val st = storage ?: return Result.failure(
            IllegalStateException("Firebase Cloud Storage is not available. Please verify google-services configuration.")
        )

        return try {
            val extension = when {
                mimeTypeHint?.contains("video", ignoreCase = true) == true -> "mp4"
                mimeTypeHint?.contains("png", ignoreCase = true) == true -> "png"
                mimeTypeHint?.contains("webp", ignoreCase = true) == true -> "webp"
                else -> "jpg"
            }
            val fileName = "${folder}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
            val fileRef = st.reference.child("$folder/$fileName")

            val metadata = StorageMetadata.Builder()
                .setContentType(mimeTypeHint ?: if (folder == "videos") "video/mp4" else "image/jpeg")
                .build()

            val uploadTask = fileRef.putFile(uri, metadata)
            uploadTask.addOnProgressListener { taskSnapshot ->
                val total = taskSnapshot.totalByteCount
                if (total > 0) {
                    val progress = (taskSnapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
            }

            uploadTask.await()
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.d(TAG, "Successfully uploaded file to Firebase Storage: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for Uri: $uri to folder: $folder", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FirebaseStorageRepo"
    }
}
