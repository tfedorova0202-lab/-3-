package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AnalysisComparisonView
import com.example.ui.components.ContourEditor
import com.example.ui.components.SettingsPanel
import com.example.ui.components.Viewport3D
import kotlinx.coroutines.launch

enum class AppTab(val titleRu: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    VIEW_3D("3D Модель", Icons.Default.ViewInAr),
    SETTINGS("Настройки", Icons.Default.Tune),
    CONTOUR("Контур", Icons.Default.Crop),
    ANALYSIS("Анализ", Icons.Default.Analytics)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Photo3DApp(
    viewModel: Photo3DViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentBitmap by viewModel.currentBitmap.collectAsStateWithLifecycle()
    val currentContour by viewModel.currentContour.collectAsStateWithLifecycle()
    val printSettings by viewModel.printSettings.collectAsStateWithLifecycle()
    val mesh by viewModel.mesh.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isAiAnalyzing by viewModel.isAiAnalyzing.collectAsStateWithLifecycle()
    val aiAnalysis by viewModel.aiAnalysis.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isNaiveMode by viewModel.isNaiveComparisonMode.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Android Photo Picker (zero storage permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadBitmapFromUri(context, uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Photo2STL 3D",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (isNaiveMode) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFE53935),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "РЕЖИМ: БЫЛО",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = statusMessage,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    // Open Photo Picker
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("action_pick_photo")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Выбрать фото")
                    }

                    // Share STL
                    IconButton(
                        onClick = {
                            viewModel.shareStl(context) { shareIntent ->
                                context.startActivity(Intent.createChooser(shareIntent, "Открыть STL в слайсере"))
                            }
                        },
                        modifier = Modifier.testTag("action_share_stl")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться STL")
                    }

                    // Export STL
                    IconButton(
                        onClick = {
                            viewModel.exportStl(context) { resultMsg ->
                                scope.launch { snackbarHostState.showSnackbar(resultMsg) }
                                Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.testTag("action_export_stl")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Сохранить STL", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppTab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = tab.titleRu) },
                        label = { Text(tab.titleRu, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = {
                        viewModel.exportStl(context) { resultMsg ->
                            scope.launch { snackbarHostState.showSnackbar(resultMsg) }
                            Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_export_stl")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Экспорт STL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    // Main 3D Viewport Screen
                    Viewport3D(
                        mesh = mesh,
                        settings = printSettings,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
                1 -> {
                    // Slicing & Geometry Settings
                    SettingsPanel(
                        settings = printSettings,
                        mesh = mesh,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                2 -> {
                    // Interactive Contour and Segmentation
                    if (currentBitmap != null) {
                        ContourEditor(
                            bitmap = currentBitmap!!,
                            currentContour = currentContour,
                            onContourUpdated = { viewModel.updateContour(it) },
                            onAutoAiSegment = { viewModel.triggerAiAnalysis() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Загрузите фото для выделения контура")
                        }
                    }
                }
                3 -> {
                    // Analysis & Before/After Comparison
                    AnalysisComparisonView(
                        aiAnalysis = aiAnalysis,
                        onApplyOptimalSettings = {
                            viewModel.applyOptimalAiSettings()
                            selectedTabIndex = 0
                        },
                        onShowNaiveMode = {
                            viewModel.toggleNaiveComparisonMode(true)
                            selectedTabIndex = 0
                        },
                        onShowOptimalMode = {
                            viewModel.toggleNaiveComparisonMode(false)
                            selectedTabIndex = 0
                        },
                        isNaiveModeActive = isNaiveMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Loading overlay for generation / AI analysis
            if (isGenerating || isAiAnalyzing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDD1E2126),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isAiAnalyzing) "AI анализ формы..." else "Генерация 3D сетки...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
