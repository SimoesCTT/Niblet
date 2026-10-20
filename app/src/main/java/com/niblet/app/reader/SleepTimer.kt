package com.niblet.app.reader

import android.os.Handler
import android.os.Looper

class SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var remainingMs = 0L
    private var ticker: Runnable? = null
    private var onTick: ((Long) -> Unit)? = null
    private var onFire: (() -> Unit)? = null

    fun start(totalMs: Long, onTick: (Long) -> Unit, onFire: () -> Unit) {
        stop()
        remainingMs = totalMs
        this.onTick = onTick; this.onFire = onFire
        ticker = object : Runnable {
            override fun run() {
                remainingMs -= 1000
                onTick(remainingMs)
                if (remainingMs <= 0) { onFire(); stop() }
                else handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(ticker!!, 1000)
    }

    fun stop() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null; remainingMs = 0L; onTick = null; onFire = null
    }
}
