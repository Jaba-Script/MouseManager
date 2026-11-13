package com.mousemanager.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mousemanager.R
import com.mousemanager.data.ConfigStore
import com.mousemanager.data.SettingsRepository
import com.mousemanager.devices.DeviceCatalog
import com.mousemanager.diagnostics.DiagnosticsLog
import com.mousemanager.ui.screens.AboutScreen
import com.mousemanager.ui.screens.DevicesScreen
import com.mousemanager.ui.screens.DiagnosticsScreen
import com.mousemanager.ui.screens.HomeScreen

/**
 * Экраны продукта (ARCHITECTURE §12). Порядок объявления задаёт порядок в навигации.
 *
 * Экран Mappings и Profiles в S7 не входит: их API появится в M2 (D-011).
 * [titleRes] — заголовок в `TopAppBar`, [labelRes] — подпись пункта навигации.
 */
enum class Screen(
    @param:StringRes val titleRes: Int,
    @param:StringRes val labelRes: Int,
) {
    HOME(R.string.screen_home_title, R.string.nav_home),
    DEVICES(R.string.screen_devices_title, R.string.nav_devices),
    DIAGNOSTICS(R.string.screen_diagnostics_title, R.string.nav_diagnostics),
    ABOUT(R.string.screen_about_title, R.string.nav_about),
}

/** Порог «широкого» окна: планшет в ландшафте показывает [NavigationRail]. */
private val WIDE_LAYOUT_MIN_WIDTH = 600.dp

/**
 * Корневой Compose-интерфейс (§12): один `Scaffold`, навигация без `navigation-compose`.
 *
 * Состояние экрана — [rememberSaveable], поэтому поворот не сбрасывает раздел.
 * `BackHandler` возвращает на Home только из других экранов; на Home системная
 * кнопка работает как обычно.
 *
 * Home (S7.2) читает [ConfigStore] и `ServiceState`; Devices (S7.3–S7.4) — каталог
 * устройств, цели из [ConfigStore] и запись через [SettingsRepository]; Diagnostics
 * (S7.5) — журнал [DiagnosticsLog]; About (S7.6) — только версия и ограничения.
 * Компоненты процесса передаются сюда из `MainActivity`, чтобы UI не создавал
 * собственных экземпляров (§9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MouseManagerUi(
    configStore: ConfigStore,
    settingsRepository: SettingsRepository,
    deviceCatalog: DeviceCatalog,
    diagnosticsLog: DiagnosticsLog,
    modifier: Modifier = Modifier,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = Screen.HOME
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= WIDE_LAYOUT_MIN_WIDTH

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(screen.titleRes)) })
            },
            bottomBar = {
                if (!wideLayout) {
                    NavigationBar {
                        Screen.entries.forEach { item ->
                            NavigationBarItem(
                                selected = item == screen,
                                onClick = { screen = item },
                                icon = {},
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                if (wideLayout) {
                    NavigationRail {
                        Screen.entries.forEach { item ->
                            NavigationRailItem(
                                selected = item == screen,
                                onClick = { screen = item },
                                icon = {},
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(configStore = configStore)
                        Screen.DEVICES -> DevicesScreen(
                            deviceCatalog = deviceCatalog,
                            settingsRepository = settingsRepository,
                            configStore = configStore,
                        )
                        Screen.DIAGNOSTICS -> DiagnosticsScreen(diagnosticsLog = diagnosticsLog)
                        Screen.ABOUT -> AboutScreen()
                    }
                }
            }
        }
    }
}
