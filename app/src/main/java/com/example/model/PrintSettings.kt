package com.example.model

enum class ShapeProfile(val titleRu: String, val descriptionRu: String) {
    CYLINDRICAL("Цилиндрический (Горелка, ручка)", "Идеально для круглых тел вращения: баллонов, горелок, цилиндров"),
    DOME("Купольный (Органический)", "Плавное сферическое скругление от краев к центру"),
    CHAMFER("Фаска со срезом", "Наклонные боковые грани и плоская площадка сверху"),
    FLAT("Плоский барельеф", "Постоянная высота тела с четким вертикальным контуром")
}

enum class SolidType(val titleRu: String) {
    FLAT_BASE("Плоское основание (Печать без поддержек)"),
    DUAL_SIDED("Двусторонняя форма (360° тело)")
}

data class FilamentColor(
    val nameRu: String,
    val hexColor: Long
)

object FilamentPresets {
    val list = listOf(
        FilamentColor("Bambu Оранжевый", 0xFFE88A25),
        FilamentColor("PLA Шёлк Золото", 0xFFD4AF37),
        FilamentColor("Матовый Чёрный", 0xFF2B2D31),
        FilamentColor("PETG Стальной Серый", 0xFF7A838F),
        FilamentColor("Изумрудно-зелёный", 0xFF1FB57A),
        FilamentColor("Неоновый Лазурный", 0xFF00B4D8),
        FilamentColor("Белый Базовый", 0xFFE2E4E8)
    )
}

data class PrintSettings(
    val widthMm: Float = 80f,
    val reliefMm: Float = 8f,
    val baseMm: Float = 2.0f,
    val resolution: Int = 180,
    val profile: ShapeProfile = ShapeProfile.CYLINDRICAL,
    val solidType: SolidType = SolidType.FLAT_BASE,
    val detailLevel: Float = 0.20f, // 0..1 - amount of high-frequency texture embossed
    val autoContrast: Boolean = true,
    val invertLuminance: Boolean = false,
    val mirrorHorizontal: Boolean = false,
    val smoothPasses: Int = 1,
    val printerBedMm: Float = 256f,
    val nozzleDiameterMm: Float = 0.4f,
    val materialDensityGcm3: Float = 1.24f, // Standard PLA
    val infillPercent: Int = 20,
    val filamentColor: FilamentColor = FilamentPresets.list[0]
)
