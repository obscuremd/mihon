package eu.kanade.domain.ui.model

/**
 * How large the whole interface is drawn, as a multiple of the system's size: type, icons, spacing and touch
 * targets together.
 */
enum class DisplaySize(val scale: Float, val label: String) {
    XS(0.8f, "XS"),
    S(0.9f, "S"),
    M(1f, "M"),
    L(1.1f, "L"),
    XL(1.2f, "XL"),
}
