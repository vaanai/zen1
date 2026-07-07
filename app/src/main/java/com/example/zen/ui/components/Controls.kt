package com.example.zen.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.design.ZenRadius
import kotlin.math.roundToInt

/**
 * Persona-tinted wrappers over the stock Material controls. The defaults leak M3's
 * grey-violet scheme (unchecked switch tracks, slider ticks, 4dp text-field corners) which
 * exists nowhere in the persona palettes — worst on parchment.
 */

@Composable
fun ZenSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = LocalPersonaColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.accent,
            checkedThumbColor = c.gradient.first(),
            uncheckedTrackColor = c.surfaceDim,
            uncheckedBorderColor = c.cardBorder,
            uncheckedThumbColor = c.textSecondary
        )
    )
}

/**
 * Continuous slider that snaps to [snap]-sized increments — no M3 tick-dot row.
 */
@Composable
fun ZenSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    snap: Int = 1
) {
    val c = LocalPersonaColors.current
    Slider(
        value = value,
        onValueChange = { raw ->
            val snapped = (raw / snap).roundToInt() * snap.toFloat()
            onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
        },
        valueRange = valueRange,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = c.accent,
            activeTrackColor = c.accent,
            inactiveTrackColor = c.textPrimary.copy(alpha = if (c.isLight) 0.14f else 0.08f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
    )
}

@Composable
fun ZenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val c = LocalPersonaColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { { Text(it, color = c.textSecondary) } },
        trailingIcon = trailingIcon,
        shape = ZenRadius.chip,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            focusedLabelColor = c.accent,
            cursorColor = c.accent,
            unfocusedBorderColor = c.cardBorder,
            unfocusedLabelColor = c.textSecondary,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary
        ),
        modifier = modifier
    )
}
