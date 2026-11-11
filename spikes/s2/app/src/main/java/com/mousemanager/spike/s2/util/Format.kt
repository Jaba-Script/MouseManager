package com.mousemanager.spike.s2.util

import android.view.KeyEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatting helpers shared by the service and the UI. */
object Format {

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)

    fun timeLabel(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(timeFormatter)

    fun hex(value: Int): String = String.format(Locale.US, "0x%08X", value)

    fun keyLabel(keyCode: Int): String =
        KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

    fun metaNames(metaState: Int): List<String> = buildList {
        if (metaState and KeyEvent.META_CTRL_ON != 0) add("CTRL")
        if (metaState and KeyEvent.META_SHIFT_ON != 0) add("SHIFT")
        if (metaState and KeyEvent.META_ALT_ON != 0) add("ALT")
        if (metaState and KeyEvent.META_META_ON != 0) add("META")
    }

    fun shortDescriptor(descriptor: String?): String = when {
        descriptor == null -> "descriptor недоступен"
        descriptor.length <= 22 -> descriptor
        else -> descriptor.take(10) + "…" + descriptor.takeLast(6)
    }
}
