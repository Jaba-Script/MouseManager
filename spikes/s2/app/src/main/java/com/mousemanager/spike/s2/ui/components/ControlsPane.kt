package com.mousemanager.spike.s2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mousemanager.spike.s2.core.ServiceStatus
import com.mousemanager.spike.s2.core.TargetDevice
import com.mousemanager.spike.s2.model.DeviceSighting
import com.mousemanager.spike.s2.util.Format

@Composable
fun ControlsPane(
    status: ServiceStatus,
    connected: Boolean,
    filterFlagApplied: Boolean,
    armed: Boolean,
    target: TargetDevice?,
    devices: List<DeviceSighting>,
    onOpenSettings: () -> Unit,
    onDisarm: () -> Unit,
    onArmedChange: (Boolean) -> Unit,
    onSelectTarget: (DeviceSighting) -> Unit,
    onClearTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ServiceCard(
            status = status,
            connected = connected,
            filterFlagApplied = filterFlagApplied,
            armed = armed,
            onOpenSettings = onOpenSettings,
            onDisarm = onDisarm,
        )
        ArmedCard(armed = armed, target = target, onArmedChange = onArmedChange)
        TargetSection(
            devices = devices,
            target = target,
            onSelect = onSelectTarget,
            onClear = onClearTarget,
        )
        KeyboardTestCard()
    }
}

@Composable
private fun ServiceCard(
    status: ServiceStatus,
    connected: Boolean,
    filterFlagApplied: Boolean,
    armed: Boolean,
    onOpenSettings: () -> Unit,
    onDisarm: () -> Unit,
) {
    SectionCard(title = "Служба доступности") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (status.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (status.enabled) "Включена в системе" else "Выключена в системе",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        KeyValue("Служба подключена (onServiceConnected)", if (connected) "да" else "нет")
        KeyValue(
            "canRequestFilterKeyEvents",
            if (status.canRequestFilterKeyEventsDeclared) "заявлено в XML: да" else "нет",
        )
        KeyValue("Флаг фильтра принят системой", if (filterFlagApplied) "да" else "нет")
        KeyValue("flags", Format.hex(status.flags))
        Spacer(Modifier.height(10.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Настройки доступности")
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onDisarm, enabled = armed, modifier = Modifier.fillMaxWidth()) {
            Text("Выключить перехват")
        }
    }
}

@Composable
private fun ArmedCard(
    armed: Boolean,
    target: TargetDevice?,
    onArmedChange: (Boolean) -> Unit,
) {
    SectionCard(title = "Перехват") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Поглощать Ctrl+C / Ctrl+V",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (armed) {
                        "Клавиши от целевой мыши не дойдут до приложений"
                    } else {
                        "Все клавиши проходят дальше"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (armed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(checked = armed, onCheckedChange = onArmedChange)
        }

        if (armed && target == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Целевое устройство не выбрано — поглощать пока нечего.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Перехват не сохраняется между запусками: после перезапуска приложения " +
                "он снова выключен.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TargetSection(
    devices: List<DeviceSighting>,
    target: TargetDevice?,
    onSelect: (DeviceSighting) -> Unit,
    onClear: () -> Unit,
) {
    SectionCard(title = "Целевое устройство") {
        if (devices.isEmpty()) {
            Text(
                text = "Пока не видно ни одного устройства с клавишами. Нажмите боковую кнопку " +
                    "мыши — оно появится здесь.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        devices.forEach { device ->
            TargetRow(
                device = device,
                selected = target?.descriptor == device.descriptor,
                onClick = { onSelect(device) },
            )
            Spacer(Modifier.height(6.dp))
        }

        if (target != null) {
            TextButton(onClick = onClear) {
                Text("Сбросить выбор")
            }
        }
    }
}

@Composable
private fun TargetRow(device: DeviceSighting, selected: Boolean, onClick: () -> Unit) {
    val accent = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(if (selected) 2.dp else 1.dp, accent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Text(
                        text = "ЦЕЛЬ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "id ${device.currentId ?: "—"} · все id: ${device.idHistory} · " +
                    "переподключений: ${device.reconnectCount}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = device.descriptor,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyboardTestCard() {
    var text by remember { mutableStateOf("") }

    SectionCard(title = "Проверка обычной клавиатуры") {
        Text(
            text = "Печатайте здесь с физической клавиатуры: буквы должны появляться, а её " +
                "собственные Ctrl+C / Ctrl+V — работать как обычно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            placeholder = { Text("Текст для проверки…") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
