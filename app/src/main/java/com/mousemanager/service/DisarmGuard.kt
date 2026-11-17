package com.mousemanager.service

/**
 * Условие срабатывания отложенного fail-safe disarm (D-030 п. 30.7, D-031 п. 31.11).
 *
 * `disarmIfEmpty` планируется только текущей сессией наблюдения за конфигом. Между
 * планированием и срабатыванием служба может успеть отключиться и подключиться снова,
 * поэтому одной проверки `generation == 0` недостаточно: callback прошлой сессии не
 * должен выключать перехват новой.
 *
 * Функция чистая и не зависит от Android, чтобы правило проверялось JUnit без
 * Robolectric.
 */
internal object DisarmGuard {

    /**
     * @param scheduledSession токен сессии, захваченный при планировании callback;
     * @param currentSession текущий токен сессии службы;
     * @param generation актуальная generation конфига движка.
     * @return `true`, только если сессия не менялась и конфиг всё ещё пуст.
     */
    fun shouldDisarm(
        scheduledSession: Long,
        currentSession: Long,
        generation: Long,
    ): Boolean = scheduledSession == currentSession && generation == 0L
}
