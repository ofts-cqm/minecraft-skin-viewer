package top.e404.skin.jfx.view

import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.scene.transform.Rotate
import javafx.scene.transform.Translate
import top.e404.skin.jfx.CanvasArgs
import top.e404.skin.jfx.SkinCanvas
import top.e404.skin.jfx.png
import top.e404.skin.jfx.runTask
import top.e404.skin.jfx.snapshot
import kotlin.math.sin

object HipView: AbstractView<SkinCanvas>(600.0, 900.0) {
    private fun update(image: Image, slim: Boolean, light: Color?, head: Double) {
        pane.children.apply {
            clear()
            canvas = SkinCanvas(image, slim, imageWidth, imageHeight, light, head)
            add(canvas)
        }
    }

    fun getHipShake(arg: CanvasArgs): List<ByteArray> {
        val images = ArrayList<Image>()
        var t: Throwable? = null
        synchronized(lock) {
            runCatching {
                runTask {
                    val step = 360 / arg.frameCount
                    val image = arg.bytes.inputStream().use {
                        Image(it)
                    }
                    update(image, arg.slim, arg.light, arg.head)

                    var left = Rotate(0.0, Rotate.Z_AXIS)
                    var right = Rotate(0.0, Rotate.Z_AXIS)
                    var bodyShift = Translate.translate(0.0, 0.0)

                    val r = if (step > 0) 1 else -1
                    canvas.apply {
                        canvas.sneak()
                        canvas.hipWave(left, right, bodyShift)

                        for (i in 0 until 360 step step * r) {
                            val rot = sin(i.toDouble() * r * u)
                            zRotate.angle = rot * 5
                            bodyShift.x = -rot
                            left.angle = -rot * 7
                            right.angle = -rot * 7
                            images.add(snapshot(arg.bg, pane))
                        }
                    }
                }
            }.onFailure {
                t = it
            }
        }
        t?.let { throw it }
        return images.map { it.png() }
    }
}
