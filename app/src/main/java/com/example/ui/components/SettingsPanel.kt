package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.model.FilamentPresets
import com.example.model.Mesh3D
import com.example.model.PrintSettings
import com.example.model.ShapeProfile
import com.example.model.SolidType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(
    settings: PrintSettings,
    mesh: Mesh3D?,
    onUpdateSettings: (PrintSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Presets
        Text(
            text = "Готовые шаблоны моделирования",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = settings.profile == ShapeProfile.CYLINDRICAL,
                onClick = {
                    onUpdateSettings(
                        settings.copy(
                            profile = ShapeProfile.CYLINDRICAL,
                            widthMm = 85f,
                            reliefMm = 12f,
                            baseMm = 2f,
                            detailLevel = 0.22f,
                            solidType = SolidType.FLAT_BASE
                        )
                    )
                },
                label = { Text("Горелка / Цилиндр") },
                modifier = Modifier.weight(1f).testTag("preset_torch")
            )

            FilterChip(
                selected = settings.profile == ShapeProfile.DOME,
                onClick = {
                    onUpdateSettings(
                        settings.copy(
                            profile = ShapeProfile.DOME,
                            widthMm = 75f,
                            reliefMm = 8f,
                            baseMm = 1.5f,
                            detailLevel = 0.15f
                        )
                    )
                },
                label = { Text("Купол / Брелок") },
                modifier = Modifier.weight(1f).testTag("preset_dome")
            )

            FilterChip(
                selected = settings.profile == ShapeProfile.FLAT,
                onClick = {
                    onUpdateSettings(
                        settings.copy(
                            profile = ShapeProfile.FLAT,
                            widthMm = 80f,
                            reliefMm = 4f,
                            baseMm = 1.2f,
                            detailLevel = 0.35f
                        )
                    )
                },
                label = { Text("Барельеф") },
                modifier = Modifier.weight(1f).testTag("preset_relief")
            )
        }

        // Section 1: Dimensions
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Размеры и геометрия", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                // Width slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Ширина модели", fontSize = 13.sp)
                        Text("${settings.widthMm.toInt()} мм", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = settings.widthMm,
                        onValueChange = { onUpdateSettings(settings.copy(widthMm = it)) },
                        valueRange = 30f..250f,
                        steps = 43,
                        modifier = Modifier.testTag("slider_width")
                    )
                }

                // Relief height slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Высота объема (толщина предмета)", fontSize = 13.sp)
                        Text("${"%.1f".format(settings.reliefMm)} мм", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = settings.reliefMm,
                        onValueChange = { onUpdateSettings(settings.copy(reliefMm = it)) },
                        valueRange = 1f..35f,
                        steps = 67,
                        modifier = Modifier.testTag("slider_relief")
                    )
                }

                // Base thickness
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Толщина плоского основания", fontSize = 13.sp)
                        Text("${"%.1f".format(settings.baseMm)} мм", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = settings.baseMm,
                        onValueChange = { onUpdateSettings(settings.copy(baseMm = it)) },
                        valueRange = 0.6f..10f,
                        steps = 46,
                        modifier = Modifier.testTag("slider_base")
                    )
                }

                // Profile picker
                Text("Форма поперечного сечения:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShapeProfile.values().forEach { prof ->
                        FilterChip(
                            selected = settings.profile == prof,
                            onClick = { onUpdateSettings(settings.copy(profile = prof)) },
                            label = { Text(prof.titleRu, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // Section 2: Texture & Details
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Тиснение деталей с фото", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "Добавляет рельефные линии (кнопки, винты, швы корпуса), не деформируя общую цилиндрическую форму.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Интенсивность деталей", fontSize = 13.sp)
                    Text("${(settings.detailLevel * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = settings.detailLevel,
                    onValueChange = { onUpdateSettings(settings.copy(detailLevel = it)) },
                    valueRange = 0f..0.8f,
                    steps = 16,
                    modifier = Modifier.testTag("slider_detail")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Авто-контраст", fontSize = 13.sp)
                        Text("Оптимизировать яркость текстуры", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.autoContrast,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoContrast = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Зеркально по горизонтали", fontSize = 13.sp)
                    Switch(
                        checked = settings.mirrorHorizontal,
                        onCheckedChange = { onUpdateSettings(settings.copy(mirrorHorizontal = it)) }
                    )
                }
            }
        }

        // Section 3: 3D Printer & Slicing check
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Параметры 3D-принтера", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Bed size selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Размер стола принтера", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(180f, 220f, 256f, 300f).forEach { bed ->
                            FilterChip(
                                selected = settings.printerBedMm == bed,
                                onClick = { onUpdateSettings(settings.copy(printerBedMm = bed)) },
                                label = { Text("${bed.toInt()} мм", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Nozzle selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Диаметр сопла", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.2f, 0.4f, 0.6f, 0.8f).forEach { noz ->
                            FilterChip(
                                selected = settings.nozzleDiameterMm == noz,
                                onClick = { onUpdateSettings(settings.copy(nozzleDiameterMm = noz)) },
                                label = { Text("$noz", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Filament color picker
                Column {
                    Text("Цвет пластика в 3D просмотре", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilamentPresets.list.forEach { fil ->
                            val isSelected = settings.filamentColor.hexColor == fil.hexColor
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(fil.hexColor))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable { onUpdateSettings(settings.copy(filamentColor = fil)) }
                            )
                        }
                    }
                }

                // Slicing Warnings
                if (mesh != null) {
                    if (mesh.stepMm < settings.nozzleDiameterMm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF29B6F6), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Шаг сетки (${"%.2f".format(mesh.stepMm)} мм) мельче сопла ${settings.nozzleDiameterMm} мм — детали будут идеально сглажены слайсером.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (mesh.widthMm > settings.printerBedMm || mesh.heightMm > settings.printerBedMm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Внимание: габариты модели превышают размер стола ${settings.printerBedMm.toInt()} мм!",
                                fontSize = 11.sp,
                                color = Color(0xFFFFA000)
                            )
                        }
                    }
                }
            }
        }
    }
}
