package com.deineko.kidsgames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deineko.kidsgames.BuildConfig
import com.deineko.kidsgames.catalog.CatalogRepository
import com.deineko.kidsgames.update.UpdateManager
import com.deineko.kidsgames.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            text = "← Ігри",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp).clickable(onClick = onBack),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Налаштування",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp),
        ) {
            Text("Версія додатка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = updateState) {
                is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                    Text("Перевіряємо...", style = MaterialTheme.typography.bodyMedium)
                }
                is UpdateState.UpToDate -> Text(
                    text = "У вас остання версія",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                is UpdateState.Available -> Column {
                    Text(
                        text = "Доступне оновлення ${state.info.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.info.notes.isNotBlank()) {
                        Text(
                            text = state.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = {
                        scope.launch {
                            updateState = UpdateState.Downloading(0)
                            runCatching {
                                val file = updateManager.download(state.info) { progress ->
                                    updateState = UpdateState.Downloading(progress)
                                }
                                updateState = UpdateState.ReadyToInstall(file)
                                updateManager.promptInstall(file)
                            }.onFailure {
                                updateState = UpdateState.Failed(it.message ?: "Помилка завантаження")
                            }
                        }
                    }) { Text("Завантажити й встановити") }
                }
                is UpdateState.Downloading -> Column {
                    Text("Завантаження...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is UpdateState.ReadyToInstall -> Text(
                    text = "Завантажено -- підтвердіть встановлення у системному вікні",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is UpdateState.Failed -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is UpdateState.Idle -> Unit
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    scope.launch {
                        updateState = UpdateState.Checking
                        val catalog = CatalogRepository.fetch()
                        updateState = updateManager.evaluate(catalog)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Перевірити оновлення")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Інші налаштування хабу з'являться тут у наступних версіях.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
