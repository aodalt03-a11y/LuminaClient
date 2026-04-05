package com.project.lumina.client.game.module.impl.world

import android.util.Log
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
            ctx.assets.open("bedrock_blocks.json").use { i -> blocksJson.outputStream().use { o -> i.copyTo(o) } }
        }

        val pb = ProcessBuilder(bin.absolutePath, "--listen", "0.0.0.0:$listenPort", "--server", serverAddress)
        pb.directory(ctx.filesDir)
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = ctx.filesDir.absolutePath

        proxyProcess = pb.start()
        isRunning = true
        Log.d("LitematicaProxy", "Started -> $serverAddress")

        Thread {
            val reader = BufferedReader(InputStreamReader(proxyProcess!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                Log.d("LitematicaProxy", line!!)
                onLog?.invoke(line!!)
            }
            isRunning = false
            Log.d("LitematicaProxy", "Proxy stopped")
        }.start()
    }

    fun stop() {
        proxyProcess?.destroy()
        proxyProcess = null
        isRunning = false
    }
}
