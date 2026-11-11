package com.mousemanager.diagnostics.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mousemanager.diagnostics.model.DiagEvent
import com.mousemanager.diagnostics.util.EventFormat

@Composable
fun EventCard(event: DiagEvent, modifier: Modifier = Modifier) {
    val isMotion = event is DiagEvent.Motion
    val accent = if (isMotion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val title = when (event) {
        is DiagEvent.Motion -> event.actionLabel
        is DiagEvent.Key -> event.keyLabel
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(text = if (isMotion) "MOTION" else "KEY", accent = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = event.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${event.deviceName}  ·  id ${event.deviceId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                when (event) {
                    is DiagEvent.Motion -> MotionDetails(event, accent)
                    is DiagEvent.Key -> KeyDetails(event, accent)
                }
            }
        }
    }
}

@Composable
private fun MotionDetails(event: DiagEvent.Motion, accent: Color) {
    Field(label = "action", value = "${event.actionLabel}  (${event.action})", accent = accent)
    Field(
        label = "source",
        value = "${event.sourceLabel}  (${EventFormat.hex(event.source)})",
        accent = accent,
    )
    Field(label = "toolType", value = "${event.toolTypeLabel}  (${event.toolType})", accent = accent)
    Field(
        label = "buttonState",
        value = if (event.buttonState == 0) "0x00000000  (none)" else EventFormat.hex(event.buttonState),
        accent = accent,
    )
    Field(label = "actionButton", value = event.actionButtonLabel, accent = accent)
    Field(
        label = "AXIS_VSCROLL",
        value = EventFormat.axis(event.verticalScroll),
        accent = accent,
        highlight = event.verticalScroll != 0f,
    )
    Field(
        label = "AXIS_HSCROLL",
        value = EventFormat.axis(event.horizontalScroll),
        accent = accent,
        highlight = event.horizontalScroll != 0f,
    )
    Field(label = "x / y", value = EventFormat.point(event.x, event.y), accent = accent)
    Field(label = "pointers", value = event.pointerCount.toString(), accent = accent)

    if (event.buttonStateLabels.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            event.buttonStateLabels.forEach { name -> InfoChip(text = name, accent = accent) }
        }
    }
}

@Composable
private fun KeyDetails(event: DiagEvent.Key, accent: Color) {
    Field(label = "keyCode", value = "${event.keyLabel}  (${event.keyCode})", accent = accent)
    Field(label = "scanCode", value = event.scanCode.toString(), accent = accent)
    Field(
        label = "metaState",
        value = buildString {
            append(EventFormat.hex(event.metaState))
            if (event.metaStateLabels.isNotEmpty()) {
                append("  ·  ")
                append(event.metaStateLabels.joinToString(" + "))
            }
        },
        accent = accent,
        highlight = event.metaState != 0,
    )
    Field(label = "repeat", value = event.repeatCount.toString(), accent = accent)

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoChip(text = "Ctrl", accent = accent, active = event.ctrlPressed)
        InfoChip(text = "Shift", accent = accent, active = event.shiftPressed)
        InfoChip(text = "Alt", accent = accent, active = event.altPressed)
        InfoChip(text = "Meta", accent = accent, active = event.metaPressed)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    accent: Color,
    highlight: Boolean = false,
) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(104.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}
