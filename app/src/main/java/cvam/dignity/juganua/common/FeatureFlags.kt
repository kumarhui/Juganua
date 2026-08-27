package cvam.dignity.juganua.common

/**
 * Global flags to enable/disable features.
 */
object FeatureFlags {
    // Aadhaar
    const val IS_AADHAAR_SCAN_ENABLED = true
    const val IS_AADHAAR_STATUS_ENABLED = true
    const val IS_AADHAAR_DOWNLOAD_ENABLED = true
    const val IS_AADHAAR_VERIFY_ENABLED = true
    const val IS_AADHAAR_LOGIN_ENABLED = true

    // PDF
    const val IS_PDF_UNLOCKER_ENABLED = true
    const val IS_PDF_TO_IMAGE_ENABLED = true
    const val IS_PDF_EXTRACT_ID_ENABLED = true
    const val IS_PDF_MERGER_ENABLED = true
    const val IS_PDF_COMPRESSOR_ENABLED = true
    const val IS_PDF_SPLIT_ENABLED = true

    // Postal
    const val IS_IPPB_ARTICLE_SCAN_ENABLED = true
    const val IS_IPPB_RPLI_ENABLED = true
    const val IS_IPPB_AADHAAR_QR_ENABLED = true
    const val IS_IPPB_GENERATE_BARCODE_ENABLED = true
    const val IS_IPPB_POST_SLIP_ENABLED = true
    const val IS_IPPB_REGISTER_ENABLED = true
}