package com.manhwatranslate.translate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelDownloadManagerTest {

    private val model = ModelDownload("/data/models/m.Q4_K_M.gguf", "m.Q4_K_M.gguf", 4096L)

    @Test
    fun `a link that is not a gguf model never reaches the network`(): Unit = runBlocking {
        var contacted = false
        var discarded: String? = null
        val manager = ModelDownloadManager(
            this,
            { _, _ -> contacted = true; model },
            {},
            discard = { url -> discarded = url },
        )

        manager.start("https://example.com/LFM3-Workspace_0.1.1_linux-x64_cli.tar.gz")

        assertFalse(contacted)
        assertEquals("https://example.com/LFM3-Workspace_0.1.1_linux-x64_cli.tar.gz", discarded)
        val state = manager.state.value
        assertTrue(state is ModelDownloadState.Failed)
        assertTrue((state as ModelDownloadState.Failed).message.contains(".tar.gz"))
    }

    @Test
    fun `the live transfer is readable from outside the screen that started it`(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val manager = ModelDownloadManager(
            this,
            { _, onProgress ->
                onProgress(DownloadProgress(0.5f, 2048, 4096))
                gate.await()
                model
            },
            {},
        )

        manager.start("https://example.com/m.Q4_K_M.gguf")

        val running = withTimeout(5_000) {
            manager.state.first { it is ModelDownloadState.Running && it.progress.bytes > 0 }
        } as ModelDownloadState.Running
        assertEquals(0.5f, running.progress.fraction, 0.001f)
        assertEquals(4096L, running.progress.total)
        // A screen that was closed and reopened reads this value instead of the partial file.
        assertEquals(running, manager.state.value)
        assertTrue(manager.running)

        gate.complete(Unit)
        withTimeout(5_000) { manager.state.first { it is ModelDownloadState.Finished } }
        assertFalse(manager.running)
    }

    @Test
    fun `a finished model is saved even when no screen is watching`(): Unit = runBlocking {
        val persisted = CompletableDeferred<ModelDownload>()
        val manager = ModelDownloadManager(
            this,
            { _, _ -> model },
            { finished -> persisted.complete(finished) },
        )

        manager.start("https://example.com/m.Q4_K_M.gguf")

        assertEquals(model, withTimeout(5_000) { persisted.await() })
        withTimeout(5_000) { manager.state.first { it is ModelDownloadState.Finished } }
    }

    @Test
    fun `a failed transfer keeps its message and its partial file`(): Unit = runBlocking {
        val manager = ModelDownloadManager(
            this,
            { _, _ -> throw java.io.IOException("Tải chưa xong (100/200 byte)") },
            { throw AssertionError("a failed download must not be saved as the model") },
        )

        manager.start("https://example.com/m.Q4_K_M.gguf")

        val failed = withTimeout(5_000) {
            manager.state.first { it is ModelDownloadState.Failed }
        } as ModelDownloadState.Failed
        assertEquals("Tải chưa xong (100/200 byte)", failed.message)
        assertFalse(manager.running)
    }

    @Test
    fun `a second tap does not start a second transfer`(): Unit = runBlocking {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val manager = ModelDownloadManager(
            this,
            { _, _ ->
                calls++
                started.complete(Unit)
                gate.await()
                model
            },
            {},
        )

        manager.start("https://example.com/m.Q4_K_M.gguf")
        withTimeout(5_000) { started.await() }
        manager.start("https://example.com/m.Q4_K_M.gguf")
        manager.start("https://example.com/other.gguf")

        assertEquals(1, calls)
        gate.complete(Unit)
        withTimeout(5_000) { manager.state.first { it is ModelDownloadState.Finished } }
    }

    @Test
    fun `consuming a result returns the card to idle`() {
        val manager = ModelDownloadManager(CoroutineScope(Dispatchers.Unconfined), { _, _ -> model }, {})

        manager.start("not a url")

        assertTrue(manager.state.value is ModelDownloadState.Failed)
        manager.consume()
        assertEquals(ModelDownloadState.Idle, manager.state.value)
    }
}
