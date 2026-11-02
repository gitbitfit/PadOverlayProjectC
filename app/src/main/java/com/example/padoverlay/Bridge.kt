package com.example.padoverlay

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku

/** One place for the Shizuku user-service settings (shared by the overlay and the main screen). */
object PadService {
    const val VERSION = 20

    fun args(ctx: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(ctx.packageName, InputService::class.java.name))
            .processNameSuffix("input")
            .version(VERSION)
}

/** Game window shapes for window mode. */
object Win {
    val LABELS = arrayOf("16:9", "16:10", "4:3", "3:2", "18:9", "19:9", "19.5:9", "20:9", "18.5:9")
    val NOTES = arrayOf(
        "wide bars", "wide bars", "very wide bars", "wide bars",
        "hides camera area and curved corners", "thinner bars", "thin bars", "almost full screen",
        "hides camera area and corners, a bit thinner than 18:9"
    )
    private val RATIOS = floatArrayOf(
        16f / 9f, 16f / 10f, 4f / 3f, 3f / 2f, 18f / 9f, 19f / 9f, 19.5f / 9f, 20f / 9f, 18.5f / 9f
    )

    fun ratio(index: Int): Float = RATIOS[index.coerceIn(0, RATIOS.size - 1)]

    /** [left, top, right, bottom] of the centred game window on a screen of sw x sh pixels. */
    fun bounds(sw: Int, sh: Int, ratio: Float): IntArray {
        val w: Int
        val h: Int
        if (sw >= sh) {
            w = minOf(sw.toFloat(), sh * ratio).toInt()
            h = (w / ratio).toInt()
        } else {
            h = minOf(sh.toFloat(), sw * ratio).toInt()
            w = (h / ratio).toInt()
        }
        val l = (sw - w) / 2
        val t = (sh - h) / 2
        return intArrayOf(l, t, l + w, t + h)
    }
}

/** Keeps a connection to the Shizuku helper (used by the mirror screen). */
class ServiceLink(private val ctx: Context) {
    @Volatile
    var binder: IBinder? = null
    private val waiting = ArrayList<(IBinder) -> Unit>()
    private var binding = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, b: IBinder) {
            binder = b
            val todo = ArrayList(waiting)
            waiting.clear()
            todo.forEach { it(b) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
            binding = false
        }
    }

    /** Runs [block] once the helper is connected. */
    fun with(block: (IBinder) -> Unit) {
        val b = binder
        if (b != null && b.isBinderAlive) {
            block(b)
            return
        }
        waiting.add(block)
        if (!binding) {
            binding = true
            Shizuku.bindUserService(PadService.args(ctx), conn)
        }
    }

    fun close() {
        runCatching { Shizuku.unbindUserService(PadService.args(ctx), conn, false) }
    }
}
