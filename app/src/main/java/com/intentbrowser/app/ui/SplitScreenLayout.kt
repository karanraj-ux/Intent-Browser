package com.intentbrowser.app.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun SplitScreenLayout(
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var weightState by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalSize = if (isLandscape) maxWidth.value else maxHeight.value
        
        val draggableState = rememberDraggableState { delta ->
            val deltaFraction = delta / totalSize
            weightState = (weightState + deltaFraction).coerceIn(0.2f, 0.8f)
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(weightState)) {
                    primaryContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal
                        ),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    VerticalDivider(
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxHeight(0.2f)
                    )
                }
                Box(modifier = Modifier.weight(1f - weightState)) {
                    secondaryContent()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(weightState)) {
                    primaryContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical
                        ),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(0.2f)
                    )
                }
                Box(modifier = Modifier.weight(1f - weightState)) {
                    secondaryContent()
                }
            }
        }
    }
}
