package com.mousemanager.core

/**
 * Единственное место принятия решений (§6).
 *
 * Экземпляр живёт в службе (M2.3), но сам движок не знает про Android: на вход приходит
 * [KeyInput], на выходе — [Verdict]. Всё состояние между событиями — [ChordTracker] и три
 * `@Volatile`-поля, которые пишутся из других потоков и только читаются на горячем пути.
 *
 * **Порядок проверок строгий** (§6, D-018, D-026). Парные события уже поглощённого DOWN
 * проверяются до `armed`/`targets`: выключение перехвата, смена конфига или удаление
 * устройства из целей не должны оставлять половину аккорда.
 *
 * До M2.3 служба выставляет только [config] (и пока не выставляет), поэтому [armed]
 * остаётся `false`, а [onKey] возвращает `Pass(DISARMED)` — поведение M1 сохраняется.
 */
class RemapEngine {

    /**
     * Текущий снимок конфигурации (D-008). Публикуется целиком, без частичных обновлений.
     * По умолчанию — fail-safe [RuntimeConfig.EMPTY] (D-018).
     */
    @Volatile
    var config: RuntimeConfig = RuntimeConfig.EMPTY

    /**
     * Пакет приложения на переднем плане для привязок профилей (§8). `null` — пакет
     * неизвестен, используется активный профиль. Заполняется службой в M2.3.
     */
    @Volatile
    var foregroundPackage: String? = null

    /**
     * Глобальный выключатель (D-006). Не хранится в DataStore: при старте процесса и
     * каждом подключении службы — `false`.
     */
    @Volatile
    var armed: Boolean = false
        private set

    /** Включает и выключает перехват (D-006). Значение читается на горячем пути. */
    fun setArmed(value: Boolean) {
        armed = value
    }

    /**
     * Сбрасывает состояние трекера при подключении/отключении службы (D-018 §18.3).
     * Pending и модификаторы забываются: события прежней сессии больше не придут.
     */
    fun resetState() {
        tracker.resetAll()
    }

    private val tracker = ChordTracker()

    /**
     * Решение по одному событию (§6). Без I/O, блокировок и JSON: только чтение
     * `@Volatile`-ссылки и поиски в `HashMap`.
     */
    fun onKey(input: KeyInput): Verdict {
        val cfg = config
        tracker.expire(input.eventTime)

        // 1. Без descriptor устройство не идентифицировать.
        val descriptor = input.deviceDescriptor
            ?: return Verdict.Pass(PassReason.NO_DESCRIPTOR)

        // 2. Парный UP уже поглощённого DOWN — до armed/targets/конфига (D-026).
        if (input.action == ACTION_UP &&
            tracker.consumePending(descriptor, input.keyCode)
        ) {
            return Verdict.Consume
        }

        // 3. Повторный DOWN/repeat при pending — Consume без dispatch (D-026).
        if (input.action == ACTION_DOWN &&
            tracker.refreshPending(descriptor, input.keyCode, input.eventTime)
        ) {
            return Verdict.Consume
        }

        // 4. Глобальный выключатель. Pending не трогаем: он должен дожить до UP.
        if (!armed) {
            tracker.resetModifiers()
            return Verdict.Pass(PassReason.DISARMED)
        }

        // 5. Идентификация устройства.
        if (cfg.targets.isEmpty()) return Verdict.Pass(PassReason.EMPTY_TARGETS)
        if (descriptor !in cfg.targets) return Verdict.Pass(PassReason.FOREIGN_DEVICE)

        // 6. Модификаторы не поглощаются никогда (D-005) — только отслеживание.
        if (input.keyCode in MODIFIER_KEYCODES) {
            tracker.onModifier(input)
            return Verdict.Pass(PassReason.MODIFIER)
        }

        // 7. Остальные UP и MULTIPLE пропускаем.
        if (input.action != ACTION_DOWN) return Verdict.Pass(PassReason.KEY_UP)

        // 8. Собираем аккорд: metaState — основной источник, трекер — страховка (§6.2).
        val chord = ChordKey(
            modsMask = modsOf(input.metaState) or tracker.heldMods(descriptor),
            keyCode = input.keyCode,
        )

        // 9. Маппинг: привязка приложения → активный профиль → default (§8).
        val action = cfg.resolve(descriptor, chord, foregroundPackage)
            ?: return Verdict.Pass(PassReason.NO_MAPPING)

        // 10. Repeat без pending не поглощаем: его первый DOWN видело приложение.
        if (input.repeatCount > 0) return Verdict.Pass(PassReason.REPEAT_OF_UNKNOWN)

        // 11. Поглощаем DOWN и запоминаем пару для UP.
        tracker.setPending(descriptor, input.keyCode, input.eventTime)
        return Verdict.ConsumeAndDispatch(action)
    }
}
