package com.mousemanager.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mousemanager.R
import com.mousemanager.diagnostics.Decision
import com.mousemanager.diagnostics.DiagnosticsLog
import com.mousemanager.diagnostics.KeyRecord
import com.mousemanager.ui.components.SectionCard

/**
 * Diagnostics (S7.5): журнал [KeyRecord] из [DiagnosticsLog], новые записи первыми.
 *
 * Экран только читает журнал и вызывает [DiagnosticsLog.clear]. Своего буфера нет;
 * служба и механизм записи не меняются. Журнал живёт в памяти процесса (D-017),
 * поэтому после перезапуска он пуст — это явно сказано в интерфейсе.
 */
@Composable
fun DiagnosticsScreen(
    diagnosticsLog: DiagnosticsLog,
    modifier: Modifier = Modifier,
) {
    val records by diagnosticsLog.records.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_count, records.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.diagnostics_memory_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { diagnosticsLog.clear() },
                enabled = records.isNotEmpty(),
            ) {
                Text(stringResource(R.string.diagnostics_clear))
            }
        }

        if (records.isEmpty()) {
            SectionCard(title = stringResource(R.string.diagnostics_empty_title)) {
                Text(
                    text = stringResource(R.string.diagnostics_empty_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records) { record ->
                    RecordCard(record = record)
                }
            }
        }
    }
}

/** Одна запись журнала: время, решение, устройство, клавиша и технические поля. */
@Composable
private fun RecordCard(record: KeyRecord) {
    val isDown = record.action == KeyEvent.ACTION_DOWN
    val direction = stringResource(
        if (isDown) R.string.diagnostics_direction_down else R.string.diagnostics_direction_up,
    )
    val repeat = if (record.repeatCount > 0) {
        stringResource(R.string.diagnostics_repeat, record.repeatCount)
    } else {
        null
    }
    val yes = stringResource(R.string.diagnostics_yes)
    val no = stringResource(R.string.diagnostics_no)
    val deviceName = record.deviceName.ifBlank {
        stringResource(R.string.diagnostics_device_unknown)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatRecordTime(record.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(decisionLabelRes(record.decision)),
                    style = MaterialTheme.typography.labelMedium,
                    color = decisionColor(record.decision),
                )
            }
            Text(
                text = stringResource(
                    R.string.diagnostics_device,
                    deviceName,
                    record.deviceId,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = listOfNotNull(
                    KeyEvent.keyCodeToString(record.keyCode),
                    stringResource(R.string.diagnostics_scan, record.scanCode),
                    direction,
                    repeat,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.diagnostics_technical,
                    formatMetaState(record.metaState),
                    if (record.isTarget) yes else no,
                    if (record.armed) yes else no,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun decisionColor(decision: Decision): Color = when (decision) {
    Decision.CONSUMED -> MaterialTheme.colorScheme.primary
    Decision.ACTION_FAILED, Decision.PASSED_ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
