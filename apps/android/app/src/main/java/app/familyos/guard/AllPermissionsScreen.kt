package app.familyos.guard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Все доступы в одном месте: обычные разрешения, особые доступы и ADB по Wi-Fi.
 * Порядок — от самого нужного к самому редкому.
 */
@Composable
fun AllPermissionsScreen(
    deviceOwner: Boolean,
    usageAccess: Boolean,
    accessibility: Boolean,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.count { it.value }
        message = "Выдано разрешений: $granted из ${result.size}"
        refreshKey += 1
        onChanged()
    }

    fun requestRuntime() {
        val list = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) list += android.Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 29) list += android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        runtimeLauncher.launch(list.toTypedArray())
    }

    fun open(intent: Intent, fallback: String) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { message = fallback }
    }

    val adbEnabled = remember(refreshKey) {
        runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Доступы", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { refreshKey += 1; onChanged() }) { Text("Обновить") }
        }
        Text(
            "Без этих доступов часть защиты не работает. Всё, что запрашиваем, используется только для родительского контроля.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Обычные разрешения", fontWeight = FontWeight.SemiBold)
                Text("Камера (сканирование), геолокация, уведомления", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { requestRuntime() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Запросить все сразу")
                }
            }
        }

        PermissionItem(
            title = "Статистика использования",
            done = usageAccess,
            why = "Считает экранное время и блокирует лимит",
            action = {
                open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), "Откройте Настройки → Специальные возможности → Доступ к данным об использовании")
            },
        )
        PermissionItem(
            title = "Специальные возможности",
            done = accessibility,
            why = "Блокирует чужие приложения по лимиту и во время задания",
            action = {
                open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Откройте Настройки → Специальные возможности")
            },
        )
        PermissionItem(
            title = "Поверх других приложений",
            done = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false),
            why = "Показывает экран блокировки поверх игр",
            action = {
                open(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    "Откройте Настройки → Приложения → Поверх других",
                )
            },
        )
        PermissionItem(
            title = "Device Owner",
            done = deviceOwner,
            why = "Защищает от удаления приложения и сброса настроек",
            action = { message = "Device Owner назначается при первой настройке телефона через ADB или QR" },
        )
        PermissionItem(
            title = "Отключить оптимизацию батареи",
            done = false,
            why = "Чтобы служба контроля не засыпала",
            action = {
                open(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    "Откройте Настройки → Батарея → Без ограничений",
                )
            },
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ADB по Wi-Fi", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (adbEnabled) "включён" else "выключен",
                        fontSize = 11.sp,
                        color = if (adbEnabled) Color(0xFF34D399) else Color(0xFFF87171),
                    )
                }
                Text(
                    "Нужен, чтобы родитель мог выдать доступы и настроить телефон без провода. " +
                        "Включите «Отладку по Wi-Fi» в параметрах разработчика и подтвердите сопряжение кодом.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val wireless = Intent("android.settings.WIRELESS_DEBUGGING")
                        runCatching { context.startActivity(wireless.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure {
                                open(
                                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                                    "Откройте Настройки → О телефоне → 7 раз по «Номер сборки» → Параметры разработчика",
                                )
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Открыть отладку по Wi-Fi") }
                TextButton(
                    onClick = {
                        open(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                            "Параметры разработчика недоступны",
                        )
                    },
                ) { Text("Параметры разработчика", fontSize = 12.sp) }
                Text(
                    "Адрес для сопряжения: ${localIp(context)}:5555",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Полное сопряжение как в Shizuku требует отдельного протокола ADB — " +
                        "пока приложение показывает адрес и ведёт в нужный экран настроек.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@Composable
private fun PermissionItem(title: String, done: Boolean, why: String, action: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(why, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (done) {
                Text("есть", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                TextButton(onClick = action) { Text("Выдать") }
            }
        }
    }
}

private fun localIp(context: Context): String = runCatching {
    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
    for (iface in interfaces) {
        if (!iface.isUp || iface.isLoopback) continue
        for (address in iface.inetAddresses) {
            if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                return@runCatching address.hostAddress ?: "—"
            }
        }
    }
    "—"
}.getOrDefault("—")
