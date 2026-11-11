package com.mousemanager.spike.s2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mousemanager.spike.s2.core.FilterState
import com.mousemanager.spike.s2.core.InputLog
import com.mousemanager.spike.s2.core.ServiceProbe
import com.mousemanager.spike.s2.core.Stats
import com.mousemanager.spike.s2.model.KeyRecord
import com.mousemanager.spike.s2.ui.components.ControlsPane
import com.mousemanager.spike.s2.ui.components.RecordCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2Screen(onOpenAccessibilitySettings: () -> Unit) {
    val events by InputLog.events.collectAsState()
    val stats by InputLog.stats.collectAsState()
    val devices by InputLog.devices.collectAsState()
    val armed by FilterState.armed.collectAsState()
    val target by FilterState.target.collectAsState()
    val status by ServiceProbe.status.collectAsState()
    val connected by ServiceProbe.connected.collectAsState()
    val filterFlagApplied by ServiceProbe.filterFlagApplied.collectAsState()

    val wide = LocalConfiguration.current.screenWidthDp >= 760

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            text = "Spike S2 · перехват клавиш",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "AccessibilityService · canRequestFilterKeyEvents",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (armed) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = "ПЕРЕХВАТ",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                },
            )
        },
    ) { innerPadding ->
        val root = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        if (wide) {
            Row(root) {
                ControlsPane(
                    status = status,
                    connected = connected,
                    filterFlagApplied = filterFlagApplied,
                    armed = armed,
                    target = target,
                    devices = devices,
                    onOpenSettings = onOpenAccessibilitySettings,
                    onDisarm = { FilterState.setArmed(false) },
                    onArmedChange = { FilterState.setArmed(it) },
                    onSelectTarget = { FilterState.setTarget(it.descriptor, it.name) },
                    onClearTarget = { FilterState.setTarget(null, null) },
                    modifier = Modifier
                        .width(420.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LogPane(
                    events = events,
                    stats = stats,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(root) {
                ControlsPane(
                    status = status,
                    connected = connected,
                    filterFlagApplied = filterFlagApplied,
                    armed = armed,
                    target = target,
                    devices = devices,
                    onOpenSettings = onOpenAccessibilitySettings,
                    onDisarm = { FilterState.setArmed(false) },
                    onArmedChange = { FilterState.setArmed(it) },
                    onSelectTarget = { FilterState.setTarget(it.descriptor, it.name) },
                    onClearTarget = { FilterState.setTarget(null, null) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LogPane(
                    events = events,
                    stats = stats,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LogPane(events: List<KeyRecord>, stats: Stats, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Журнал клавиш",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (events.isNotEmpty()) {
                TextButton(onClick = { InputLog.clear() }) {
                    Text("Очистить")
                }
            }
        }

        StatsRow(stats)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (events.isEmpty()) {
            EmptyLog(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.id }) { record ->
                    RecordCard(record)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(stats: Stats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard("Поглощено", stats.consumed.toString(), Modifier.weight(1f))
        StatCard("Пропущено", stats.passed.toString(), Modifier.weight(1f))
        StatCard("Всего", stats.total.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyLog(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Клавиши ещё не приходили",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Если служба включена, а счётчики не растут даже от клавиатуры — значит " +
                    "события до службы не доходят (флаг фильтра не принят или ROM блокирует сервис).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
