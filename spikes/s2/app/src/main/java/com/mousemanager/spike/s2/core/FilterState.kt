package com.mousemanager.spike.s2.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TargetDevice(val descriptor: String, val name: String)

/**
 * Runtime switches of the spike.
 *
 * `armed` is deliberately **not persisted**. The service lives in the same process as the
 * activity, so after a process restart or a reboot interception comes back disabled — a
 * forgotten switch can never keep swallowing keystrokes. Only the selected target device
 * is saved, and it does nothing on its own.
 */
object FilterState {

    private const val PREFS_NAME = "spike_s2"
    private const val KEY_TARGET_DESCRIPTOR = "target_descriptor"
    private const val KEY_TARGET_NAME = "target_name"

    @Volatile private var armedValue = false
    @Volatile private var targetDescriptor: String? = null

    private var prefs: SharedPreferences? = null

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _target = MutableStateFlow<TargetDevice?>(null)
    val target: StateFlow<TargetDevice?> = _target.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        val descriptor = p.getString(KEY_TARGET_DESCRIPTOR, null)
        targetDescriptor = descriptor
        if (descriptor != null) {
            _target.value = TargetDevice(descriptor, p.getString(KEY_TARGET_NAME, null) ?: "—")
        }
    }

    /** Hot path: called for every hardware key event, so it stays allocation-free. */
    fun isArmed(): Boolean = armedValue

    fun targetDescriptor(): String? = targetDescriptor

    fun setArmed(value: Boolean) {
        armedValue = value
        _armed.value = value
    }

    fun setTarget(descriptor: String?, name: String?) {
        targetDescriptor = descriptor
        prefs?.edit()?.apply {
            if (descriptor == null) {
                remove(KEY_TARGET_DESCRIPTOR)
                remove(KEY_TARGET_NAME)
            } else {
                putString(KEY_TARGET_DESCRIPTOR, descriptor)
                putString(KEY_TARGET_NAME, name)
            }
        }?.apply()
        _target.value = descriptor?.let { TargetDevice(it, name ?: "—") }
    }
}
