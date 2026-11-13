package com.mousemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mousemanager.R
import com.mousemanager.data.ConfigStore
import com.mousemanager.data.SettingsRepository
import com.mousemanager.devices.DeviceCatalog
import com.mousemanager.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Devices (S7.3–S7.4): список мышей и тачпадов из [DeviceCatalog] и выбор целей.
 *
 * Список строится чистой [deviceRows] по снимку каталога и `RuntimeConfig.targets`.
 * Сохранённая цель, устройство которой отключено, не исчезает: она показывается как
 * «не подключено» и восстанавливает галочку при повторном подключении (§10, D-003).
 *
 * Запись — только через [SettingsRepository.setTargets] (D-007); своего хранилища
 * выбора экран не держит. Пока настройки загружаются или недоступны, чекбоксы
 * отключены: иначе выбор строился бы на пустом fail-safe снимке и мог стереть цели.
 */
@Composable
fun DevicesScreen(
    deviceCatalog: DeviceCatalog,
    settingsRepository: SettingsRepository,
    configStore: ConfigStore,
    modifier: Modifier = Modifier,
) {
    val devices by deviceCatalog.devices.collectAsState()
    val runtime by configStore.runtime.collectAsState()
    val error by configStore.error.collectAsState()

    val loading = error == null && runtime.generation == 0L
    val rows = deviceRows(devices, runtime.targets)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveErrorMessage = stringResource(R.string.devices_save_error)
    var saving by remember { mutableStateOf(false) }
    val selectionEnabled = error == null && !loading && !saving

    val onToggle: (String, Boolean) -> Unit = { descriptor, checked ->
        // Берём последний опубликованный снимок, а не значение из замыкания.
        val current = configStore.runtime.value.targets
        val updated = if (checked) current + descriptor else current - descriptor
        scope.launch {
            saving = true
            val saved = try {
                settingsRepository.setTargets(updated)
                true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                false
            } finally {
                saving = false
            }
            if (!saved) {
                // Галочка не меняется: runtime обновится только после успешной записи.
                snackbarHostState.showSnackbar(saveErrorMessage)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                error != null -> NoticeCard(
                    text = stringResource(R.string.devices_config_error),
                    isError = true,
                )

                loading -> NoticeCard(
                    text = stringResource(R.string.devices_loading),
                    isError = false,
                )

                rows.isEmpty() -> SectionCard(
                    title = stringResource(R.string.devices_empty_title),
                ) {
                    Text(
                        text = stringResource(R.string.devices_empty_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> SectionCard(title = stringResource(R.string.devices_title)) {
                    Text(
                        text = stringResource(
                            R.string.devices_targets_count,
                            runtime.targets.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.devices_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    rows.forEach { row ->
                        DeviceRowItem(
                            row = row,
                            enabled = selectionEnabled,
                            onCheckedChange = { checked -> onToggle(row.descriptor, checked) },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

/** Строка устройства: одна цель выбора, поэтому у дубликатов общий чекбокс. */
@Composable
private fun DeviceRowItem(
    row: DeviceRow,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = row.isTarget,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = row.isTarget,
            onCheckedChange = null,
            enabled = enabled,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (row.connected) {
                    row.name ?: stringResource(R.string.devices_unknown_name)
                } else {
                    stringResource(R.string.devices_disconnected)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = rowSubtitle(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!row.connected) {
                Text(
                    text = stringResource(R.string.devices_disconnected_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.isDuplicate) {
                Text(
                    text = stringResource(R.string.devices_duplicate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun rowSubtitle(row: DeviceRow): String =
    if (row.connected) {
        listOfNotNull(
            row.vendorProduct,
            shortDescriptor(row.descriptor),
            stringResource(R.string.devices_connected),
        ).joinToString(" · ")
    } else {
        shortDescriptor(row.descriptor)
    }

/** Нейтральное или ошибочное сообщение вместо списка (загрузка, ошибка конфига). */
@Composable
private fun NoticeCard(
    text: String,
    isError: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
