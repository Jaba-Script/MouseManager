package com.mousemanager.spike.s2

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mousemanager.spike.s2.core.FilterState
import com.mousemanager.spike.s2.core.ServiceProbe
import com.mousemanager.spike.s2.ui.S2Screen
import com.mousemanager.spike.s2.ui.theme.MouseManagerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FilterState.init(this)
        enableEdgeToEdge()
        setContent {
            MouseManagerTheme {
                S2Screen(
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just enabled or disabled the service in system settings.
        ServiceProbe.refresh(this)
    }
}
