package com.mousemanager.core

/*
 * Числовые значения `android.view.KeyEvent`, нужные горячему пути (§6).
 *
 * `core` намеренно не импортирует Android: константы продублированы числами, а
 * `KeyCodesTest` сверяет их с одноимёнными константами `android.view.KeyEvent`.
 * Значения неизменны с API 1, поэтому дублирование безопасно.
 */

/** `KeyEvent.ACTION_DOWN`. */
const val ACTION_DOWN = 0

/** `KeyEvent.ACTION_UP`. */
const val ACTION_UP = 1

// Коды модификаторов. Модификаторы никогда не поглощаются (D-005): по ним только
// собирается аккорд (§6.2), поэтому они перечислены явно.
const val KEYCODE_CTRL_LEFT = 113
const val KEYCODE_CTRL_RIGHT = 114
const val KEYCODE_SHIFT_LEFT = 59
const val KEYCODE_SHIFT_RIGHT = 60
const val KEYCODE_ALT_LEFT = 57
const val KEYCODE_ALT_RIGHT = 58
const val KEYCODE_META_LEFT = 117
const val KEYCODE_META_RIGHT = 118

/**
 * Все модификаторы, которые трекер отслеживает и которые всегда проходят в приложение
 * (D-005). Используется шагом 6 алгоритма §6.
 */
val MODIFIER_KEYCODES: Set<Int> = setOf(
    KEYCODE_CTRL_LEFT,
    KEYCODE_CTRL_RIGHT,
    KEYCODE_SHIFT_LEFT,
    KEYCODE_SHIFT_RIGHT,
    KEYCODE_ALT_LEFT,
    KEYCODE_ALT_RIGHT,
    KEYCODE_META_LEFT,
    KEYCODE_META_RIGHT,
)

// Флаги KeyEvent.metaState. Достаточно общих битов: на устройстве измерен
// `0x3000` = META_CTRL_ON | META_CTRL_LEFT_ON (SPIKES §4), поэтому уточнения
// left/right для аккорда не нужны.
private const val META_SHIFT_ON = 0x1
private const val META_ALT_ON = 0x02
private const val META_CTRL_ON = 0x1000
private const val META_META_ON = 0x10000

/**
 * Переводит `KeyEvent.metaState` в битовую маску [MOD_CTRL]/[MOD_SHIFT]/[MOD_ALT]/
 * [MOD_META] (§6.2).
 *
 * Посторонние флаги (`META_CAPS_LOCK_ON`, `META_NUM_LOCK_ON`, `META_SYM_ON` и другие)
 * игнорируются: они не входят в аккорд, иначе маппинг перестал бы совпадать при
 * включённом Caps Lock.
 */
fun modsOf(metaState: Int): Int {
    var mask = 0
    if (metaState and META_CTRL_ON != 0) mask = mask or MOD_CTRL
    if (metaState and META_SHIFT_ON != 0) mask = mask or MOD_SHIFT
    if (metaState and META_ALT_ON != 0) mask = mask or MOD_ALT
    if (metaState and META_META_ON != 0) mask = mask or MOD_META
    return mask
}
