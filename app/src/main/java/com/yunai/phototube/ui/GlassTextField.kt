package com.yunai.phototube.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.theme.AppTheme

/** A readable inset field on glass; avoid stacking a second refracting surface. */
@Composable
internal fun GlassTextField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = Int.MAX_VALUE,
    isError: Boolean = false, supportingText: (@Composable () -> Unit)? = null) {
    val colors = AppTheme.colors
    TextField(value, onValueChange, modifier.fillMaxWidth(), enabled = enabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        isError = isError, supportingText = supportingText,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        label = { Text(label) }, shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.subtle, unfocusedContainerColor = colors.subtle,
            disabledContainerColor = colors.subtle, errorContainerColor = colors.subtle,
            errorTextColor = colors.text, focusedSupportingTextColor = colors.secondary, unfocusedSupportingTextColor = colors.secondary,
            focusedTextColor = colors.text, unfocusedTextColor = colors.text,
            focusedLabelColor = colors.accent, unfocusedLabelColor = colors.secondary,
            cursorColor = colors.accent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent))
}
