package com.manhwatranslate.translate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.ByteArrayInputStream
import java.io.File

class ModelDownloaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `streams bytes and reports progress`() {
        val bytes = ByteArray(200 * 1024) { (it % 251).toByte() }
        val target = File(tempDir, "model.gguf")
        val progress = ArrayList<DownloadProgress>()

        ModelDownloader.streamTo(target, ByteArrayInputStream(bytes), bytes.size.toLong()) { progress += it }

        assertEquals(bytes.size.toLong(), target.length())
        assertTrue(progress.isNotEmpty())
        assertEquals(bytes.size.toLong(), progress.last().bytes)
        assertEquals(1f, progress.last().fraction, 0.001f)
        assertTrue(progress.last().bytes <= progress.last().total)
    }

    @Test
    fun `reports zero fraction when length is unknown`() {
        val bytes = ByteArray(4096)
        val target = File(tempDir, "unknown.gguf")
        val progress = ArrayList<DownloadProgress>()
        ModelDownloader.streamTo(target, ByteArrayInputStream(bytes), 0L) { progress += it }
        assertEquals(0f, progress.last().fraction)
        assertEquals(bytes.size.toLong(), target.length())
    }

    @Test
    fun `keeps a gguf name from the url`() {
        assertEquals("model.gguf", ModelDownloader.fileNameFrom("https://huggingface.co/x/model", null))
        assertEquals("tiny.Q4_K_M.gguf", ModelDownloader.fileNameFrom("https://huggingface.co/x/tiny.Q4_K_M.gguf", null))
        assertEquals("dolphin.gguf", ModelDownloader.fileNameFrom("https://x/y/dolphin.gguf?download=true", null))
    }

    @Test
    fun `prefers the content disposition name`() {
        assertEquals("chosen.gguf", ModelDownloader.fileNameFrom(
            "https://x/y.gguf",
            "attachment; filename=\"chosen.gguf\"",
        ))
    }

    @Test
    fun `downloads a file over http into the target directory`() = runBlocking {
        val payload = ByteArray(300 * 1024) { (it * 31 % 253).toByte() }
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(okio.Buffer().write(payload)),
        )
        server.start()
        try {
            val url = server.url("/model").toString()
            val progress = ArrayList<DownloadProgress>()
            val model = ModelDownloader.downloadTo(
                client = ModelDownloader.client(),
                targetDir = tempDir,
                url = url,
                onProgress = { progress += it },
            )

            assertEquals(payload.size.toLong(), model.bytes)
            assertEquals(payload.size.toLong(), File(model.path).length())
            assertTrue(File(model.path).name.endsWith(".gguf"))
            assertTrue(progress.isNotEmpty())
            assertEquals(payload.size.toLong(), progress.last().bytes)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `an http error is reported instead of writing a file`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.start()
        try {
            val url = server.url("/missing.gguf").toString()
            val error = assertThrows(java.io.IOException::class.java) {
                runBlocking {
                    ModelDownloader.downloadTo(
                        client = ModelDownloader.client(),
                        targetDir = tempDir,
                        url = url,
                        onProgress = {},
                    )
                }
            }
            assertTrue(error.message.orEmpty().contains("404"))
            assertEquals(0, tempDir.listFiles()?.size ?: 0)
        } finally {
            server.shutdown()
        }
    }
}
