package cvam.dignity.juganua.features.passportphoto

import android.graphics.Bitmap

enum class PhotoPaperSize {
    A4,
    A6
}

enum class PassportBackground(
    val title: String,
    val startColor: Int,
    val endColor: Int? = null
) {
    SKY_BLUE(
        "Sky Blue",
        0xFF87CEEB.toInt()
    ),

    LIGHT_PINK(
        "Light Pink",
        0xFFFFD1DC.toInt()
    ),

    WHITE(
        "White",
        0xFFFFFFFF.toInt()
    ),

    BLUE_GRADIENT(
        "Blue",
        0xFFB8E7FF.toInt(),
        0xFF4FA3D1.toInt()
    ),

    PINK_GRADIENT(
        "Pink",
        0xFFFFD6E7.toInt(),
        0xFFB9DFFF.toInt()
    )
}

sealed interface PassportPhotoStage {

    data object Selecting : PassportPhotoStage

    data object Cropping : PassportPhotoStage

    data class BackgroundEditor(
        val croppedBitmap: Bitmap,
        val foregroundBitmap: Bitmap?,
        val resultBitmap: Bitmap?,
        val removeBackground: Boolean = true,
        val background: PassportBackground =
            PassportBackground.SKY_BLUE,
        val processing: Boolean = false
    ) : PassportPhotoStage

    data object Layout : PassportPhotoStage
}