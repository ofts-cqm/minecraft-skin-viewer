package top.e404.skin.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import top.e404.skiko.draw.render3d.*
import top.e404.skiko.frame.Frame
import top.e404.skiko.frame.encodeToBytes
import kotlin.math.cos
import kotlin.math.sin

/**
 * 封装所有可能的变换操作
 */
sealed class Transformation {
    /**
     * 旋转操作
     * @param x 绕X轴旋转的角度
     * @param y 绕Y轴旋转的角度
     * @param z 绕Z轴旋转的角度
     */
    data class Rotate(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) : Transformation()

    /**
     * 缩放操作
     * @param x X轴的缩放比例
     * @param y Y轴的缩放比例
     * @param z Z轴的缩放比例
     */
    data class Scale(val x: Float = 1f, val y: Float = 1f, val z: Float = 1f) : Transformation()

    /**
     * 平移操作
     * @param x X轴的平移量
     * @param y Y轴的平移量
     * @param z Z轴的平移量
     */
    data class Translate(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) : Transformation()
}

object PosePresets {
    val WALKING = mapOf(
        BodyPart.RIGHT_ARM to listOf(Transformation.Rotate(x = -30f)),
        BodyPart.LEFT_ARM to listOf(Transformation.Rotate(x = 30f)),
        BodyPart.RIGHT_LEG to listOf(Transformation.Rotate(x = 30f)),
        BodyPart.LEFT_LEG to listOf(Transformation.Rotate(x = -30f)),
    )
    val SIT = mapOf(
        BodyPart.RIGHT_ARM to listOf(Transformation.Rotate(x = -30f)),
        BodyPart.LEFT_ARM to listOf(Transformation.Rotate(x = -30f)),
        BodyPart.RIGHT_LEG to listOf(Transformation.Rotate(x = -80f, z = -20f)),
        BodyPart.LEFT_LEG to listOf(Transformation.Rotate(x = -80f, z = 20f)),
    )
    val HOMO = mapOf(
        BodyPart.HEAD to listOf(Transformation.Rotate(y = -30f)),
        BodyPart.RIGHT_ARM to listOf(Transformation.Rotate(x = -30f)),
        BodyPart.LEFT_ARM to listOf(Transformation.Rotate(x = -30f)),
        BodyPart.RIGHT_LEG to listOf(Transformation.Rotate(x = -80f, z = -20f)),
        BodyPart.LEFT_LEG to listOf(Transformation.Rotate(x = -80f, z = 20f)),
    )
    fun withScale(headScale: Float = 1f, laScale: Float = 1f, raScale: Float = 1f, llScale: Float = 1f, rlScale: Float = 1f, ) = mapOf(
        BodyPart.HEAD to listOf(
            Transformation.Rotate(y = -30f),
            Transformation.Scale(headScale, headScale, headScale),
            Transformation.Translate(y = BodyPart.HEAD.getDims(false).y.let { it * (headScale - 1) / 2 }),
        ),
        BodyPart.RIGHT_ARM to listOf(
            Transformation.Rotate(x = -30f),
            Transformation.Scale(raScale, raScale, raScale),
            Transformation.Translate(x = -BodyPart.LEFT_ARM.getDims(false).x.let { it * (raScale - 1) / 2 }),
        ),
        BodyPart.LEFT_ARM to listOf(
            Transformation.Rotate(x = -30f),
            Transformation.Scale(laScale, laScale, laScale),
            Transformation.Translate(x = BodyPart.LEFT_ARM.getDims(false).x.let { it * (laScale - 1) / 2 }),
        ),
        BodyPart.RIGHT_LEG to listOf(
            Transformation.Rotate(x = -80f, z = -20f),
            Transformation.Scale(rlScale, rlScale, rlScale),
            Transformation.Translate(y = -BodyPart.LEFT_LEG.getDims(false).y.let { it * (rlScale - 1) / 2 }),
        ),
        BodyPart.LEFT_LEG to listOf(
            Transformation.Rotate(x = -80f, z = 20f),
            Transformation.Scale(llScale, llScale, llScale),
            Transformation.Translate(y = -BodyPart.LEFT_LEG.getDims(false).y.let { it * (llScale - 1) / 2 }),
        ),
    )
}

// region 扩展函数
private fun Vec3.rotateX(angle: Float): Vec3 {
    val cos = cos(angle)
    val sin = sin(angle)
    return Vec3(x, y * cos - z * sin, y * sin + z * cos)
}

private fun Vec3.rotateY(angle: Float): Vec3 {
    val cos = cos(angle)
    val sin = sin(angle)
    return Vec3(x * cos + z * sin, y, -x * sin + z * cos)
}

