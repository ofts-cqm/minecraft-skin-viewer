package top.e404.skin.jfx.view

import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import top.e404.skiko.gif.gif
import top.e404.skin.jfx.SkinCanvas
import kotlin.math.PI

abstract class AbstractView<T> (val imageWidth: Double, val imageHeight: Double, protected val pane: Pane){
    protected val u = PI / 180
    protected val lock = Object()
    lateinit var stage: Stage

    protected lateinit var canvas: T & Any

    companion object{

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
    }

    open fun load() {
        stage = Stage().apply {
            scene = Scene(pane, imageWidth, imageHeight)
            isResizable = false
        }
    }
}