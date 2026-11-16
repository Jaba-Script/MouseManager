package com.mousemanager.diagnostics

/**
 * Итог обработки одного события в журнале (D-017, D-027).
 *
 * Имена — стабильные коды для журнала и `Logcat`; человекочитаемые подписи для UI
 * живут в `strings.xml` (D-019), а не в модели.
 *
 * Семь кодов перечислены в D-017. D-027 добавляет четыре кода для штатных пропусков,
 * которые раньше неразличимо попадали в [PASSED_ERROR]: [PASSED_NO_DESCRIPTOR],
 * [PASSED_NO_TARGETS], [PASSED_KEY_UP], [PASSED_REPEAT].
 * [PASSED_NON_HARDWARE] остаётся резервом и в v1 не производится: это код для
 * событий, не связанных с аппаратным устройством. [ACTION_FAILED] — отдельная запись
 * об ошибке диспетчера (D-010, §14): клавиша уже поглощена, но действие не
 * выполнилось. Оно не является «пропуском», поэтому не входит в семейство `PASSED_*`.
 */
enum class Decision {
    CONSUMED,
    PASSED_DISARMED,
    PASSED_NO_DESCRIPTOR,
    PASSED_NO_TARGETS,
    PASSED_FOREIGN_DEVICE,
    PASSED_MODIFIER,
    PASSED_KEY_UP,
    PASSED_NO_MAPPING,
    PASSED_REPEAT,
    PASSED_NON_HARDWARE,
    PASSED_ERROR,
    ACTION_FAILED,
}
