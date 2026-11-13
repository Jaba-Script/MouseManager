package com.mousemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mousemanager.BuildConfig
import com.mousemanager.R
import com.mousemanager.ui.components.SectionCard

/**
 * About (S7.6): версия и честные ограничения текущей сборки.
 *
 * Тексты описывают только реализованное поведение: переназначение кнопок — M2,
 * LMB/RMB/MMB не трогаются (D-009), «вперёд» не входит в v1 (D-021), журнал живёт
 * в памяти (D-017).
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.app_name)) {
            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.about_limitations_title)) {
            Text(
                text = stringResource(R.string.about_limitation_no_remap),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_limitation_buttons),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_limitation_forward),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_limitation_accessibility),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_limitation_journal),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
