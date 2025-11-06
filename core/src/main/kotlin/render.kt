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
import top.e404.skiko.gif.GIFBuilder
import top.e404.skiko.gif.gif
import kotlin.math.cos
import kotlin.math.sin

/**
 * 新增：用于表示身体部件在X, Y, Z轴上的旋转角度（单位：度）
 */
data class PartRotation(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

object PosePresets {
    val WALKING = mapOf(
        BodyPart.RIGHT_ARM to PartRotation(x = -30f, z = 0f),
        BodyPart.LEFT_ARM to PartRotation(x = 30f, z = 0f),
        BodyPart.RIGHT_LEG to PartRotation(x = 30f, z = 0f),
        BodyPart.LEFT_LEG to PartRotation(x = -30f, z = 0f),
    )
    val SIT = mapOf(
        BodyPart.RIGHT_ARM to PartRotation(x = -30f, z = 0f),
        BodyPart.LEFT_ARM to PartRotation(x = -30f, z = 0f),
        BodyPart.RIGHT_LEG to PartRotation(x = -80f, z = -20f),
        BodyPart.LEFT_LEG to PartRotation(x = -80f, z = 20f),
    )
    val HOMO = mapOf(
        BodyPart.HEAD to PartRotation(y = -30f),
        BodyPart.RIGHT_ARM to PartRotation(x = -30f, z = 0f),
        BodyPart.LEFT_ARM to PartRotation(x = -30f, z = 0f),
        BodyPart.RIGHT_LEG to PartRotation(x = -80f, z = -20f),
        BodyPart.LEFT_LEG to PartRotation(x = -80f, z = 20f),
    )
}

// region 新增：为Vec3添加旋转的扩展函数
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
private fun Vec3.rotate(rotation: PartRotation): Vec3 {
    val radX = Math.toRadians(rotation.x.toDouble()).toFloat()
    val radY = Math.toRadians(rotation.y.toDouble()).toFloat()
    val radZ = Math.toRadians(rotation.z.toDouble()).toFloat()
    return this.rotateZ(radZ).rotateY(radY).rotateX(radX)
}
// endregion

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
     * 新增：获取身体部件的旋转轴心点（局部坐标系，即相对于部件几何中心）
     */
    abstract fun getPivot(isSlim: Boolean): Vec3
}

/**
 * 根据皮肤贴图和模型类型（标准/Slim）创建完整的Minecraft玩家模型。
 * @param rotations 一个映射，定义了特定身体部件的旋转
 */
internal fun createMinecraftPlayer(
    skin: Bitmap,
    isSlim: Boolean,
    rotations: Map<BodyPart, PartRotation> = emptyMap()
): Mesh {
    val texW = skin.width.toFloat()
    val texH = skin.height.toFloat()
    val componentMeshes = mutableListOf<Mesh>()
    // 外层皮肤的膨胀量
    val overlayAmount = 0.5f
    val headOverlayAmount = 1f // 头部外层膨胀量稍大

    // 遍历所有身体部件
    for (part in BodyPart.entries) {
        val dims = part.getDims(isSlim)
        val pos = part.getPos(isSlim)
        val baseUVs = part.getBaseUVs(isSlim)
        val overlayUVs = part.getOverlayUVs(isSlim)
        val rotation = rotations[part]
        val pivot = part.getPivot(isSlim)

        // 定义一个统一的变换函数，用于处理旋转
        val transform: (Vec3) -> Vec3 = { vertexPos ->
            if (rotation != null) {
                // 旋转逻辑：1.移动到轴心点 2.旋转 3.移回原位
                (vertexPos - pivot).rotate(rotation) + pivot
            } else {
                vertexPos
            }
        }

        // 创建并添加基础层
        val baseCuboid = createUVCuboid(dims, baseUVs, texW, texH)
        componentMeshes.add(Mesh(baseCuboid.vertices.map {
            Vertex(transform(it.position) + pos, it.uv)
        }, baseCuboid.faces))

        // 如果有外层定义，则创建并添加外层
        overlayUVs?.let { uvs ->
            val overlaySize = if (part == BodyPart.HEAD) headOverlayAmount else overlayAmount
            // 外层的尺寸比基础层稍大
            val overlayCuboid = createUVCuboid(dims + Vec3(overlaySize, overlaySize, overlaySize), uvs, texW, texH)
            componentMeshes.add(
                Mesh(
                    overlayCuboid.vertices.map {
                        Vertex(transform(it.position) + pos, it.uv)
                    },
                    overlayCuboid.faces
                )
            )
        }
    }
    // 将所有部件的Mesh合并成一个，并附上皮肤纹理
    return combineMeshes(componentMeshes, skin)
}

/**
 * 渲染Minecraft皮肤为图像的函数
 *
 * @param skin 皮肤图片
 * @param isSlim 是否为Slim模型
 * @param backgroundColor 背景颜色
 * @param camera 相机参数
 * @param rotations 新增：一个映射，定义了特定身体部件的旋转
 * @return 渲染后的图像
 */
fun renderMinecraftView(
    skin: Image,
    isSlim: Boolean,
    width: Int,
    height: Int,
    backgroundColor: Int,
    camera: OrbitCamera,
    rotations: Map<BodyPart, PartRotation> = emptyMap()
): Image {
    val skinBitmap = Bitmap.makeFromImage(skin)
    val playerMesh = createMinecraftPlayer(skinBitmap, isSlim, rotations)
    val (viewMatrix, eyePosition) = createViewMatrix(camera)
    val cameraForward = (camera.target - eyePosition).normalized()
    return renderToImage(
        playerMesh,
        width,
        height,
        viewMatrix,
        cameraForward,
        camera.distance,
        true,
        true,
        backgroundColor,
        useBackFaceCulling = false
    )
}

/**
 * 渲染旋转动画的函数
 *
 * @param skin 皮肤图片
 * @param isSlim 是否为Slim模型
 * @param width 画布宽度
 * @param height 画布高度
 * @param backgroundColor 背景颜色
 * @param camera 相机参数
 * @param frameCount 帧数
 * @param rotations 新增：一个映射，定义了特定身体部件的旋转，模型将以这个姿势进行旋转
 * @return 渲染后的图像二进制数据
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
    rotations: Map<BodyPart, PartRotation> = emptyMap()
): ByteArray {
    val unitAngel = 360f / frameCount
    val skinBitmap = Bitmap.makeFromImage(skin)
    val playerMesh = createMinecraftPlayer(skinBitmap, isSlim, rotations)
    return coroutineScope {
        // 绘图是cpu密集型操作
        withContext(Dispatchers.Default) {
            (0 until frameCount).map { i ->
                async {
                    val angle = i * unitAngel
                    val rotatedCamera = camera.copy(azimuthDegrees = camera.azimuthDegrees + angle)
                    val (viewMatrix, eyePosition) = createViewMatrix(rotatedCamera)
                    val cameraForward = (rotatedCamera.target - eyePosition).normalized()
                    renderToImage(
                        playerMesh,
                        width,
                        height,
                        viewMatrix,
                        cameraForward,
                        rotatedCamera.distance,
                        true,
                        true,
                        backgroundColor,
                        useBackFaceCulling = false
                    ).let {
                        Frame(frameDuration, it)
                    }
                }
            }.awaitAll()
        }.encodeToBytes() // 编码是IO密集
    }
}