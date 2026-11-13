package com.mousemanager.core

/** Ключ маппинга «любое целевое устройство» (§5.2, D-007: `device: null`). */
const val ANY_DEVICE = "*"

enum class Modifier { CTRL, SHIFT, ALT, META }

/** Битовая маска модификаторов (§6.2). */
const val MOD_CTRL = 1
const val MOD_SHIFT = 2
const val MOD_ALT = 4
const val MOD_META = 8

val Modifier.mask: Int
    get() = when (this) {
        Modifier.CTRL -> MOD_CTRL
        Modifier.SHIFT -> MOD_SHIFT
        Modifier.ALT -> MOD_ALT
        Modifier.META -> MOD_META
    }

/** Маска набора модификаторов: объединение битов, как в `ChordKey.modsMask`. */
fun modsMask(mods: Set<Modifier>): Int = mods.fold(0) { acc, modifier -> acc or modifier.mask }

/** Персистентный аккорд (§5.1). */
data class Chord(val mods: Set<Modifier>, val keyCode: Int)

/** Ключ предвычисленной карты маппингов (§5.2). */
data class ChordKey(val modsMask: Int, val keyCode: Int)
