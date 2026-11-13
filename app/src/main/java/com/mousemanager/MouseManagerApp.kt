package com.mousemanager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mousemanager.data.ConfigStore
import com.mousemanager.data.SettingsRepository
import com.mousemanager.devices.DeviceCatalog
import com.mousemanager.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Один `DataStore` на процесс (§9). Делегат объявлен на уровне файла, поэтому даже
 * разные `Context` получают один и тот же экземпляр `Preferences`.
 */
private val Context.mouseManagerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mouse_manager",
)

/**
 * Точка сборки компонентов приложения (§4, §9).
 *
 * Создаёт и хранит единственные на процесс экземпляры:
 * - [SettingsRepository] — единственная точка записи настроек;
 * - [ConfigStore] — фоновый сбор DataStore и публикация `RuntimeConfig`;
 * - [DeviceCatalog] — каталог мышей для UI.
 *
 * Служба и UI получают эти объекты через `applicationContext as MouseManagerApp`;
 * второго DataStore, репозитория или каталога быть не должно.
 *
 * Каталог живёт только пока приложение видно: `start()` на первом стартовавшем
 * Activity, `stop()` на остановке последнего. Счётчик Activity нужен, чтобы смена
 * конфигурации и диалоги не отписывали каталог между двумя Activity.
 */
class MouseManagerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var configStore: ConfigStore
        private set

    lateinit var deviceCatalog: DeviceCatalog
        private set

    /** Единственный журнал на процесс: пишет служба, читает экран Diagnostics (S7). */
    lateinit var diagnosticsLog: DiagnosticsLog
        private set

    private var startedActivities = 0

    private val catalogLifecycle = object : Application.ActivityLifecycleCallbacks {

        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            if (startedActivities == 1) deviceCatalog.start()
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities == 0) deviceCatalog.stop()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()

        settingsRepository = SettingsRepository(mouseManagerDataStore)
        configStore = ConfigStore(settingsRepository, appScope)
        deviceCatalog = DeviceCatalog(this)
        diagnosticsLog = DiagnosticsLog()

        registerActivityLifecycleCallbacks(catalogLifecycle)
    }

    override fun onTerminate() {
        // На реальном устройстве не вызывается; нужен для эмулятора и тестов.
        deviceCatalog.stop()
        appScope.cancel()
        super.onTerminate()
    }
}
