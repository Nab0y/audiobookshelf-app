package com.audiobookshelf.app.player

import android.app.PendingIntent
import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.toRawFile
import com.audiobookshelf.app.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.*

const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

class AbMediaDescriptionAdapter constructor(private val controller: MediaControllerCompat, val playerNotificationService: PlayerNotificationService) : PlayerNotificationManager.MediaDescriptionAdapter {
  private val tag = "MediaDescriptionAdapter"

  var currentIconUri: Uri? = null
  var currentBitmap: Bitmap? = null

  private val glideOptions = RequestOptions()
    .fallback(R.drawable.icon)
    .diskCacheStrategy(DiskCacheStrategy.DATA)
  private val serviceJob = SupervisorJob()
  private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

  override fun createCurrentContentIntent(player: Player): PendingIntent? =
    controller.sessionActivity

  override fun getCurrentContentText(player: Player) = controller.metadata.description.subtitle.toString()

  override fun getCurrentContentTitle(player: Player) = controller.metadata.description.title.toString()

  override fun getCurrentLargeIcon(
    player: Player,
    callback: PlayerNotificationManager.BitmapCallback
  ): Bitmap? {
    val albumArtUri = controller.metadata.description.iconUri

    return if (currentIconUri != albumArtUri || currentBitmap == null) {
      // Cache the bitmap for the current audiobook so that successive calls to
      // `getCurrentLargeIcon` don't cause the bitmap to be recreated.
      currentIconUri = albumArtUri
      Log.d(tag, "ART $currentIconUri")
      serviceScope.launch {
        currentBitmap = albumArtUri?.let {
          resolveUriAsBitmap(it)
        }
        currentBitmap?.let { callback.onBitmap(it) }
      }
      null
    } else {
      currentBitmap
    }
  }

  private suspend fun resolveUriAsBitmap(uri: Uri): Bitmap? {
    return withContext(Dispatchers.IO) {
      // Block on downloading artwork.
      val context = playerNotificationService.getContext()

      // Fix attempt for #35 local cover crashing
      //  Convert content uri to a file and pass to Glide
      var urival:Any = uri
      if (uri.toString().startsWith("content:")) {
        val imageDocFile = DocumentFile.fromSingleUri(context, uri)
        Log.d(tag, "Converting local content url $uri to file with path ${imageDocFile?.getAbsolutePath(context)}")
        val file = imageDocFile?.toRawFile(context)
        file?.let {
          Log.d(tag, "Using local file image instead of content uri ${it.absolutePath}")
          urival = it
        }
      }

      try {
        Glide.with(context).applyDefaultRequestOptions(glideOptions)
          .asBitmap()
          .load(urival)
          .placeholder(R.drawable.icon)
          .error(R.drawable.icon)
          .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
          .get()
      } catch (e: Exception) {
        e.printStackTrace()

        Glide.with(context).applyDefaultRequestOptions(glideOptions)
          .asBitmap()
          .load(Uri.parse("android.resource://com.audiobookshelf.app/" + R.drawable.icon))
          .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
          .get()
      }
    }
  }
}
