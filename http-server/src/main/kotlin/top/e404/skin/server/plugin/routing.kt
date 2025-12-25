package top.e404.skin.server.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import javafx.scene.paint.Color
import org.jetbrains.skia.*
import org.slf4j.LoggerFactory
import top.e404.skiko.gif.gif
import top.e404.skin.jfx.CanvasArgs
import top.e404.skin.jfx.view.HeadView
import top.e404.skin.jfx.view.HipView
import top.e404.skin.jfx.view.SkinView
import top.e404.skin.jfx.view.SneakView
import top.e404.skin.jfx.view.AbstractView.Companion.asGIF
import top.e404.skin.jfx.view.GroupView
import top.e404.skin.server.ConfigManager
import top.e404.skin.server.RateLimiter
import top.e404.skin.server.Skin

private val defaultBgColor = Color.web("#1F1B1D")
private val logger = LoggerFactory.getLogger("Router")!!
private val rateLimiter = RateLimiter(ConfigManager.config.minInterval, ConfigManager.config.cleanEntry, ConfigManager.config.cleanCutoff)

fun Application.routing() = routing {
    get("/render/{type}/{content}/{position}") {
        val data = when (call.parameters["type"]!!.lowercase()) {
            "id" -> Skin.getById(call.parameters["content"]!!)
            "name" -> Skin.getByName(call.parameters["content"]!!)
            else -> {
                call.respond(HttpStatusCode.BadRequest, "type must be 'id' or 'name'")
                return@get
            }
        }
        if (data == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val skinBytes = data.skinBytes

        val parameters = call.request.queryParameters
        val bg = parameters["bg"]?.runCatching { Color.web(this) }?.getOrNull() ?: defaultBgColor
        val light = parameters["light"]?.let { Color.web(it) }
        val slim = parameters["t"]?.toBoolean() ?: data.slim
        val frameCount = parameters["x"]?.toIntOrNull() ?: 20
        val pitch = parameters["y"]?.toIntOrNull() ?: 20
        val head = parameters["head"]?.toDoubleOrNull() ?: 1.0
        val d = (parameters["duration"]?.toIntOrNull() ?: 40).coerceAtLeast(20)

        val args = CanvasArgs(skinBytes, slim, bg, frameCount, pitch, light, head)

        when (call.parameters["position"]!!.lowercase()) {
            "sneak" -> {

                val (first, second) = SneakView.getSneak(
                    skinBytes,
                    slim,
                    bg,
                    light,
                    parameters["head"]?.toDoubleOrNull() ?: 1.0
                )
                val d = parameters["duration"]?.toIntOrNull() ?: 40
                val bytes = gif(600, 900) {
                    options {
                        disposalMethod = AnimationDisposalMode.RESTORE_BG_COLOR
                        alphaType = ColorAlphaType.OPAQUE
                    }
                    frame(Bitmap.makeFromImage(Image.makeFromEncoded(first))) { duration = d }
                    frame(Bitmap.makeFromImage(Image.makeFromEncoded(second))) { duration = d }
                }.bytes
                call.respondBytes(bytes, ContentType.Image.GIF)
            }

            "sk" -> {
                val slim = parameters["t"]?.toBoolean() ?: data.slim
                val bytes = SkinView.getSkin(
                    skinBytes,
                    slim,
                    bg,
                    light,
                    parameters["head"]?.toDoubleOrNull() ?: 1.0
                )
                call.respondBytes(bytes, ContentType.Image.PNG)
            }

            "dsk" -> {
                val slim = parameters["t"]?.toBoolean() ?: data.slim
                val bytes = SkinView.getSkinRotate(
                    skinBytes,
                    slim,
                    bg,
                    parameters["x"]?.toIntOrNull() ?: 20,
                    parameters["y"]?.toIntOrNull() ?: 20,
                    light,
                    parameters["head"]?.toDoubleOrNull() ?: 1.0
                ).let { frames ->
                    val d = parameters["duration"]?.toIntOrNull() ?: 40
                    gif(600, 900) {
                        options {
                            disposalMethod = AnimationDisposalMode.RESTORE_BG_COLOR
                            alphaType = ColorAlphaType.OPAQUE
                        }
                        frames.forEach {
                            frame(Bitmap.makeFromImage(Image.makeFromEncoded(it))) { duration = d }
                        }
                    }.bytes
                }
                call.respondBytes(bytes, ContentType.Image.GIF)
            }

            "head" -> {
                val bytes = HeadView.getHead(skinBytes, bg, light)
                call.respondBytes(bytes, ContentType.Image.PNG)
            }

            "dhead" -> {
                val bytes = HeadView.getHeadRotate(
                    skinBytes,
                    bg,
                    parameters["x"]?.toIntOrNull() ?: 20,
                    parameters["y"]?.toIntOrNull() ?: 20,
                    light
                ).let { frames ->
                    val d = parameters["duration"]?.toIntOrNull() ?: 40
                    gif(400, 400) {
                        options {
                            disposalMethod = AnimationDisposalMode.RESTORE_BG_COLOR
                            alphaType = ColorAlphaType.OPAQUE
                        }
                        frames.forEach {
                            frame(Bitmap.makeFromImage(Image.makeFromEncoded(it))) { duration = d }
                        }
                    }.bytes
                }
                call.respondBytes(bytes, ContentType.Image.GIF)
            }

            "homo" -> {
                val bytes = GroupView.homo.getGroupPhoto(args)
                call.respondBytes(bytes, ContentType.Image.PNG)
            }

            "trump" -> {
                val bytes = GroupView.trump.getGroupPhoto(args)
                call.respondBytes(bytes, ContentType.Image.PNG)
            }

            "milano" -> {
                val bytes = GroupView.milano.getGroupPhoto(args)
                call.respondBytes(bytes, ContentType.Image.PNG)
            }

            "temple" -> {
                val bytes = GroupView.temple.getGroupPhoto(args)
                call.respondBytes(bytes, ContentType.Image.PNG)
            }

            "gwall" -> {
                val bytes = GroupView.gWall.getGroupPhoto(args)
                call.respondBytes(bytes, ContentType.Image.PNG)
            }

            "hip" -> {
                val bytes = asGIF(HipView.getHipShake(args), HipView.imageWidth.toInt(), HipView.imageHeight.toInt(), d)
                call.respondBytes(bytes, ContentType.Image.GIF)
            }

            else -> call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/refresh/{type}/{content}") {
        val success = when (call.parameters["type"]!!.lowercase()) {
            "id" -> Skin.refreshById(call.parameters["content"]!!)
            "name" -> Skin.refreshByName(call.parameters["content"]!!)
            else -> {
                call.respond(HttpStatusCode.BadRequest, "type must be 'id' or 'name'")
                return@get
            }
        }
        call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.NotFound)
    }

    staticResources("/", "public") {
        default("index.html")
    }

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path().startsWith("/render/")) {
            logger.info("request received")
            if (!rateLimiter.allow(call.request.origin.remoteAddress)) {
                logger.info("request invalid")
                call.respond(HttpStatusCode.TooManyRequests)
                finish()
            }
        }
    }

}