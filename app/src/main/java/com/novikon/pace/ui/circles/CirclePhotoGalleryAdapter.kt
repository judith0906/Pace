package com.novikon.pace.ui.circles

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import java.net.URL
import java.util.concurrent.Executors

// Adapter de la galería de fotos del grupo en el dialog de info.
// Muestra miniaturas en grid 3 columnas, al estilo WhatsApp.
class CirclePhotoGalleryAdapter(
    private val photoUrls: List<String>,
    private val onPhotoClick: (String) -> Unit
) : RecyclerView.Adapter<CirclePhotoGalleryAdapter.PhotoThumbViewHolder>() {

    private val ioExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ViewHolder de miniatura de foto en la galería del grupo.
    class PhotoThumbViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.iv_gallery_thumb)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoThumbViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_photo, parent, false)
        return PhotoThumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoThumbViewHolder, position: Int) {
        val url = photoUrls[position]
        holder.ivThumb.setImageDrawable(null)

        // Carga la miniatura en background para no bloquear el hilo principal
        ioExecutor.execute {
            runCatching {
                URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.onSuccess { bitmap ->
                if (bitmap != null) {
                    mainHandler.post { holder.ivThumb.setImageBitmap(bitmap) }
                }
            }
        }

        holder.ivThumb.setOnClickListener { onPhotoClick(url) }
    }

    override fun getItemCount(): Int = photoUrls.size
}