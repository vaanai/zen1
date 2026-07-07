package com.example.zen.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.design.ZenSpacing

/**
 * The one header treatment for every page: title on the left, optional kicker-style subtitle
 * under it, optional trailing controls. Gutter-aligned with the content below so titles and
 * cards share an edge.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val c = LocalPersonaColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ZenSpacing.xl, bottom = ZenSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(ZenSpacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = subtitleColor ?: c.textSecondary
                )
            }
        }
        trailing()
    }
}
