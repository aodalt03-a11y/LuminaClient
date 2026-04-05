package com.project.lumina.client.game.module.impl.world

import android.util.Log
import android.widget.Toast
import com.project.lumina.client.application.AppContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object LitematicaProxyManager {

    private var proxyProcess: Process? = null
    var isRunning = false
        private set
    val listenPort = 19133
    var onLog: ((String) -> Unit)? = null

    fun start(serverAddress: String) {
        if (isRunning) stop()
        val ctx = AppContext.instance
        val bin = File(ctx.applicationInfo.nativeLibraryDir, "liblunaproxy.so")

        val blocksJson = File(ctx.filesDir, "bedrock_blocks.json")
        if (!blocksJson.exists()) {
            try {
                ctx.assets.open("bedrock_blocks.json").use { i -> blocksJson.outputStream().use { o -> i.copyTo(o) } }
            } catch (e: Exception) {
                Log.e("LunaProxy", "Failed to copy bedrock_blocks.json", e)
            }
        }

        val pb = ProcessBuilder(bin.absolutePath, "--listen", "0.0.0.0:$listenPort", "--server", serverAddress)
        pb.directory(ctx.filesDir)
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = ctx.filesDir.absolutePath

        proxyProcess = pb.start()
        isRunning = true
        Log.d("LunaProxy", "Started -> $serverAddress")
        Toast.makeText(ctx, "LunaProxy started on port $listenPort", Toast.LENGTH_SHORT).show()

        Thread {
            val reader = BufferedReader(InputStreamReader(proxyProcess!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                Log.d("LunaProxy", line!!)
                onLog?.invoke(line!!)
            }
            isRunning = false
            Log.d("LunaProxy", "Proxy stopped")
        }.start()
    }

    fun startWithToastOnFail(serverAddress: String) {
        try {
            start(serverAddress)
        } catch (e: Exception) {
            Toast.makeText(AppContext.instance, "LunaProxy failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("LunaProxy", "Failed to start", e)
        }
    }

    fun stop() {
        proxyProcess?.destroy()
        proxyProcess = null
        isRunning = false
    }
}
