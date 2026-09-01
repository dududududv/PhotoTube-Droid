package com.yunai.phototube.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit,
) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initial?.toEpochDay()?.times(MILLIS_PER_DAY),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onSelected(LocalDate.ofEpochDay(it / MILLIS_PER_DAY))
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = pickerState) }
}

private const val MILLIS_PER_DAY = 86_400_000L
