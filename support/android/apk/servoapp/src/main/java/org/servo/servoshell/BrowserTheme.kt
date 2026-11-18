/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.servo.servoshell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Quiet graphite chrome used by both the container home and the browser. */
private val BrowserColorScheme = darkColorScheme(
    primary = Color(0xFF5FC79A),
    onPrimary = Color(0xFF06120C),
    secondary = Color(0xFF98A4AF),
    onSecondary = Color(0xFF0B0E11),
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF141A1F),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C242B),
    onSurfaceVariant = Color(0xFF98A4AF),
    outline = Color(0xFF232C33),
)

@Composable
fun ServoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BrowserColorScheme, content = content)
}
