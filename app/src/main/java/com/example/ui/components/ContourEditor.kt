package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

@Composable
fun ContourEditor(
    bitmap: Bitmap,
    currentContour: List<PointF>?,
    onContourUpdated: (List<PointF>?) -> Unit,
    onAutoAiSegment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val points = remember(currentContour) {
        mutableStateListOf<PointF>().apply {
            currentContour?.let { addAll(it) }
        }
    }
    var isDrawing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Explanatory guidance card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Обведите предмет пальцем или нажмите «Авто AI», чтобы отсечь стол и построить 3D-модель только по корпусу горелки.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Interactive photo canvas with lasso drawing
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16181B))
                .testTag("contour_editor_canvas")
        ) {
            val imgBitmap = remember(bitmap) { bitmap.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDrawing = true
                                points.clear()
                                val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                val normY = (offset.y / size.height).coerceIn(0f, 1f)
                                points.add(PointF(normX, normY))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                                val normY = (change.position.y / size.height).coerceIn(0f, 1f)
                                val last = points.lastOrNull()
                                if (last == null || hypot((normX - last.x) * size.width, (normY - last.y) * size.height) > 12f) {
                                    points.add(PointF(normX, normY))
                                }
                            },
                            onDragEnd = {
                                isDrawing = false
                                if (points.size >= 4) {
                                    onContourUpdated(points.toList())
                                }
                            },
                            onDragCancel = {
                                isDrawing = false
                            }
                        )
                    }
            ) {
                // Calculate aspect-fit image positioning
                val imgW = bitmap.width.toFloat()
                val imgH = bitmap.height.toFloat()
                val scale = minOf(size.width / imgW, size.height / imgH)
                val drawW = imgW * scale
                val drawH = imgH * scale
                val ox = (size.width - drawW) / 2f
                val oy = (size.height - drawH) / 2f

                drawImage(
                    image = imgBitmap,
                    dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                    dstSize = IntSize(drawW.toInt(), drawH.toInt())
                )

                // Darken outside area if closed contour exists
                if (points.size >= 3) {
                    val path = Path()
                    points.forEachIndexed { idx, pt ->
                        val px = pt.x * size.width
                        val py = pt.y * size.height
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()

                    // Draw semi-transparent emerald fill over selected object
                    drawPath(
                        path = path,
                        color = Color(0x331FB57A)
                    )

                    // Draw bright contour stroke
                    drawPath(
                        path = path,
                        color = Color(0xFF1FB57A),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw vertices
                    points.forEach { pt ->
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(pt.x * size.width, pt.y * size.height)
                        )
                    }
                }
            }
        }

        // Toolbar buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAutoAiSegment,
                modifier = Modifier.weight(1f).testTag("btn_auto_ai"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Авто AI", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    points.clear()
                    onContourUpdated(null)
                },
                modifier = Modifier.weight(1f).testTag("btn_reset_contour")
            ) {
                Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Весь кадр", fontSize = 13.sp)
            }
        }
    }
}
