package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AiAnalysisResult
import com.example.ai.GeminiVisionAnalyzer
import com.example.engine.DistanceTransform
import com.example.engine.ImageProcessor
import com.example.engine.MeshBuilder
import com.example.engine.StlExporter
import com.example.model.Mesh3D
import com.example.model.PrintSettings
import com.example.model.ShapeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Photo3DViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _currentPhotoName = MutableStateFlow("torch_sample.jpg")
    val currentPhotoName: StateFlow<String> = _currentPhotoName.asStateFlow()

    private val _currentContour = MutableStateFlow<List<PointF>?>(null)
    val currentContour: StateFlow<List<PointF>?> = _currentContour.asStateFlow()

    private val _printSettings = MutableStateFlow(PrintSettings())
    val printSettings: StateFlow<PrintSettings> = _printSettings.asStateFlow()

    private val _mesh = MutableStateFlow<Mesh3D?>(null)
    val mesh: StateFlow<Mesh3D?> = _mesh.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing: StateFlow<Boolean> = _isAiAnalyzing.asStateFlow()

    private val _aiAnalysis = MutableStateFlow<AiAnalysisResult?>(null)
    val aiAnalysis: StateFlow<AiAnalysisResult?> = _aiAnalysis.asStateFlow()

    private val _statusMessage = MutableStateFlow("Готово к работе")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isNaiveComparisonMode = MutableStateFlow(false)
    val isNaiveComparisonMode: StateFlow<Boolean> = _isNaiveComparisonMode.asStateFlow()

    private var meshGenerationJob: Job? = null

    init {
        // Load default sample on launch so app starts with an active demonstration
        loadSampleTorchDemo()
    }

    fun loadBitmap(bitmap: Bitmap, fileName: String = "photo.jpg") {
        _currentBitmap.value = bitmap
        _currentPhotoName.value = fileName
        _currentContour.value = null
        _aiAnalysis.value = null
        _statusMessage.value = "Фото загружено: $fileName"

        // Automatically trigger initial AI analysis and mesh generation
        triggerAiAnalysis()
    }

    fun loadBitmapFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            loadBitmap(bmp, "photo_${System.currentTimeMillis()}.jpg")
                        }
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка открытия: ${e.message}"
            }
        }
    }

    fun loadSampleTorchDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val resId = R.drawable.sample_torch
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = BitmapFactory.decodeResource(context.resources, resId, options)
                    ?: generateSyntheticTorchBitmap()

                withContext(Dispatchers.Main) {
                    _currentBitmap.value = bmp
                    _currentPhotoName.value = "gas_torch_photo.jpg"
                    _statusMessage.value = "Загружен образец газовой горелки"
                    triggerAiAnalysis()
                }
            } catch (e: Exception) {
                val fallbackBmp = generateSyntheticTorchBitmap()
                withContext(Dispatchers.Main) {
                    _currentBitmap.value = fallbackBmp
                    _currentPhotoName.value = "gas_torch_demo.jpg"
                    rebuildMesh()
                }
            }
        }
    }

    fun updateSettings(newSettings: PrintSettings) {
        _printSettings.value = newSettings
        rebuildMesh()
    }

    fun updateContour(newContour: List<PointF>?) {
        _currentContour.value = newContour
        rebuildMesh()
    }

    fun toggleNaiveComparisonMode(enable: Boolean) {
        _isNaiveComparisonMode.value = enable
        rebuildMesh()
    }

    fun triggerAiAnalysis() {
        val bmp = _currentBitmap.value ?: return
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            _statusMessage.value = "ИИ анализирует геометрию и контур предмета..."
            try {
                val result = GeminiVisionAnalyzer.analyzeImage(bmp)
                _aiAnalysis.value = result
                _statusMessage.value = "Обнаружено: ${result.objectName}"

                if (result.detectedContour != null && _currentContour.value == null) {
                    _currentContour.value = result.detectedContour
                }

                _printSettings.value = _printSettings.value.copy(
                    profile = result.recommendedProfile,
                    reliefMm = result.recommendedReliefMm,
                    widthMm = result.recommendedWidthMm
                )
            } catch (e: Exception) {
                _statusMessage.value = "Локальный анализ применён"
            } finally {
                _isAiAnalyzing.value = false
                rebuildMesh()
            }
        }
    }

    fun applyOptimalAiSettings() {
        val analysis = _aiAnalysis.value ?: return
        _printSettings.value = _printSettings.value.copy(
            profile = analysis.recommendedProfile,
            reliefMm = analysis.recommendedReliefMm,
            widthMm = analysis.recommendedWidthMm,
            baseMm = 2.0f,
            detailLevel = 0.22f,
            autoContrast = true
        )
        if (analysis.detectedContour != null) {
            _currentContour.value = analysis.detectedContour
        }
        _isNaiveComparisonMode.value = false
        rebuildMesh()
    }

    fun rebuildMesh() {
        val bmp = _currentBitmap.value ?: return
        meshGenerationJob?.cancel()

        meshGenerationJob = viewModelScope.launch(Dispatchers.Default) {
            _isGenerating.value = true
            val t0 = System.currentTimeMillis()

            try {
                val settings = _printSettings.value
                val isNaive = _isNaiveComparisonMode.value

                val newMesh = if (isNaive) {
                    // NAIVE BROKEN MODE (as shown in user's screenshot):
                    // No mask (whole table included), height = raw brightness directly!
                    val grid = ImageProcessor.processBitmap(bmp, polygonOutline = null, settings = settings)
                    val naiveMask = ByteArray(grid.rows * grid.cols) { 1.toByte() }
                    // Raw brightness: dark object = 0 = sunken pit!
                    MeshBuilder.buildMesh(grid.rawLuminance, naiveMask, grid.rows, grid.cols, settings)
                } else {
                    // OPTIMAL SMART 3D SILHOUETTE INFLATION:
                    val grid = ImageProcessor.processBitmap(bmp, polygonOutline = _currentContour.value, settings = settings)
                    val distField = DistanceTransform.computeDistanceField(grid.mask, grid.rows, grid.cols)
                    val heightMap = DistanceTransform.shapeFromSilhouette(
                        distField = distField,
                        mask = grid.mask,
                        detailTexture = grid.detailTexture,
                        rows = grid.rows,
                        cols = grid.cols,
                        profile = settings.profile,
                        detailAmount = settings.detailLevel
                    )
                    MeshBuilder.buildMesh(heightMap, grid.mask, grid.rows, grid.cols, settings)
                }

                val dt = System.currentTimeMillis() - t0
                _mesh.value = newMesh
                _statusMessage.value = "3D модель готова (${newMesh.triangleCount} полигонов, ${dt} мс)"
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "Ошибка расчета: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun exportStl(context: Context, onResult: (String) -> Unit) {
        val currentMesh = _mesh.value
        if (currentMesh == null || currentMesh.triangleCount == 0) {
            onResult("Сначала постройте 3D-модель")
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Экспорт STL файла..."
            val baseName = _currentPhotoName.value.substringBeforeLast(".")
            val result = StlExporter.saveStlToDownloads(context, currentMesh, "Photo2STL_${baseName}")
            result.onSuccess { msg ->
                _statusMessage.value = "Успешно: $msg"
                onResult(msg)
            }.onFailure { err ->
                _statusMessage.value = "Ошибка: ${err.message}"
                onResult("Ошибка сохранения: ${err.message}")
            }
        }
    }

    fun shareStl(context: Context, onShareReady: (android.content.Intent) -> Unit) {
        val currentMesh = _mesh.value ?: return
        viewModelScope.launch {
            val baseName = _currentPhotoName.value.substringBeforeLast(".")
            val intent = StlExporter.createShareIntent(context, currentMesh, "Photo2STL_${baseName}")
            if (intent != null) {
                onShareReady(intent)
            } else {
                _statusMessage.value = "Не удалось подготовить STL к отправке"
            }
        }
    }

    private fun generateSyntheticTorchBitmap(): Bitmap {
        val w = 600
        val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        // Background table
        canvas.drawColor(android.graphics.Color.rgb(45, 55, 60))
        // Torch body (dark cylinder)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(30, 32, 35)
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(150f, 250f, 450f, 550f, 60f, 60f, paint)
        // Nozzle
        paint.color = android.graphics.Color.rgb(20, 20, 20)
        canvas.drawRoundRect(350f, 200f, 500f, 320f, 20f, 20f, paint)
        return bmp
    }
}
