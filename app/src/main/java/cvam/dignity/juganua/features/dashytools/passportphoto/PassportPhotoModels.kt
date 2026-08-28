package cvam.dignity.juganua.features.dashytools.passportphoto

import android.graphics.Bitmap

enum class PhotoPaperSize { A4, A6 }

data class PhotoGridConfig(
    val photosPerSlot: Int,
    val hasBorder: Boolean,
    val borderColor: Int,
    val paperSize: PhotoPaperSize
)

