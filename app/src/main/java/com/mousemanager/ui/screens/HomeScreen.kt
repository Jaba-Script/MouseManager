package com.mousemanager.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mousemanager.R
import com.mousemanager.data.ConfigStore
import com.mousemanager.service.ServiceBridge
import com.mousemanager.service.ServiceState
import com.mousemanager.service.ServiceStatus
import com.mousemanager.ui.components.SectionCard

/**
 * Home (S7.2, ARCHITECTURE §12): честный статус без ложных обещаний.
 *
 * Источники только для чтения: [ServiceState.status] (статус службы, применённый
 * фильтр и зеркало `armed`) и [ConfigStore] (`runtime`, `error`). Мастер-переключатель
 * меняет `armed` только через [ServiceBridge] (D-031): включение — после диалога
 * подтверждения, выключение — сразу. Экран не создаёт движок и не пишет настройки.
 * Все тексты — из `strings.xml` (D-019).
 */
@Composable
fun HomeScreen(
    configStore: ConfigStore,
    modifier: Modifier = Modifier,
) {
    val status by ServiceState.status.collectAsState()
    val runtime by configStore.runtime.collectAsState()
    val error by configStore.error.collectAsState()
    val configState = homeConfigState(runtime, error)

    val bridge = remember { ServiceBridge() }
    val masterState = homeMasterState(
        status = status,
        error = error,
        canArm = bridge.canArm(),
    )
    var confirmEnable by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ServiceSection(status = status)
        MasterSwitchSection(
            state = masterState,
            onCheckedChange = { checked ->
                when {
                    !checked -> bridge.setArmed(false)
                    masterState.requiresConfirmation -> confirmEnable = true
                }
            },
        )
        ConfigSection(state = configState)
    }

    if (confirmEnable) {
        AlertDialog(
            onDismissRequest = { confirmEnable = false },
            title = { Text(stringResource(R.string.home_master_confirm_title)) },
            text = { Text(stringResource(R.string.home_master_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEnable = false
                        bridge.setArmed(true)
                    },
                ) {
                    Text(stringResource(R.string.home_master_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnable = false }) {
                    Text(stringResource(R.string.home_master_confirm_cancel))
                }
            },
        )
    }
}

/** Статус службы: подключение и реально применённый `FLAG_REQUEST_FILTER_KEY_EVENTS`. */
@Composable
private fun ServiceSection(status: ServiceStatus) {
    val context = LocalContext.current

    SectionCard(title = stringResource(R.string.home_service_title)) {
        InfoRow(
            label = stringResource(R.string.home_service_connection_label),
            value = stringResource(
                if (status.connected) {
                    R.string.home_service_connected
                } else {
                    R.string.home_service_disconnected
                },
            ),
            valueColor = if (status.connected) MaterialTheme.colorScheme.primary else null,
        )
        InfoRow(
            label = stringResource(R.string.home_service_filter_label),
            value = stringResource(
                if (status.filterApplied) {
                    R.string.home_service_filter_applied
                } else {
                    R.string.home_service_filter_missing
                },
            ),
            valueColor = when {
                status.filterApplied -> MaterialTheme.colorScheme.primary
                status.connected -> MaterialTheme.colorScheme.error
                else -> null
            },
        )
        Text(
            text = stringResource(
                when {
                    !status.connected -> R.string.home_service_note_disconnected
                    !status.filterApplied -> R.string.home_service_note_filter_missing
                    else -> R.string.home_service_note_ready
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (status.connected && !status.filterApplied) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        OutlinedButton(
            onClick = {
                // Службу нельзя включить программно (§12): только системный экран,
                // и только по явному действию пользователя.
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
        ) {
            Text(stringResource(R.string.home_open_accessibility_settings))
        }
    }
}

/**
 * Мастер-переключатель (D-006, D-031): включение — только после подтверждения,
 * выключение — сразу. Переключатель недоступен, пока гейт закрыт; причина показана
 * рядом. Если перехват уже включён, выключить его можно всегда, даже когда гейт
 * успел закрыться.
 */
@Composable
private fun MasterSwitchSection(
    state: HomeMasterState,
    onCheckedChange: (Boolean) -> Unit,
) {
    SectionCard(title = stringResource(R.string.home_master_title)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.checked) {
                            R.string.home_master_state_on
                        } else {
                            R.string.home_master_state_off
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = stringResource(R.string.home_master_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.checked,
                onCheckedChange = onCheckedChange,
                enabled = state.enabled || state.checked,
            )
        }
        state.reason?.let { reason ->
            Text(
                text = stringResource(reason.textRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Ресурс причины недоступности: UI переводит чистое состояние в строку (D-019). */
@get:StringRes
private val HomeMasterReason.textRes: Int
    get() = when (this) {
        HomeMasterReason.SERVICE_DISCONNECTED -> R.string.home_master_reason_disconnected
        HomeMasterReason.FILTER_MISSING -> R.string.home_master_reason_filter_missing
        HomeMasterReason.CONFIG_ERROR -> R.string.home_master_reason_config_error
        HomeMasterReason.CONFIG_LOADING -> R.string.home_master_reason_config_loading
        HomeMasterReason.ENGINE_UNAVAILABLE -> R.string.home_master_reason_engine_unavailable
    }

/** Конфигурация: загрузка, ошибка или сводка из [HomeConfigState]. */
@Composable
private fun ConfigSection(state: HomeConfigState) {
    SectionCard(title = stringResource(R.string.home_config_title)) {
        when {
            state.error != null -> {
                Text(
                    text = stringResource(R.string.home_config_error_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.home_config_error_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.loading -> Text(
                text = stringResource(R.string.home_config_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                InfoRow(
                    label = stringResource(R.string.home_config_targets_label),
                    value = state.targetsCount.toString(),
                )
                InfoRow(
                    label = stringResource(R.string.home_config_profile_label),
                    value = state.activeProfileName
                        ?: stringResource(R.string.home_config_profile_none),
                )
            }
        }
    }
}

/** Строка «подпись — значение»; цвет значения задаётся только для акцентов. */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}
