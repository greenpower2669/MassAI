package fr.massai.app

/** Guard against taking a cropped silhouette's lowest visible pixel as the floor. */
object SilhouetteFraming {
    fun isVerticallyClipped(top: Int, bottom: Int, frameHeight: Int): Boolean {
        if (frameHeight <= 2 || top < 0 || bottom < top || bottom >= frameHeight) return true
        val margin = (frameHeight / 80).coerceAtLeast(2)
        return top <= margin || bottom >= frameHeight - 1 - margin
    }
}
