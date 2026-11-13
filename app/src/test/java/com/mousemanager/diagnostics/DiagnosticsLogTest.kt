package com.mousemanager.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Контракт журнала (D-017): порядок «новые первыми», ёмкость 500, очистка,
 * потокобезопасность и `armed=false` в M1.
 */
class DiagnosticsLogTest {

    @Test
    fun `new log is empty`() {
        val log = DiagnosticsLog(logger = {})

        assertTrue(log.records.value.isEmpty())
    }

    @Test
    fun `newest record comes first`() {
        val log = DiagnosticsLog(logger = {})

        log.record(entry(keyCode = 31, timestamp = 100L))
        log.record(entry(keyCode = 50, timestamp = 200L))

        assertEquals(listOf(50, 31), log.records.value.map { it.keyCode })
        assertEquals(listOf(200L, 100L), log.records.value.map { it.timestamp })
    }

    @Test
    fun `buffer keeps only capacity records`() {
        val log = DiagnosticsLog(capacity = 3, logger = {})

        (1..5).forEach { key -> log.record(entry(keyCode = key, timestamp = key.toLong())) }

        assertEquals(3, log.records.value.size)
        assertEquals(listOf(5, 4, 3), log.records.value.map { it.keyCode })
    }

    @Test
    fun `clear removes all records`() {
        val log = DiagnosticsLog(logger = {})
        log.record(entry(keyCode = 31))
        log.record(entry(keyCode = 50))

        log.clear()

        assertTrue(log.records.value.isEmpty())
    }

    @Test
    fun `armed is false by default`() {
        val record = entry(keyCode = 31)

        assertFalse(record.armed)
        assertFalse(record.copy(decision = Decision.CONSUMED).armed)
    }

    @Test
    fun `decision has eight stable values`() {
        assertEquals(
            listOf(
                "CONSUMED",
                "PASSED_DISARMED",
                "PASSED_FOREIGN_DEVICE",
                "PASSED_MODIFIER",
                "PASSED_NO_MAPPING",
                "PASSED_NON_HARDWARE",
                "PASSED_ERROR",
                "ACTION_FAILED",
            ),
            Decision.entries.map { it.name },
        )
        assertEquals(8, Decision.entries.size)
    }

    @Test
    fun `debug logger gets one line per record`() {
        val lines = mutableListOf<String>()
        val log = DiagnosticsLog(logger = { lines += it })

        log.record(
            entry(keyCode = 31, timestamp = 100L).copy(
                decision = Decision.PASSED_DISARMED,
                isTarget = true,
            ),
        )

        assertEquals(1, lines.size)
        val line = lines.single()
        assertTrue(line.startsWith("PASSED_DISARMED"))
        assertTrue(line.contains("key=31"))
        assertTrue(line.contains("scan=46"))
        assertTrue(line.contains("action=DOWN"))
        assertTrue(line.contains("target=true"))
        assertTrue(line.contains("armed=false"))
        assertTrue(line.contains("desc=$DESCRIPTOR"))
    }

    @Test
    fun `concurrent writers do not lose or duplicate records`() {
        val log = DiagnosticsLog(logger = {})
        val threads = 4
        val perThread = 250
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) { thread ->
            Thread {
                start.await()
                repeat(perThread) { index ->
                    val key = thread * perThread + index
                    log.record(entry(keyCode = key, timestamp = key.toLong()))
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("потоки не завершились", done.await(10, TimeUnit.SECONDS))

        val records = log.records.value
        assertEquals(DiagnosticsLog.MAX_RECORDS, records.size)
        assertEquals(records.size, records.map { it.keyCode }.distinct().size)
    }

    private fun entry(keyCode: Int, timestamp: Long = 1_000L) = KeyRecord(
        timestamp = timestamp,
        deviceId = 29,
        deviceName = "VXE R1SE+ Mouse",
        descriptor = DESCRIPTOR,
        keyCode = keyCode,
        scanCode = 46,
        metaState = 0x3000,
        repeatCount = 0,
        action = 0,
        decision = Decision.PASSED_DISARMED,
        isTarget = false,
    )

    private companion object {
        const val DESCRIPTOR = "ce5035b0dc81cc3d25dbb6f30e5e51d03dcb3992"
    }
}
