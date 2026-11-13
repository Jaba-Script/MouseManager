package com.mousemanager.diagnostics

import android.util.Log
import com.mousemanager.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Журнал решений в памяти процесса (D-017): кольцевой буфер на [capacity] записей,
 * новые — первыми. Теряется при смерти процесса: для диагностики этого достаточно.
 *
 * Потокобезопасность без блокировок: [MutableStateFlow.update] атомарно заменяет
 * неизменяемый снимок, поэтому параллельные вызовы [record] не теряют и не дублируют
 * записи. Читатели видят либо старый, либо новый снимок целиком — частичных обновлений
 * нет, как и требует §13.
 *
 * [logger] вынесен в конструктор, чтобы JVM-тесты не трогали `android.util.Log`.
 * По умолчанию пишет в `Logcat` только в DEBUG, с тегом [TAG] (D-017).
 */
class DiagnosticsLog(
    private val capacity: Int = MAX_RECORDS,
    private val logger: (String) -> Unit = { message ->
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    },
) {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    private val _records = MutableStateFlow<List<KeyRecord>>(emptyList())

    /** Текущий снимок журнала: новые записи первыми, не длиннее [capacity]. */
    val records: StateFlow<List<KeyRecord>> = _records.asStateFlow()

    /** Добавляет запись в буфер и, в DEBUG, строку в `Logcat`. */
    fun record(entry: KeyRecord) {
        _records.update { current ->
            if (current.size < capacity) {
                listOf(entry) + current
            } else {
                listOf(entry) + current.subList(0, capacity - 1)
            }
        }
        logger(entry.toLogLine())
    }

    /** Очищает журнал (кнопка «Сбросить журнал», §12). */
    fun clear() {
        _records.value = emptyList()
    }

    companion object {
        /** Размер кольцевого буфера (D-017). */
        const val MAX_RECORDS = 500

        /** Единый тег приложения (AGENTS §4). */
        const val TAG = "MouseManager"
    }
}

/** Компактная строка для `Logcat`: коды, а не текст (D-017). */
private fun KeyRecord.toLogLine(): String = buildString {
    append(decision.name)
    append(" key=").append(keyCode)
    append(" scan=").append(scanCode)
    append(" action=").append(if (action == 0) "DOWN" else "UP")
    append(" repeat=").append(repeatCount)
    append(" dev=").append(deviceId)
    append(" target=").append(isTarget)
    append(" armed=").append(armed)
    append(" desc=").append(descriptor ?: "-")
}