private fun Vec3.rotateZ(angle: Float): Vec3 {
    val cos = cos(angle)
    val sin = sin(angle)
    return Vec3(x * cos - y * sin, x * sin + y * cos, z)
}

/**
 * 按照 Z -> Y -> X 的顺序旋转向量
 */
private fun Vec3.rotate(rotation: Transformation.Rotate): Vec3 {
    val radX = Math.toRadians(rotation.x.toDouble()).toFloat()
    val radY = Math.toRadians(rotation.y.toDouble()).toFloat()
    val radZ = Math.toRadians(rotation.z.toDouble()).toFloat()
    return this.rotateZ(radZ).rotateY(radY).rotateX(radX)
}

private fun Vec3.scale(scaling: Transformation.Scale): Vec3 {
    return Vec3(x * scaling.x, y * scaling.y, z * scaling.z)
}

private fun Vec3.translate(translation: Transformation.Translate): Vec3 {
    return this.plus(Vec3(translation.x, translation.y, translation.z))
}

/**
 * 将Minecraft模型所有复杂的、硬编码的数据（尺寸、位置、UV坐标）封装起来
 *
 * 实现了数据与逻辑的完全分离，极大地提高了代码的可读性、可维护性和可扩展性
 */
enum class BodyPart {
    HEAD {
        override fun getDims(isSlim: Boolean) = Vec3(8f, 8f, 8f)
        override fun getPos(isSlim: Boolean) = Vec3(0f, 20f, 0f)
        override fun getBaseUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(16f, 8f, 8f, 8f),
            FaceDirection.LEFT to Rect.makeXYWH(0f, 8f, 8f, 8f),
            FaceDirection.TOP to Rect.makeXYWH(8f, 0f, 8f, 8f),
            FaceDirection.BOTTOM to Rect.makeXYWH(16f, 0f, 8f, 8f),
            FaceDirection.FRONT to Rect.makeXYWH(8f, 8f, 8f, 8f),
            FaceDirection.BACK to Rect.makeXYWH(24f, 8f, 8f, 8f)
        )

