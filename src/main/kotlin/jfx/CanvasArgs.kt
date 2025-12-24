package top.e404.skin.jfx

import javafx.scene.paint.Color

data class CanvasArgs(
    val bytes: ByteArray,
    val slim: Boolean,
    val bg: Color,
    val frameCount: Int,
    val y: Int,
    val light: Color?,
    val head: Double
    ) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CanvasArgs

        if (slim != other.slim) return false
        if (frameCount != other.frameCount) return false
        if (y != other.y) return false
        if (head != other.head) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (bg != other.bg) return false
        if (light != other.light) return false

        return true
    }

    override fun hashCode(): Int {
        var result = slim.hashCode()
        result = 31 * result + frameCount
        result = 31 * result + y
        result = 31 * result + head.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + bg.hashCode()
        result = 31 * result + (light?.hashCode() ?: 0)
        return result
    }
}