package cvam.dignity.juganua.features.backgroundremover

import android.graphics.Bitmap
import android.net.Uri

enum class BackgroundStyle(
    val title: String,
    val startColor: Int,
    val endColor: Int? = null
) {
    SKY_BLUE(
        title = "Sky Blue",
        startColor = 0xFF87CEEB.toInt()
    ),

    LIGHT_PINK(
        title = "Light Pink",
        startColor = 0xFFFFD1DC.toInt()
    ),

    WHITE(
        title = "White",
        startColor = 0xFFFFFFFF.toInt()
    ),

    SKY_BLUE_GRADIENT(
        title = "Blue Gradient",
        startColor = 0xFFB8E7FF.toInt(),
        endColor = 0xFF4FA3D1.toInt()
    ),

    PINK_BLUE_GRADIENT(
        title = "Pink Gradient",
        startColor = 0xFFFFD6E7.toInt(),
        endColor = 0xFFB9DFFF.toInt()
    )
}

sealed interface BackgroundRemovalState {

    data object Empty : BackgroundRemovalState

    data class LoadingImage(
        val uri: Uri
    ) : BackgroundRemovalState

    data class Processing(
        val original: Bitmap
    ) : BackgroundRemovalState

    data class Ready(
        val original: Bitmap,
        val foreground: Bitmap,
        val result: Bitmap,
        val background: BackgroundStyle
    ) : BackgroundRemovalState

    data class Error(
        val message: String,
        val original: Bitmap? = null
    ) : BackgroundRemovalState
}