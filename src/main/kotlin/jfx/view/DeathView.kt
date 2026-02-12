package top.e404.skin.jfx.view

import javafx.scene.image.Image
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import top.e404.skin.jfx.CanvasArgs
import top.e404.skin.jfx.SkinCanvas
import top.e404.skin.jfx.png
import top.e404.skin.jfx.runTask
import top.e404.skin.jfx.snapshot
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minecraft death animation.
 * 
 * The animation sequence:
 * 1. Character stands straight up (initial pose)
 * 2. Character falls to the right side (rotation around Z axis)
 * 3. Character disappears (fade out)
 * 
 * This mimics the classic Minecraft death animation where the player
 * falls over and fades away when they die.
 */
object DeathView : AbstractView<SkinCanvas>(400.0, 400.0, StackPane()) {

    private fun update(image: Image, slim: Boolean, light: Color?, head: Double) {
        pane.children.apply {
            clear()
            canvas = SkinCanvas(image, slim, imageWidth, imageHeight, light, head)
            add(canvas)
        }
    }

    /**
     * Generate the death animation frames.
     * 
     * @param args Canvas arguments including frameCount
     * @return List of PNG frames showing the death animation
     */
    fun getDeath(args: CanvasArgs): List<ByteArray> {
        val images = ArrayList<Image>()
        var t: Throwable? = null

        synchronized(lock) {
            runCatching {
                runTask {
                    val step = 360 / args.frameCount
                    val image = args.bytes.inputStream().use { Image(it) }
                    update(image, args.slim, args.light, args.head)

                    canvas.apply {
                        // Base positioning
                        translate.y += 10
                        translate.x -= 15
                        scale.x = 1.8
                        scale.y = 1.8

                        // Set rotation pivots to feet position
                        // The feet are at +(bodyInner.height + lLegInner.height) / 2.0
                        val feetY = 16.0 // body height + leg height
                        xRotate.pivotY = feetY
                        yRotate.pivotY = feetY
                        zRotate.pivotY = feetY

                        // Initial camera angles using x->y->z Euler sequence
                        val baseXRotate = 15.0
                        val baseYRotate = -20.0
                        val baseZRotate = 0.0

                        for (i in 0 until args.frameCount) {
                            val progress = i.toDouble() / (args.frameCount - 1)
                            
                            // Phase 1: Standing (0-30% of animation)
                            // Phase 2: Falling (30-80% of animation) 
                            // Phase 3: Disappearing (80-100% of animation)
                            
                            when {
                                progress < 0.3 -> {
                                    // Standing straight up
                                    xRotate.angle = baseXRotate
                                    yRotate.angle = baseYRotate
                                    zRotate.angle = baseZRotate
                                    translate.x = 0.0
                                    translate.y = 8.0
                                }
                                progress < 0.8 -> {
                                    // Falling to the right side
                                    val fallProgress = (progress - 0.3) / 0.5 // Normalize to 0-1
                                    val fallAngle = fallProgress * 90.0 // Fall 90 degrees
                                    
                                    xRotate.angle = baseXRotate
                                    yRotate.angle = baseYRotate
                                    zRotate.angle = baseZRotate + fallAngle // Fall to right
                                    
                                    // Add some horizontal movement as falling
                                    //translate.x = fallProgress * 20.0
                                    //translate.y = 8.0 + fallProgress * 15.0 // Lower as falling
                                }
                                else -> {
                                    scale.x = 0.0
                                    scale.y = 0.0
                                    scale.z = 0.0
                                }
                            }

                            images.add(snapshot(args.bg, pane))
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
