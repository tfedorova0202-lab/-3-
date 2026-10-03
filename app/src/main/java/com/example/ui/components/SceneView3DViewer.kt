package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.StlSceneViewLoader
import com.example.model.Mesh3D
import com.example.model.PrintSettings
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.node.GeometryNode
import kotlinx.coroutines.delay

@Composable
fun SceneView3DViewer(
    mesh: Mesh3D?,
    settings: PrintSettings,
    modifier: Modifier = Modifier
) {
    var sceneViewRef by remember { mutableStateOf<SceneView?>(null) }
    var currentNode by remember { mutableStateOf<GeometryNode?>(null) }
    var isAutoSpinning by remember { mutableStateOf(false) }
    var isMetallicPbr by remember { mutableStateOf(false) }

    // Auto-spin animation
    LaunchedEffect(isAutoSpinning, currentNode) {
        val node = currentNode
        if (isAutoSpinning && node != null) {
            while (isAutoSpinning) {
                val currentRot = node.rotation
                node.rotation = Float3(currentRot.x, currentRot.y + 0.8f, currentRot.z)
                delay(16)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101215))
            .testTag("sceneview_3d_container")
    ) {
        if (mesh != null && mesh.triangleCount > 0) {
            AndroidView(
                modifier = Modifier.fillMaxSize().testTag("sceneview_surface"),
                factory = { context ->
                    SceneView(context).apply {
                        sceneViewRef = this

                        // Background color matching CAD environment
                        renderer.clearOptions = renderer.clearOptions.apply {
                            clear = true
                        }

                        // Configure initial camera
                        cameraNode.position = Float3(0f, 0.4f, 1.8f)
                        cameraNode.lookAt(Float3(0f, 0f, 0f))

                        // Load initial mesh node
                        val matColor = (settings.filamentColor.hexColor or 0xFF000000).toInt()
                        val material = materialLoader.createColorInstance(
                            color = matColor,
                            metallic = if (isMetallicPbr) 0.85f else 0.15f,
                            roughness = if (isMetallicPbr) 0.25f else 0.45f,
                            reflectance = 0.5f
                        )

                        val node = StlSceneViewLoader.createNodeFromMesh(engine, material, mesh)
                        node.position = Float3(0f, 0f, 0f)
                        addChildNode(node)
                        currentNode = node
                    }
                },
                update = { sceneView ->
                    sceneViewRef = sceneView
                    // Rebuild node when mesh or filament color changes
                    currentNode?.let { oldNode ->
                        sceneView.removeChildNode(oldNode)
                        oldNode.destroy()
                    }

                    val matColor = (settings.filamentColor.hexColor or 0xFF000000).toInt()
                    val material = sceneView.materialLoader.createColorInstance(
                        color = matColor,
                        metallic = if (isMetallicPbr) 0.85f else 0.15f,
                        roughness = if (isMetallicPbr) 0.25f else 0.45f,
                        reflectance = 0.5f
                    )

                    val newNode = StlSceneViewLoader.createNodeFromMesh(sceneView.engine, material, mesh)
                    newNode.position = Float3(0f, 0f, 0f)
                    sceneView.addChildNode(newNode)
                    currentNode = newNode
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text("Подготовка STL модели...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Top Action Controls (SceneView features)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            AssistChip(
                onClick = {
                    sceneViewRef?.let { sv ->
                        sv.cameraNode.position = Float3(0f, 0.4f, 1.8f)
                        sv.cameraNode.lookAt(Float3(0f, 0f, 0f))
                        currentNode?.rotation = Float3(0f, 0f, 0f)
                    }
                },
                label = { Text("Сброс ракурса", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x9925282D))
            )

            Spacer(modifier = Modifier.width(6.dp))

            AssistChip(
                onClick = { isAutoSpinning = !isAutoSpinning },
                label = { Text(if (isAutoSpinning) "Остановить" else "Вращение", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isAutoSpinning) MaterialTheme.colorScheme.primaryContainer else Color(0x9925282D)
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            AssistChip(
                onClick = { isMetallicPbr = !isMetallicPbr },
                label = { Text(if (isMetallicPbr) "PBR Шёлк" else "PLA Матовый", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isMetallicPbr) Color(0xFFE8A33D) else Color(0x9925282D),
                    labelColor = if (isMetallicPbr) Color.Black else Color.White
                )
            )
        }

        // SceneView & Filament Engine Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xCC1A1D22)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1FB57A))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SceneView 3D (Filament)",
                    color = Color(0xFFD4D8DE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Bottom Model Information Badge
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
                        text = "STL: ${mesh.widthMm.toInt()}×${mesh.heightMm.toInt()}×${mesh.thicknessMm.toInt()} мм  •  ${mesh.triangleCount} полиг.  •  ~${"%.1f".format(mesh.estimatedWeightGrams)} г PLA",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentNode?.destroy()
        }
    }
}
