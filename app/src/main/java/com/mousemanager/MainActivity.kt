package com.mousemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mousemanager.ui.MouseManagerUi
import com.mousemanager.ui.theme.MouseManagerTheme

/**
 * Единственная Activity продукта (§4). Показывает каркас Compose UI:
 * экраны и навигация — S7.1, реальные данные — S7.2–S7.6.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Компоненты процесса создаёт MouseManagerApp (§9); UI получает готовые.
        val app = application as MouseManagerApp
        setContent {
            MouseManagerTheme {
                MouseManagerUi(
                    configStore = app.configStore,
                    settingsRepository = app.settingsRepository,
                    deviceCatalog = app.deviceCatalog,
                    diagnosticsLog = app.diagnosticsLog,
                )
            }
        }
    }
}
