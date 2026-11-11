package com.mousemanager.diagnostics.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mousemanager.diagnostics.R
import com.mousemanager.diagnostics.input.InputEventLogger
import com.mousemanager.diagnostics.model.InputDeviceInfo
import kotlinx.coroutines.delay

@Composable
fun DeviceSection(devices: List<InputDeviceInfo>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Bluetooth mice sleep and come back with a new deviceId, so poll while the screen is visible.
    LaunchedEffect(context) {
        while (true) {
            delay(3_000)
            InputEventLogger.refreshDevices(context)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.devices_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { InputEventLogger.refreshDevices(context) }) {
                Text(stringResource(R.string.refresh))
            }
        }

        if (devices.isEmpty()) {
            Text(
                text = stringResource(R.string.no_devices),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.id }) { device -> DeviceCard(device) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeviceCard(device: InputDeviceInfo, modifier: Modifier = Modifier) {
    val accent = if (device.isMouse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Surface(
        modifier = modifier.width(215.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (device.isMouse) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoChip(text = if (device.isMouse) "MOUSE" else "INPUT", accent = accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "id ${device.id}  ·  ${device.vendorProduct}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = device.sourcesSummary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
