package top.e404.skin.jfx.view

import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import top.e404.skiko.gif.gif
import kotlin.math.PI

fun asGIF(frames:List<ByteArray>, width:Int, height:Int, d:Int): ByteArray{
    return gif(width, height) {
        options {
            disposalMethod = AnimationDisposalMode.RESTORE_BG_COLOR
            alphaType = ColorAlphaType.OPAQUE
        }
        frames.forEach {
            frame(Bitmap.makeFromImage(org.jetbrains.skia.Image.makeFromEncoded(it))) { duration = d }
        }
    }.bytes
}

abstract class AbstractView<T> (val imageWidth: Double, val imageHeight: Double){
    protected val u = PI / 180
    protected val lock = Object()
    protected val pane = StackPane()
    lateinit var stage: Stage

    protected lateinit var canvas: T & Any

    fun load() {
        stage = Stage().apply {
            scene = Scene(pane, imageWidth, imageHeight)
            isResizable = false
        }
    }
}