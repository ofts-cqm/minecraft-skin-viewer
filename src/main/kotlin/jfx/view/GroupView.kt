package top.e404.skin.jfx.view

import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import top.e404.skin.jfx.CanvasArgs
import top.e404.skin.jfx.SkinCanvas
import top.e404.skin.jfx.png
import top.e404.skin.jfx.runTask
import top.e404.skin.jfx.snapshot

class GroupView(imageHeight: Double, imageWidth: Double, val resourceName: String, val modelProvider: (SkinCanvas) -> Unit): AbstractView<SkinCanvas>(imageHeight, imageWidth, AnchorPane()) {
    companion object{
        val allGroupViews = arrayListOf<GroupView>()

        val homo = GroupView(500.0, 889.0, "homo.jpg") { canvas -> canvas.homo() }
        val trump = GroupView(1200.0, 800.0, "trump.jpg") { canvas -> trump(canvas) }
        val milano = GroupView(720.0, 1280.0, "milano.jpg") { canvas -> milano(canvas) }
        val temple = GroupView(720.0, 1280.0, "temple.jpg") { canvas -> temple(canvas) }
        val gWall = GroupView(800.0, 1449.0, "gWall.jpg") { canvas -> temple(canvas) }

        fun loadAll(){
            allGroupViews.forEach { it.load() }
        }

        fun temple(canvas: SkinCanvas){
            canvas.apply {
                xRotate.angle = -10.0
                yRotate.angle = .0
                zRotate.angle = .0
                translate.y -= 30
                scale.apply {
                    x = 2.5
                    y = 2.5
                }
            }
        }

        fun milano(canvas: SkinCanvas){
            canvas.apply {
                xRotate.angle = -5.0
                yRotate.angle = -30.0
                zRotate.angle = .0
                translate.y -= 20
                scale.apply {
                    x = 2.5
                    y = 2.5
                }
            }
        }

        fun trump(canvas: SkinCanvas){
            canvas.apply {
                xRotate.angle = .0
                yRotate.angle = -30.0
                zRotate.angle = .0
                translate.x = -7.0
                translate.y = -2.0
                scale.apply {
                    x = 0.9
                    y = 0.9
                }
            }
        }
    }

    init{
        allGroupViews.add(this)
    }

    override fun load() {
        val bgImage = this::class.java
            .classLoader
            .getResourceAsStream(resourceName)
            .use { Image(it) }
        val bg = ImageView(bgImage)
        pane.children.add(0, bg)
        super.load()
    }

    private fun update(image: Image, slim: Boolean, light: Color?, head: Double) {
        pane.children.apply {
            if (::canvas.isInitialized) remove(canvas)
            canvas = SkinCanvas(image, slim, imageWidth, imageHeight, light, head).apply(modelProvider)
            add(canvas)
        }
    }

    fun getGroupPhoto(args: CanvasArgs): ByteArray {
        var snapshot: Image? = null
        var t: Throwable? = null
        synchronized(lock) {
            runCatching {
                runTask {
                    args.bytes.inputStream().use {
                        update(Image(it), args.slim, args.light, args.head)
                    }
                    snapshot = snapshot(Color.web("#00000000"), pane)
                }
            }.onFailure {
                t = it
            }
        }
        t?.let { throw it }
        return snapshot!!.png()
    }
}