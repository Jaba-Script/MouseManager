package com.mousemanager.diagnostics

/**
 * Итог обработки одного события в журнале (D-017).
 *
 * Имена — стабильные коды для журнала и `Logcat`; человекочитаемые подписи для UI
 * появятся в `strings.xml` на этапе S7, чтобы не дублировать их в модели.
 *
 * Семь значений перечислены в D-017. Восьмое, [ACTION_FAILED], — отдельная запись об
 * ошибке диспетчера (D-010, §14): клавиша уже поглощена, но действие не выполнилось.
 * Оно не является «пропуском», поэтому не входит в семейство `PASSED_*`.
 */
enum class Decision {
    CONSUMED,
    PASSED_DISARMED,
    PASSED_FOREIGN_DEVICE,
    PASSED_MODIFIER,
    PASSED_NO_MAPPING,
    PASSED_NON_HARDWARE,
    PASSED_ERROR,
    ACTION_FAILED,
}
