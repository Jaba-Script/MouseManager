package com.mousemanager.service

import com.mousemanager.core.RemapEngine
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide синглтон-держатель ссылки на текущий [RemapEngine] (§11).
 *
 * Владелец экземпляра — [MouseManagerService]: она создаёт движок, а [attach] и
 * [detach] вызываются жизненным циклом службы (подключение и отключение). Holder
 * нужен UI (M2.4, через `ServiceBridge`), чтобы не создавать второй движок и не
 * дублировать его состояние.
 *
 * **Только ссылка.** Holder не хранит `armed`, `config`, `foregroundPackage` или
 * снимок журнала, не создаёт [RemapEngine], не вызывает [RemapEngine.setArmed] или
 * [RemapEngine.resetState]. Единственный источник состояния — сам движок.
 *
 * **Attach/detach.** [attach] публикует экземпляр: последний вызов побеждает, а
 * повторный `attach` того же экземпляра идемпотентен. [detach] снимает экземпляр
 * только при совпадении identity: `AtomicReference.compareAndSet` не позволяет
 * старому экземпляру службы снять новый.
 *
 * **Потоки.** [engine] можно читать из любого потока: ссылка атомарна. Сам
 * [RemapEngine] и его `ChordTracker` потокобезопасными не становятся: `onKey` и
 * `resetState` по-прежнему вызываются только на главном потоке (§13). UI не должен
 * вызывать эти методы напрямую.
 */
object EngineHolder {

    private val current = AtomicReference<RemapEngine?>(null)

    /** Текущий движок или `null`, если служба не подключена. */
    val engine: RemapEngine?
        get() = current.get()

    /**
     * Публикует [engine] как текущий. Повторный вызов с тем же экземпляром
     * идемпотентен; другой экземпляр заменяет предыдущий (последний побеждает).
     */
    fun attach(engine: RemapEngine) {
        current.set(engine)
    }

    /**
     * Снимает [engine], только если он всё ещё текущий. Чужой или устаревший
     * экземпляр не может снять движок, опубликованный позже.
     */
    fun detach(engine: RemapEngine) {
        current.compareAndSet(engine, null)
    }
}
