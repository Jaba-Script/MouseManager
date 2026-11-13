package com.mousemanager.core

/**
 * Действия v1 (D-010): только то, что реализуется через
 * `AccessibilityService.performGlobalAction`. «Вперёд» в v1 нет (D-021).
 *
 * [NONE] — маппинг существует, но ничего не делает; кодек ставит его при неизвестном
 * действии в JSON (D-007), а не роняет загрузку настроек.
 */
enum class ActionCode {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    POWER_DIALOG,
    LOCK_SCREEN,
    SCREENSHOT,
    NONE,
}
