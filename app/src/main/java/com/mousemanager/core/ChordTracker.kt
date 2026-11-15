package com.mousemanager.core

/**
 * Ширина скользящего окна pending (D-026 §26.4): 1500 мс от последнего события по этой
 * клавише. Ровно 1500 мс — pending ещё действителен, удаляется только при превышении.
 */
const val PENDING_TIMEOUT_MS = 1_500L

/**
 * Состояние горячего пути между событиями (§6.1).
 *
 * Хранит два независимых вида состояния:
 *
 * - нажатые модификаторы по устройству — только для сборки аккорда, они никогда не
 *   поглощаются (D-005);
 * - pending по паре `(descriptor, keyCode)` — время последнего события клавиши, чей DOWN
 *   уже поглощён (D-026 §26.1).
 *
 * Pending нужен, чтобы парные события поглощённой клавиши не уходили в приложение, даже
 * если между DOWN и UP выключили перехват, сменили конфиг или убрали устройство из целей.
 *
 * **Время не проверяется лениво.** Вызывающий обязан вызвать [expire] с временем текущего
 * события первым делом в `onKey` (M2.2, §6). [hasPending] и [consumePending] проверяют
 * только наличие записи, а не её свежесть: истечение — ответственность [expire].
 *
 * **Поток.** Экземпляр используется только с главного потока (`onKeyEvent`,
 * `onServiceConnected`, `onUnbind`), поэтому внутри обычные `HashMap` без синхронизации.
 */
class ChordTracker {

    // descriptor -> маска MOD_* нажатых модификаторов этого устройства
    private val heldModifiers = HashMap<String, Int>()

    // descriptor -> (keyCode -> время последнего события этой клавиши).
    // Несколько записей: два аккорда могут пересекаться по времени.
    private val pendingUp = HashMap<String, HashMap<Int, Long>>()

    /** Монотонный указатель для [expire]: назад не откатывается (§6.1). */
    private var lastExpireTime = 0L

    /**
     * Отслеживает модификатор. Событие никогда не поглощается — меняется только маска.
     *
     * Игнорируются: `descriptor == null` (устройство не идентифицировать, D-018), keyCode
     * не из [MODIFIER_KEYCODES] и action, отличный от [ACTION_DOWN]/[ACTION_UP].
     * Повторный DOWN идемпотентен: бит уже установлен.
     */
    fun onModifier(input: KeyInput) {
        val descriptor = input.deviceDescriptor ?: return
        val mask = modifierMaskOf(input.keyCode) ?: return

        when (input.action) {
            ACTION_DOWN -> {
                val current = heldModifiers[descriptor] ?: 0
                heldModifiers[descriptor] = current or mask
            }

            ACTION_UP -> {
                val current = heldModifiers[descriptor] ?: 0
                val updated = current and mask.inv()
                if (updated == 0) {
                    heldModifiers.remove(descriptor)
                } else {
                    heldModifiers[descriptor] = updated
                }
            }

            else -> Unit
        }
    }

    /** Маска модификаторов, которые трекер считает нажатыми на этом устройстве. */
    fun heldMods(descriptor: String): Int = heldModifiers[descriptor] ?: 0

    /**
     * Запоминает поглощённый DOWN. Вызывается только тогда, когда pending ещё нет:
     * повторный DOWN обрабатывается через [refreshPending] (D-026 §26.2).
     *
     * @throws IllegalStateException если запись для пары уже существует.
     */
    fun setPending(descriptor: String, keyCode: Int, time: Long) {
        val byKey = pendingUp[descriptor]
        if (byKey == null) {
            pendingUp[descriptor] = hashMapOf(keyCode to time)
            return
        }
        check(keyCode !in byKey) {
            "pending already exists for $descriptor/$keyCode; use refreshPending"
        }
        byKey[keyCode] = time
    }

    /**
     * Продлевает pending повторным DOWN/repeat. Время обновляется как `max(старое, time)`:
     * пачечная доставка не может откатить его назад (§6.1).
     *
     * @return `true`, если pending был; иначе `false` (запись не создаётся).
     */
    fun refreshPending(descriptor: String, keyCode: Int, time: Long): Boolean {
        val byKey = pendingUp[descriptor] ?: return false
        val previous = byKey[keyCode] ?: return false
        byKey[keyCode] = maxOf(previous, time)
        return true
    }

    /**
     * Забирает pending: парный UP уже поглощён. Запись удаляется.
     *
     * @return `true`, если pending был; иначе `false`.
     */
    fun consumePending(descriptor: String, keyCode: Int): Boolean {
        val byKey = pendingUp[descriptor] ?: return false
        if (byKey.remove(keyCode) == null) return false
        if (byKey.isEmpty()) pendingUp.remove(descriptor)
        return true
    }

    /**
     * Есть ли сохранённая запись для пары. Свежесть не проверяется: см. контракт класса.
     */
    fun hasPending(descriptor: String, keyCode: Int): Boolean =
        pendingUp[descriptor]?.containsKey(keyCode) == true

    /**
     * Удаляет pending старше [PENDING_TIMEOUT_MS] (строго старше: ровно 1500 мс — жив).
     *
     * Время только вперёд: `effectiveNow = max(now, lastExpireTime)`. Пачечная доставка
     * (`PROJECT_STATUS.md` §2 п. 13) не может откатить указатель назад. Пустые записи
     * устройств удаляются, чтобы карта не росла при смене `descriptor`.
     */
    fun expire(now: Long) {
        val effectiveNow = maxOf(now, lastExpireTime)
        lastExpireTime = effectiveNow

        val devices = pendingUp.values.iterator()
        while (devices.hasNext()) {
            val byKey = devices.next()
            byKey.values.removeAll { effectiveNow - it > PENDING_TIMEOUT_MS }
            if (byKey.isEmpty()) devices.remove()
        }
    }

    /**
     * Забывает модификаторы при выключении перехвата. Pending **не** трогается: уже
     * поглощённый DOWN должен получить парный UP (D-026 §26.4).
     */
    fun resetModifiers() {
        heldModifiers.clear()
    }

    /**
     * Полный сброс при подключении/отключении службы. [lastExpireTime] намеренно не
     * сбрасывается: монотонность времени важнее, чем «чистое» состояние.
     */
    fun resetAll() {
        heldModifiers.clear()
        pendingUp.clear()
    }

    private fun modifierMaskOf(keyCode: Int): Int? = when (keyCode) {
        KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT -> MOD_CTRL
        KEYCODE_SHIFT_LEFT, KEYCODE_SHIFT_RIGHT -> MOD_SHIFT
        KEYCODE_ALT_LEFT, KEYCODE_ALT_RIGHT -> MOD_ALT
        KEYCODE_META_LEFT, KEYCODE_META_RIGHT -> MOD_META
        else -> null
    }
}
