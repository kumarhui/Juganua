package cvam.dignity.juganua.features.pdf.id_card_splitter

import android.graphics.Bitmap

enum class FlowState {
    PAGE_OVERVIEW, PREVIEW_READY, CROPPED, SLICED
}

enum class PaperSize { A4, A6 }

/**
 * POS_1 to POS_6 are used for the 6-row layout.
 * POS_1, POS_2, POS_3 (Center), POS_4, POS_5 are used for the 5-slot stacked layout.
 */
enum class PrintPosition {
    POS_1, POS_2, POS_3, POS_4, POS_5, POS_6
}

data class SlotData(
    val front: Bitmap,
    val back: Bitmap
)