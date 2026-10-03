package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAnalysisResult

@Composable
fun AnalysisComparisonView(
    aiAnalysis: AiAnalysisResult?,
    onApplyOptimalSettings: () -> Unit,
    onShowNaiveMode: () -> Unit,
    onShowOptimalMode: () -> Unit,
    isNaiveModeActive: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Comparison Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Анализ проблемы: Почему модель была испорчена?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Разбор дефекта из скриншота и решение для 3D-печати",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Mode switcher for visual confirmation
                Text(
                    text = "Переключение алгоритма генерации:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isNaiveModeActive,
                        onClick = onShowOptimalMode,
                        label = { Text("✅ Объемная инфляция (Стало)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1FB57A),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("chip_optimal_mode")
                    )

                    FilterChip(
                        selected = isNaiveModeActive,
                        onClick = onShowNaiveMode,
                        label = { Text("❌ Наивный рельеф (Было)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE53935),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("chip_naive_mode")
                    )
                }
            }
        }

        // Four Key Engineering Explanations
        Text(
            text = "4 причины сбоя на вашем фото и как мы их исправили:",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        AnalysisFactorCard(
            title = "1. Ловушка черного пластика (Инверсия высоты)",
            problemText = "Было: Матовый черный корпус горелки имеет яркость 0.0–0.15. В базовом Photo2STL высота рассчитывалась прямо из яркости, поэтому горелка превратилась в глубокую траншею и яму на дне.",
            solutionText = "Решение: Высота тела берется не из цвета, а из расстояния от контура (Distance Transform). Чёрный корпус имеет полную 3D-толщину цилиндра!",
            icon = Icons.Default.Error,
            iconTint = Color(0xFFE53935)
        )

        AnalysisFactorCard(
            title = "2. Захват текстурированного стола и бликов",
            problemText = "Было: Стол с плетеной текстурой и блики стекла заняли 85% кадра и поднялись в виде острых горных хребтов, испортив форму.",
            solutionText = "Решение: Фон стола полностью отсекается интеллектуальной AI-сегментацией или контуром. Генерируется только сам предмет!",
            icon = Icons.Default.Warning,
            iconTint = Color(0xFFFFA000)
        )

        AnalysisFactorCard(
            title = "3. Цилиндрический профиль вместо плоского блина",
            problemText = "Было: На скриншоте модель получилась плоской пластиной со случайными кочками.",
            solutionText = "Решение: Встроен полукруглый профиль (Cylindrical Profile cross-section), превращающий вытянутый силуэт горелки в реальный кругляк с гладкими скатами.",
            icon = Icons.Default.Tune,
            iconTint = Color(0xFF29B6F6)
        )

        AnalysisFactorCard(
            title = "4. Разделение геометрии и микродеталей",
            problemText = "Было: Рельеф смешивал свет/тень и форму в одну кашу.",
            solutionText = "Решение: Алгоритм разделяет крупную геометрию тела и микрорельеф (сопло, винты, стыки) с помощью полосового фильтра. Детали мягко гравируются поверх цилиндра.",
            icon = Icons.Default.CheckCircle,
            iconTint = Color(0xFF1FB57A)
        )

        // Gemini AI Detected parameters summary
        if (aiAnalysis != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2620)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF1FB57A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Результат AI анализа: ${aiAnalysis.objectName}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1FB57A),
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = aiAnalysis.analysisExplanationRu,
                        fontSize = 13.sp,
                        color = Color(0xFFD4E5DC),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onApplyOptimalSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FB57A)),
                        modifier = Modifier.fillMaxWidth().testTag("btn_apply_optimal")
                    ) {
                        Text("Применить оптимальные параметры для горелки", color = Color(0xFF04210F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisFactorCard(
    title: String,
    problemText: String,
    solutionText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = problemText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = solutionText,
                fontSize = 12.sp,
                color = Color(0xFF1FB57A),
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )
        }
    }
}