        override fun getOverlayUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(48f, 8f, 8f, 8f),
            FaceDirection.LEFT to Rect.makeXYWH(32f, 8f, 8f, 8f),
            FaceDirection.TOP to Rect.makeXYWH(40f, 0f, 8f, 8f),
            FaceDirection.BOTTOM to Rect.makeXYWH(48f, 0f, 8f, 8f),
            FaceDirection.FRONT to Rect.makeXYWH(40f, 8f, 8f, 8f),
            FaceDirection.BACK to Rect.makeXYWH(56f, 8f, 8f, 8f)
        )

        override fun getPivot(isSlim: Boolean) = Vec3(0f, 0f, 0f)
    },
    BODY {
        override fun getDims(isSlim: Boolean) = Vec3(8f, 12f, 4f)
        override fun getPos(isSlim: Boolean) = Vec3(0f, 10f, 0f)
        override fun getBaseUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(16f, 20f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(28f, 20f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(20f, 16f, 8f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(28f, 16f, 8f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(20f, 20f, 8f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(32f, 20f, 8f, 12f)
        )

        override fun getOverlayUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(16f, 36f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(28f, 36f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(20f, 32f, 8f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(28f, 32f, 8f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(20f, 36f, 8f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(32f, 36f, 8f, 12f)
        )

        override fun getPivot(isSlim: Boolean) = Vec3(0f, 0f, 0f)
    },
    RIGHT_ARM {
        override fun getDims(isSlim: Boolean) = if (isSlim) Vec3(3f, 12f, 4f) else Vec3(4f, 12f, 4f)
        override fun getPos(isSlim: Boolean) = if (isSlim) Vec3(-5.5f, 10f, 0f) else Vec3(-6f, 10f, 0f)
        override fun getBaseUVs(isSlim: Boolean) = if (isSlim) mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(40f, 20f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(47f, 20f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(44f, 16f, 3f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(47f, 16f, 3f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(44f, 20f, 3f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(51f, 20f, 3f, 12f)
        ) else mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(40f, 20f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(48f, 20f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(44f, 16f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(48f, 16f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(44f, 20f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(52f, 20f, 4f, 12f)
        )

        override fun getOverlayUVs(isSlim: Boolean) = if (isSlim) mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(40f, 36f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(47f, 36f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(44f, 32f, 3f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(47f, 32f, 3f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(44f, 36f, 3f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(51f, 36f, 3f, 12f)
        ) else mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(40f, 36f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(48f, 36f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(44f, 32f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(48f, 32f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(44f, 36f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(52f, 36f, 4f, 12f)
        )

        override fun getPivot(isSlim: Boolean) = Vec3(0f, 6f, 0f)
    },
    LEFT_ARM {
        override fun getDims(isSlim: Boolean) = if (isSlim) Vec3(3f, 12f, 4f) else Vec3(4f, 12f, 4f)
        override fun getPos(isSlim: Boolean) = if (isSlim) Vec3(5.5f, 10f, 0f) else Vec3(6f, 10f, 0f)
        override fun getBaseUVs(isSlim: Boolean) = if (isSlim) mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(39f, 52f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(32f, 52f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(36f, 48f, 3f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(39f, 48f, 3f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(36f, 52f, 3f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(43f, 52f, 3f, 12f)
        ) else mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(40f, 52f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(32f, 52f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(36f, 48f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(40f, 48f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(36f, 52f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(44f, 52f, 4f, 12f)
        )

        override fun getOverlayUVs(isSlim: Boolean) = if (isSlim) mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(55f, 52f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(48f, 52f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(52f, 48f, 3f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(55f, 48f, 3f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(52f, 52f, 3f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(59f, 52f, 3f, 12f)
        ) else mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(56f, 52f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(48f, 52f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(52f, 48f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(56f, 48f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(52f, 52f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(60f, 52f, 4f, 12f)
        )

        override fun getPivot(isSlim: Boolean) = Vec3(0f, 6f, 0f)
    },
    RIGHT_LEG {
        override fun getDims(isSlim: Boolean) = Vec3(4f, 12f, 4f)
        override fun getPos(isSlim: Boolean) = Vec3(-2f, -2f, 0f)
        override fun getBaseUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(0f, 20f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(8f, 20f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(4f, 16f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(8f, 16f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(4f, 20f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(12f, 20f, 4f, 12f)
        )

        override fun getOverlayUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(0f, 36f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(8f, 36f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(4f, 32f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(8f, 32f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(4f, 36f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(12f, 36f, 4f, 12f)
        )

        override fun getPivot(isSlim: Boolean) = Vec3(0f, 6f, 0f)
    },
    LEFT_LEG {
        override fun getDims(isSlim: Boolean) = Vec3(4f, 12f, 4f)
        override fun getPos(isSlim: Boolean) = Vec3(2f, -2f, 0f)
        override fun getBaseUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(24f, 52f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(16f, 52f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(20f, 48f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(24f, 48f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(20f, 52f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(28f, 52f, 4f, 12f)
        )

        override fun getOverlayUVs(isSlim: Boolean) = mapOf(
            FaceDirection.RIGHT to Rect.makeXYWH(8f, 52f, 4f, 12f),
            FaceDirection.LEFT to Rect.makeXYWH(0f, 52f, 4f, 12f),
            FaceDirection.TOP to Rect.makeXYWH(4f, 48f, 4f, 4f),
            FaceDirection.BOTTOM to Rect.makeXYWH(8f, 48f, 4f, 4f),
            FaceDirection.FRONT to Rect.makeXYWH(4f, 52f, 4f, 12f),
            FaceDirection.BACK to Rect.makeXYWH(12f, 52f, 4f, 12f)
        )

        override fun getPivot(isSlim: Boolean) = Vec3(0f, 6f, 0f)
    };

    /** 每个身体部件都必须提供自己的尺寸、位置和UV数据 */
    abstract fun getDims(isSlim: Boolean): Vec3
    /** 获取身体部件相对于模型中心点的位置 */
    abstract fun getPos(isSlim: Boolean): Vec3
    /** 获取身体部件的基础层UV坐标 */
    abstract fun getBaseUVs(isSlim: Boolean): Map<FaceDirection, Rect>
    /** 获取身体部件的外层UV坐标，若无外层则返回null */
    abstract fun getOverlayUVs(isSlim: Boolean): Map<FaceDirection, Rect>?

    /**
     * 获取身体部件的旋转轴心点（局部坐标系，即相对于部件几何中心）
     */
    abstract fun getPivot(isSlim: Boolean): Vec3
}

/**
 * 根据皮肤贴图和模型类型（标准/Slim）创建完整的Minecraft玩家模型。
 * @param pose 一个映射，定义了特定身体部件的一系列有序变换
 */
internal fun createMinecraftPlayer(
    skin: Bitmap,
    isSlim: Boolean,
    pose: Map<BodyPart, List<Transformation>> = emptyMap()
): Mesh {
    val texW = skin.width.toFloat()
    val texH = skin.height.toFloat()
    val componentMeshes = mutableListOf<Mesh>()
    val overlayAmount = 0.5f
    val headOverlayAmount = 1f

    for (part in BodyPart.entries) {
        val dims = part.getDims(isSlim)
        val pos = part.getPos(isSlim)
        val baseUVs = part.getBaseUVs(isSlim)
        val overlayUVs = part.getOverlayUVs(isSlim)
        val pivot = part.getPivot(isSlim)
        val transformations = pose[part]

        // 定义统一的变换函数
        val transform: (Vec3) -> Vec3 = { vertexPos ->
            if (transformations.isNullOrEmpty()) {
                vertexPos // 如果没有变换，直接返回原坐标
            } else {
                // 使用 fold 依次应用变换链中的每一个操作
                transformations.fold(vertexPos) { currentPos, transformation ->
                    when (transformation) {
                        // 旋转和缩放是围绕轴心点进行的
                        is Transformation.Rotate -> (currentPos - pivot).rotate(transformation) + pivot
                        is Transformation.Scale -> (currentPos - pivot).scale(transformation) + pivot
                        // 平移是绝对的
                        is Transformation.Translate -> currentPos.translate(transformation)
                    }
                }
            }
        }

        // 创建并添加基础层
        val baseCuboid = createUVCuboid(dims, baseUVs, texW, texH)
        componentMeshes.add(Mesh(baseCuboid.vertices.map {
            Vertex(transform(it.position) + pos, it.uv)
        }, baseCuboid.faces))

        // 创建并添加外层
        overlayUVs?.let { uvs ->
            val overlaySize = if (part == BodyPart.HEAD) headOverlayAmount else overlayAmount
            val overlayCuboid = createUVCuboid(dims + Vec3(overlaySize, overlaySize, overlaySize), uvs, texW, texH)
            componentMeshes.add(Mesh(overlayCuboid.vertices.map {
                Vertex(transform(it.position) + pos, it.uv)
            }, overlayCuboid.faces))
        }
    }
    return combineMeshes(componentMeshes, skin)
}

/**
 * 渲染Minecraft皮肤为图像的函数
 *
 * @param skin 皮肤图片
 * @param isSlim 是否为Slim模型
 * @param backgroundColor 背景颜色
 * @param camera 相机参数
 * @param pose 一个映射，定义了特定身体部件的一系列有序变换
 */
fun renderMinecraftView(
    skin: Image,
    isSlim: Boolean,
    width: Int,
    height: Int,
    backgroundColor: Int,
    camera: OrbitCamera,
    pose: Map<BodyPart, List<Transformation>> = emptyMap()
): Image {
    val skinBitmap = Bitmap.makeFromImage(skin)
    val playerMesh = createMinecraftPlayer(skinBitmap, isSlim, pose)
    val (viewMatrix, eyePosition) = createViewMatrix(camera)
    val cameraForward = (camera.target - eyePosition).normalized()
    return renderToImage(
        playerMesh, width, height, viewMatrix, cameraForward, camera.distance,
        true, true, backgroundColor, useBackFaceCulling = false
    )
}

/**
 * 渲染旋转动画的函数
 * @param skin 皮肤图片
 * @param isSlim 是否为Slim模型
 * @param width 画布宽度
 * @param height 画布高度
 * @param backgroundColor 背景颜色
 * @param camera 相机参数
 * @param frameCount 帧数
 * @param rotations 新增：一个映射，定义了特定身体部件的旋转，模型将以这个姿势进行旋转
 * @param pose 一个映射，定义了特定身体部件的一系列有序变换，模型将以这个姿势进行旋转
 */
suspend fun renderRotate(
    skin: Image,
    isSlim: Boolean,
    width: Int,
    height: Int,
    backgroundColor: Int,
    camera: OrbitCamera,
    frameCount: Int,
    frameDuration: Int,
    pose: Map<BodyPart, List<Transformation>> = emptyMap()
): ByteArray {
    val unitAngel = 360f / frameCount
    val skinBitmap = Bitmap.makeFromImage(skin)
    val playerMesh = createMinecraftPlayer(skinBitmap, isSlim, pose)
    return coroutineScope {
        withContext(Dispatchers.Default) {
            (0 until frameCount).map { i ->
                async {
                    val angle = i * unitAngel
                    val rotatedCamera = camera.copy(azimuthDegrees = camera.azimuthDegrees + angle)
                    val (viewMatrix, eyePosition) = createViewMatrix(rotatedCamera)
                    val cameraForward = (rotatedCamera.target - eyePosition).normalized()
                    renderToImage(
                        playerMesh, width, height, viewMatrix, cameraForward, rotatedCamera.distance,
                        true, true, backgroundColor, useBackFaceCulling = false
                    ).let { Frame(frameDuration, it) }
                }
            }.awaitAll()
        }.encodeToBytes()
    }
}