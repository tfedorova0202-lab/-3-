package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Mesh3D
import com.example.model.PrintSettings
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Viewport3D(
    mesh: Mesh3D?,
    settings: PrintSettings,
    modifier: Modifier = Modifier
) {
    var azimuth by remember { mutableFloatStateOf(-0.85f) }
    var elevation by remember { mutableFloatStateOf(0.72f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var showWireframe by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141619))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.4f, 4.5f)
                    if (zoom == 1.0f) {
                        // Rotation drag
                        azimuth += pan.x * 0.008f
                        elevation = (elevation + pan.y * 0.008f).coerceIn(0.12f, 1.54f)
                    } else {
                        // Pan when pinching
                        panX += pan.x
                        panY += pan.y
                    }
                }
            }
            .testTag("viewport_3d_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f + panX
            val cy = size.height / 2f + panY

            // Camera orientation
            val cosAz = cos(azimuth)
            val sinAz = sin(azimuth)
            val cosEl = cos(elevation)
            val sinEl = sin(elevation)

            // Dynamic camera distance based on model & bed
            val baseDim = mesh?.let { maxOf(it.widthMm, it.heightMm, it.thicknessMm) } ?: settings.printerBedMm
            val focalLength = (minOf(size.width, size.height) * 0.95f) * zoomScale
            val camDist = baseDim * 2.1f

            val projectPoint = { x: Float, y: Float, z: Float ->
                // Rotate by Azimuth around Z
                val x1 = x * cosAz - y * sinAz
                val y1 = x * sinAz + y * cosAz
                val z1 = z

                // Rotate by Elevation around X'
                val xCam = x1
                val yCam = y1 * cosEl - z1 * sinEl
                val zCam = y1 * sinEl + z1 * cosEl + camDist

                if (zCam > 1f) {
                    val pX = cx + (xCam / zCam) * focalLength
                    val pY = cy - (yCam / zCam) * focalLength
                    Offset(pX, pY) to zCam
                } else {
                    Offset(cx, cy) to zCam
                }
            }

            // 1. Draw 3D Printer Bed Grid
            drawPrinterBed(
                bedSizeMm = settings.printerBedMm,
                project = projectPoint
            )

            // 2. Draw 3D Solid Mesh
            if (mesh != null && mesh.triangleCount > 0) {
                drawMeshTriangles(
                    mesh = mesh,
                    project = projectPoint,
                    filamentColor = Color(settings.filamentColor.hexColor),
                    showWireframe = showWireframe,
                    viewCosEl = cosEl,
                    viewSinEl = sinEl,
                    viewCosAz = cosAz,
                    viewSinAz = sinAz
                )
            }
        }

        // Camera control overlay chips
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            AssistChip(
                onClick = { azimuth = -0.78f; elevation = 0.65f; zoomScale = 1.0f; panX = 0f; panY = 0f },
                label = { Text("Изометрия", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x9925282D))
            )
            Spacer(modifier = Modifier.width(6.dp))
            AssistChip(
                onClick = { azimuth = -1.57f; elevation = 0.15f },
                label = { Text("Спереди", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x9925282D))
            )
            Spacer(modifier = Modifier.width(6.dp))
            AssistChip(
                onClick = { azimuth = -1.57f; elevation = 1.52f },
                label = { Text("Сверху", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x9925282D))
            )
        }

        // Action controls (Wireframe, Fit view)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xCC25282D)
            ) {
                Row {
                    IconButton(
                        onClick = { showWireframe = !showWireframe },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Сетка",
                            tint = if (showWireframe) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(
                        onClick = { azimuth = -0.85f; elevation = 0.72f; zoomScale = 1.0f; panX = 0f; panY = 0f },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Центрировать",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom stats & dimensions pill
        if (mesh != null && mesh.triangleCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE1E2126),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${mesh.widthMm.toInt()} × ${mesh.heightMm.toInt()} × ${mesh.thicknessMm.toInt()} мм  •  ${mesh.triangleCount} полиг.  •  ~${"%.1f".format(mesh.estimatedWeightGrams)} г PLA",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPrinterBed(
    bedSizeMm: Float,
    project: (Float, Float, Float) -> Pair<Offset, Float>
) {
    val half = bedSizeMm / 2f
    val minorStep = 10f
    val majorStep = 50f

    val minorColor = Color(0x1F7A8599)
    val majorColor = Color(0x4095A2B8)
    val borderColor = Color(0xAA1FB57A)

    var cur = -half
    while (cur <= half) {
        val isMajor = (cur % majorStep == 0f) || (cur == -half) || (cur == half)
        val col = if (isMajor) majorColor else minorColor
        val strokeW = if (isMajor) 1.5f else 0.8f

        // X line
        val p1 = project(cur, -half, 0f).first
        val p2 = project(cur, half, 0f).first
        drawLine(col, p1, p2, strokeWidth = strokeW)

        // Y line
        val p3 = project(-half, cur, 0f).first
        val p4 = project(half, cur, 0f).first
        drawLine(col, p3, p4, strokeWidth = strokeW)

        cur += minorStep
    }

    // Border
    val b1 = project(-half, -half, 0f).first
    val b2 = project(half, -half, 0f).first
    val b3 = project(half, half, 0f).first
    val b4 = project(-half, half, 0f).first
    drawLine(borderColor, b1, b2, strokeWidth = 2f)
    drawLine(borderColor, b2, b3, strokeWidth = 2f)
    drawLine(borderColor, b3, b4, strokeWidth = 2f)
    drawLine(borderColor, b4, b1, strokeWidth = 2f)
}

private fun DrawScope.drawMeshTriangles(
    mesh: Mesh3D,
    project: (Float, Float, Float) -> Pair<Offset, Float>,
    filamentColor: Color,
    showWireframe: Boolean,
    viewCosEl: Float,
    viewSinEl: Float,
    viewCosAz: Float,
    viewSinAz: Float
) {
    val pos = mesh.positions
    val numTris = mesh.triangleCount

    // Adaptive decimation for smooth rendering in Compose Canvas if triangles exceed 6000
    val stride = if (numTris > 12000) 3 else if (numTris > 7000) 2 else 1

    // Light direction (key light from top-left front)
    val lx = 0.35f
    val ly = -0.55f
    val lz = 0.75f
    val lLen = sqrt(lx * lx + ly * ly + lz * lz)
    val nlx = lx / lLen; val nly = ly / lLen; val nlz = lz / lLen

    // Camera view direction
    val cVx = -viewSinAz * viewCosEl
    val cVy = -viewCosAz * viewCosEl
    val cVz = -viewSinEl

    val path = Path()

    var i = 0
    while (i < pos.size) {
        val ax = pos[i]; val ay = pos[i + 1]; val az = pos[i + 2]
        val bx = pos[i + 3]; val by = pos[i + 4]; val bz = pos[i + 5]
        val cx = pos[i + 6]; val cy = pos[i + 7]; val cz = pos[i + 8]

        // Normal computation
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val nLen = sqrt(nx * nx + ny * ny + nz * nz)

        if (nLen > 1e-6f) {
            nx /= nLen; ny /= nLen; nz /= nLen

            // Backface culling check against camera ray
            val dotCam = nx * cVx + ny * cVy + nz * cVz
            if (dotCam < 0.25f) {
                // Front or semi-front facing
                val (pa, _) = project(ax, ay, az)
                val (pb, _) = project(bx, by, bz)
                val (pc, _) = project(cx, cy, cz)

                // Shading calculation
                val dotL = (nx * nlx + ny * nly + nz * nlz).coerceIn(0f, 1f)
                val brightness = 0.22f + 0.78f * dotL

                val shadedColor = Color(
                    red = (filamentColor.red * brightness).coerceIn(0f, 1f),
                    green = (filamentColor.green * brightness).coerceIn(0f, 1f),
                    blue = (filamentColor.blue * brightness).coerceIn(0f, 1f),
                    alpha = 1f
                )

                path.reset()
                path.moveTo(pa.x, pa.y)
                path.lineTo(pb.x, pb.y)
                path.lineTo(pc.x, pc.y)
                path.close()

                drawPath(path, shadedColor)

                if (showWireframe) {
                    drawPath(path, Color(0x33FFFFFF), style = Stroke(width = 0.5f))
                }
            }
        }

        i += 9 * stride
    }
}
