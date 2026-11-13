package com.mousemanager.core

/**
 * Единственное место принятия решений (§6).
 *
 * В M1 — заглушка по плану вехи: всегда [Verdict.Pass] с причиной
 * [PassReason.DISARMED], поэтому служба не поглощает ни одного события и требование
 * «ничего не перехватывается» выполняется по построению.
 *
 * Проверки `descriptor`/`targets`, `armed`, `ChordTracker` и диспетчеризация
 * появляются в M2 — сигнатура [onKey] при этом не меняется.
 */
class RemapEngine {

    fun onKey(input: KeyInput): Verdict = Verdict.Pass(PassReason.DISARMED)
}
