package com.manhwatranslate.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.manhwatranslate.core.image.ArgbImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImageStore(private val context: Context) {

    companion object {
        /** Pixel budget for a page held in memory; the pipeline slices taller pages anyway. */
        const val DEFAULT_MAX_PIXELS = 24_000_000
    }

    suspend fun import(uri: Uri, maxPixels: Int): ArgbImage = withContext(Dispatchers.IO) {
        decodeScaled(uri, maxPixels)
    }

    fun decodeFile(file: File, maxPixels: Int = DEFAULT_MAX_PIXELS): ArgbImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight.toLong() / (sample.toLong() * sample) > maxPixels
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IOException("Không đọc được ảnh")
        return decodeBitmapToArgb(bitmap)
    }

    fun encodePng(image: ArgbImage, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            toBitmap(image).compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    fun encodeJpeg(image: ArgbImage, file: File, quality: Int = 92) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            toBitmap(image).compress(Bitmap.CompressFormat.JPEG, quality, output)
        }
    }

    fun toBitmap(image: ArgbImage): Bitmap {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(image.pixels, 0, image.width, 0, 0, image.width, image.height)
        return bitmap
    }

    fun fromBitmap(bitmap: Bitmap): ArgbImage {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ArgbImage(bitmap.width, bitmap.height, pixels)
    }

    /** Writes to the public gallery when allowed, otherwise to the app's own exports folder. */
    suspend fun exportToGallery(image: ArgbImage, displayName: String): Uri = withContext(Dispatchers.IO) {
        val bitmap = toBitmap(image)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Manhwa Translate")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Không tạo được mục trong thư viện ảnh")
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } ?: throw IOException("Không ghi được ảnh")
                uri
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Manhwa Translate",
                )
                directory.mkdirs()
                val file = File(directory, displayName)
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                MediaStore.Images.Media.insertImage(
                    context.contentResolver,
                    file.absolutePath,
                    displayName,
                    "Manhwa Translate",
                )?.let { Uri.parse(it) } ?: Uri.fromFile(file)
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun exportToCache(image: ArgbImage, fileName: String): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, fileName)
        encodePng(image, file)
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun decodeScaled(uri: Uri, maxPixels: Int): ArgbImage {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                if (pixels > maxPixels) {
                    val scale = kotlin.math.sqrt(maxPixels.toDouble() / pixels.toDouble())
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeLegacy(uri, maxPixels)
        }
        return decodeBitmapToArgb(bitmap)
    }

    private fun decodeLegacy(uri: Uri, maxPixels: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight.toLong() / (sample * sample) > maxPixels) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Không đọc được ảnh")
        return applyExifRotation(uri, bitmap)
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun decodeBitmapToArgb(bitmap: Bitmap): ArgbImage {
        val argb = fromBitmap(bitmap)
        bitmap.recycle()
        return argb
    }
}
