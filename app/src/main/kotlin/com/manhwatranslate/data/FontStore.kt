package com.manhwatranslate.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Stores a font the user imported so the app can draw translations with it. */
class FontStore(private val context: Context) {

    private val directory: File by lazy {
        File(context.filesDir, "fonts").apply { mkdirs() }
    }

    private val fontFile: File by lazy { File(directory, "custom.ttf") }

    fun hasCustomFont(): Boolean = fontFile.exists()

    fun loadTypeface(): Typeface? {
        if (!fontFile.exists()) return null
        return runCatching { Typeface.createFromFile(fontFile) }.getOrNull()
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Không mở được tệp font")
        stream.use { input ->
            fontFile.outputStream().use { output -> input.copyTo(output) }
        }
        typefaceOrFail()
        displayName(uri)
    }

    fun clear() {
        runCatching { fontFile.delete() }
    }

    private fun typefaceOrFail(): Typeface =
        runCatching { Typeface.createFromFile(fontFile) }.getOrElse {
            fontFile.delete()
            throw IllegalStateException("Tệp không phải font TrueType/OpenType hợp lệ")
        }

    private fun displayName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Font tuỳ chỉnh"
}
