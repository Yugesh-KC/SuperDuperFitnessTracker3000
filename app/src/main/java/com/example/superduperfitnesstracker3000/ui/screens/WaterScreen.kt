package com.example.superduperfitnesstracker3000.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.superduperfitnesstracker3000.data.ReminderSettings
import com.example.superduperfitnesstracker3000.data.UserProfile
import com.example.superduperfitnesstracker3000.data.WaterEntry
import com.example.superduperfitnesstracker3000.reminders.WaterReminders
import com.example.superduperfitnesstracker3000.ui.components.ChartPoint
import com.example.superduperfitnesstracker3000.ui.components.DailyBarChart
import com.example.superduperfitnesstracker3000.ui.components.DateNavigator
import com.example.superduperfitnesstracker3000.ui.components.EmptyHint
import com.example.superduperfitnesstracker3000.ui.components.ProgressRing
import com.example.superduperfitnesstracker3000.ui.components.SectionCard
import com.example.superduperfitnesstracker3000.ui.theme.Accent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val quickAmounts = listOf(150, 250, 330, 500)
private val intervals = listOf(30, 60, 90, 120, 180)
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun WaterScreen(
    water: List<WaterEntry>,
    profile: UserProfile,
    reminders: ReminderSettings,
    onAdd: (LocalDate, Int) -> Unit,
    onDelete: (Long) -> Unit,
    onSaveTarget: (Int) -> Unit,
    onSaveReminders: (ReminderSettings) -> Unit,
    onTestReminder: () -> Unit,
    contentPadding: PaddingValues,
) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var customDialog by remember { mutableStateOf(false) }
    var targetDialog by remember { mutableStateOf(false) }
    val target = profile.effectiveWaterTargetMl
    val dayEntries = water.filter { it.date == date }
    val total = dayEntries.sumOf { it.ml }
    val today = LocalDate.now()
    val chartFrom = today.minusDays(13)
    val daily = water.filter { it.date >= chartFrom }.groupBy { it.date }.map { (d, v) -> ChartPoint(d, v.sumOf { it.ml }.toDouble()) }
    val blue = Accent.water

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DateNavigator(date, onDateChange = { date = it }) }

        item {
            SectionCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressRing(progress = total.toFloat() / target, color = blue, modifier = Modifier.size(190.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = blue)
                            Text("$total ml", style = MaterialTheme.typography.headlineMedium)
                            TextButton(onClick = { targetDialog = true }) {
                                Text("of $target ml", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        when {
                            total >= target -> "Goal reached — nice work! 🎉"
                            total == 0 -> "Nothing yet. Start with a glass!"
                            else -> "${target - total} ml to go"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickAmounts.forEach { ml ->
                            FilledTonalButton(
                                onClick = { onAdd(date, ml) },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = blue.copy(alpha = 0.15f), contentColor = blue),
                            ) { Text("+$ml") }
                        }
                        OutlinedButton(onClick = { customDialog = true }) { Text("Custom") }
                    }
                }
            }
        }

        item { ReminderCard(reminders, onSaveReminders, onTestReminder) }

        item {
            SectionCard(title = "Last 14 days") {
                DailyBarChart(
                    points = daily,
                    from = chartFrom,
                    to = today,
                    target = target.toDouble(),
                    unit = "ml",
                    emptyText = "No water logged in this range",
                    color = blue,
                )
            }
        }

        item { Text("Logged", style = MaterialTheme.typography.titleMedium) }
        if (dayEntries.isEmpty()) item { EmptyHint("No water logged for this day.") }
        items(dayEntries.asReversed(), key = { it.id }) { e ->
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = blue, modifier = Modifier.size(20.dp))
                    Text("${e.ml} ml", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    Text(
                        Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { onDelete(e.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
                HorizontalDivider()
            }
        }
    }

    if (customDialog) {
        NumberDialog("Add water", "Amount (ml)", "", onDismiss = { customDialog = false }) {
            if (it in 1..5000) onAdd(date, it)
            customDialog = false
        }
    }
    if (targetDialog) {
        NumberDialog("Daily water goal", "Goal (ml)", target.toString(), onDismiss = { targetDialog = false }) {
            if (it in 500..8000) onSaveTarget(it)
            targetDialog = false
        }
    }
}

@Composable
private fun ReminderCard(r: ReminderSettings, onSave: (ReminderSettings) -> Unit, onTest: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onSave(r.copy(enabled = true))
    }
    fun pickTime(minute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
    }
    val blue = Accent.water
    // Re-checked whenever we come back from the system settings screen.
    var exactAllowed by remember { mutableStateOf(WaterReminders.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        val now = WaterReminders.canScheduleExact(context)
        if (now != exactAllowed) {
            exactAllowed = now
            WaterReminders.schedule(context)
        }
        onPauseOrDispose { }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (r.enabled) "Every ${intervalLabel(r.intervalMinutes)}, ${fmtMinute(r.startMinute)} – ${fmtMinute(r.endMinute)}. Skipped once you hit your goal."
                    else "Get a nudge to drink during the day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = r.enabled,
                onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !WaterReminders.canNotify(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onSave(r.copy(enabled = on))
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = blue),
            )
        }
        if (r.enabled) {
            Text("Remind me every", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                intervals.forEach { m ->
                    FilterChip(
                        selected = r.intervalMinutes == m,
                        onClick = { onSave(r.copy(intervalMinutes = m)) },
                        label = { Text(intervalLabel(m)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = blue.copy(alpha = 0.2f)),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("From", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickTime(r.startMinute) { onSave(r.copy(startMinute = it)) } }) { Text(fmtMinute(r.startMinute)) }
                Text("to", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickTime(r.endMinute) { onSave(r.copy(endMinute = it)) } }) { Text(fmtMinute(r.endMinute)) }
            }
            if (!exactAllowed && Build.VERSION.SDK_INT >= 31) {
                Text(
                    "Reminders can arrive up to 10 minutes late. Allow \"Alarms & reminders\" for exact timing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("Allow exact timing") }
            }
            TextButton(onClick = onTest) { Text("Send a test notification") }
        }
    }
}

@Composable
private fun NumberDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }, enabled = text.isNotEmpty()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun intervalLabel(minutes: Int) = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60}.${(minutes % 60) * 10 / 60} h"
}

private fun fmtMinute(m: Int): String = LocalTime.of(m / 60, m % 60).format(timeFmt)
