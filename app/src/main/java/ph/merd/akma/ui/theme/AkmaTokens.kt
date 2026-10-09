package ph.merd.akma.ui.theme

/**
 * Raw ARGB tokens from the AKMA Figma file (DYiiO7JdJsIMjyDH2Cc4ZF).
 * Plain Kotlin so JVM tests can compare them with res/values/colors.xml.
 */
object AkmaTokens {
    const val TEXT_PRIMARY = 0xFF17121FL
    const val TEXT_SECONDARY = 0xFF6B6478L
    const val TEXT_BRAND_STRONG = 0xFF6B21A8L
    const val TEXT_ON_BRAND = 0xFFFFFFFFL
    const val BG_APP = 0xFFF9F8FBL
    const val BG_SURFACE = 0xFFFFFFFFL
    const val BG_SUBTLE = 0xFFF2F0F5L
    const val BG_MUTED = 0xFFE4E0EAL
    const val BG_BRAND = 0xFFA855F7L
    const val BG_BRAND_STRONG = 0xFF7E22CEL
    const val BG_BRAND_SUBTLE = 0xFFFAF5FFL
    const val BG_BRAND_MUTED = 0xFFF3E8FFL
    const val BG_SUCCESS = 0xFF15803DL
    const val BG_SUCCESS_SUBTLE = 0xFFDCFCE7L
    const val TEXT_SUCCESS = 0xFF15803DL
    const val BG_SCRIM = 0x8017121FL
    const val BORDER_DEFAULT = 0xFFE4E0EAL
    const val BORDER_STRONG = 0xFFCBC5D4L

    /** XML resource name to ARGB value; every entry must exist in colors.xml. */
    val colorResources: Map<String, Long> = mapOf(
        "akma_text_primary" to TEXT_PRIMARY,
        "akma_text_secondary" to TEXT_SECONDARY,
        "akma_text_brand_strong" to TEXT_BRAND_STRONG,
        "akma_text_on_brand" to TEXT_ON_BRAND,
        "akma_bg_app" to BG_APP,
        "akma_bg_surface" to BG_SURFACE,
        "akma_bg_subtle" to BG_SUBTLE,
        "akma_bg_muted" to BG_MUTED,
        "akma_bg_brand" to BG_BRAND,
        "akma_bg_brand_strong" to BG_BRAND_STRONG,
        "akma_bg_brand_subtle" to BG_BRAND_SUBTLE,
        "akma_bg_brand_muted" to BG_BRAND_MUTED,
        "akma_bg_success" to BG_SUCCESS,
        "akma_bg_success_subtle" to BG_SUCCESS_SUBTLE,
        "akma_text_success" to TEXT_SUCCESS,
        "akma_bg_scrim" to BG_SCRIM,
        "akma_border_default" to BORDER_DEFAULT,
        "akma_border_strong" to BORDER_STRONG,
    )
}
