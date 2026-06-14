package com.shortvideocleaner.app

import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class GalleryActivity : AppCompatActivity() {

    private val photos = mutableListOf<PhotoEntry>()
    private lateinit var adapter: PhotoAdapter
    private lateinit var tvCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_gallery)

            tvCount = findViewById(R.id.tv_photo_count)
            findViewById<View>(R.id.btn_gallery_back).setOnClickListener { finish() }

            adapter = PhotoAdapter(photos)
            findViewById<RecyclerView>(R.id.rv_photos).apply {
                layoutManager = GridLayoutManager(this@GalleryActivity, 3)
                adapter = this@GalleryActivity.adapter
            }

            try {
                findViewById<StarryBackgroundView>(R.id.starry_bg)?.resumeAnimation()
            } catch (_: Exception) {}
            loadPhotos()
        } catch (e: Exception) {
            Toast.makeText(this, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadPhotos() {
        CoroutineScope(Dispatchers.IO).launch {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
            )
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
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

            withContext(Dispatchers.Main) {
                tvCount.text = "共 ${photos.size} 张"
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        findViewById<StarryBackgroundView>(R.id.starry_bg)?.pauseAnimation()
    }

    override fun onResume() {
        super.onResume()
        findViewById<StarryBackgroundView>(R.id.starry_bg)?.resumeAnimation()
    }

    data class PhotoEntry(val id: Long, val path: String, val dateAdded: Long, val size: Long)

    class PhotoAdapter(
        private val photos: List<PhotoEntry>
    ) : RecyclerView.Adapter<PhotoAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view.findViewById(R.id.iv_photo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val photo = photos[position]
            val uri = android.net.Uri.parse("${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/${photo.id}")
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val thumbnail = holder.itemView.context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
                    holder.iv.setImageBitmap(thumbnail)
                } else {
                    val input = holder.itemView.context.contentResolver.openInputStream(uri)
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    val bmp = BitmapFactory.decodeStream(input, null, opts)
                    input?.close()
                    holder.iv.setImageBitmap(bmp ?: Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888))
                }
            } catch (_: Exception) {
                holder.iv.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

        override fun getItemCount() = photos.size
    }
}
