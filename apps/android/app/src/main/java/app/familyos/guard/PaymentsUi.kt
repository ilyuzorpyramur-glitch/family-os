package app.familyos.guard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyos.guard.data.ParsedPayment
import app.familyos.guard.data.PaymentData
import app.familyos.guard.scan.CodeScannerDialog
import kotlinx.coroutines.launch

/**
 * Оплата по QR СБП: ребёнок сканирует код, родитель подтверждает у себя.
 * Приложение не переводит деньги — оно передаёт запрос родителю.
 */
@Composable
fun PayByQrDialog(
    limits: PaymentData?,
    onParse: suspend (String) -> ParsedPayment,
    onCreate: suspend (String, Double?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var scanning by remember { mutableStateOf(true) }
    var payload by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedPayment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (scanning) {
        CodeScannerDialog(
            title = "Оплатить по QR",
            hint = "Наведите камеру на QR-код для оплаты (СБП)",
            qrOnly = true,
            onResult = { raw ->
                scanning = false
                payload = raw
                scope.launch {
                    busy = true
                    error = null
                    runCatching { onParse(raw) }
                        .onSuccess { result ->
                            parsed = result
                            if (!result.isPayment) {
                                error = "Это не платёжный QR. Попросите код СБП у продавца."
                            } else {
                                result.amount?.let { amount = formatAmount(it) }
                                result.purpose?.let { purpose = it }
                            }
                        }
                        .onFailure { error = it.message }
                    busy = false
                }
            },
            onDismiss = onDismiss,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sent) "Запрос отправлен" else "Запрос на оплату") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sent) {
                    Text(
                        "Родитель увидит запрос и оплатит его в банковском приложении. " +
                            "Здесь появится отметка, когда он подтвердит.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    limits?.let { data ->
                        Text(
                            "В день можно просить до ${data.limits.daily} ₽, " +
                                "один запрос — до ${data.limits.perRequest} ₽. " +
                                "Сегодня уже ${"%.0f".format(data.limits.spentToday)} ₽.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        amount,
                        { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("Сумма, ₽") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        purpose,
                        { purpose = it },
                        label = { Text("За что (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    parsed?.let {
                        Text(
                            "Распознано: " + when (it.scheme) {
                                "sbp" -> "QR СБП"
                                "emv" -> "платёжный QR"
                                else -> "неизвестный код"
                            },
                            fontSize = 11.sp,
                            color = app.familyos.guard.ui.textMuted,
                        )
                    }
                }
                error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1214))) {
                        Text(it, Modifier.padding(10.dp), color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("Готово") }
            } else {
                TextButton(
                    onClick = {
                        val value = amount.replace(',', '.').toDoubleOrNull()
                        if (value == null || value <= 0) {
                            error = "Введите сумму"
                            return@TextButton
                        }
                        scope.launch {
                            busy = true
                            error = null
                            runCatching { onCreate(payload, value, purpose.ifBlank { null }) }
                                .onSuccess { sent = true }
                                .onFailure { error = it.message ?: "Не удалось отправить" }
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text(if (busy) "Отправляем…" else "Попросить родителя") }
            }
        },
        dismissButton = {
            if (!sent) TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Компактная карточка на главной: вход в оплату по QR. */
@Composable
fun PayByQrCard(
    pendingCount: Int,
    onOpen: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Оплата по QR", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (pendingCount > 0) "Ждут родителя: $pendingCount"
                    else "Отсканируй код СБП — родитель оплатит",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text("📷 Сканировать", fontSize = 12.sp) }
        }
    }
}

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
