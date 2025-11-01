package top.e404.skin.server

import com.charleskorn.kaml.Yaml
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyType
import io.ktor.client.engine.http
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

object ConfigManager {
    private val file = File("config.yml")
    lateinit var config: Config

    fun saveDefault(): Config? {
        if (file.isDirectory) file.deleteRecursively()
        if (file.exists()) return null
        val default = Config()
        file.writeText(Yaml.default.encodeToString(default))
        return default
    }

    fun load() {
        val default = saveDefault()
        if (default != null) {
            config = default
            return
        }
        config = Yaml.default.decodeFromString(file.readText())
    }
}

@Serializable
data class Config(
    val address: String = "127.0.0.1",
    val port: Int = 2345,
    val proxy: Proxy? = null,
    val timeout: Long = 86400
)

@Serializable
data class Proxy(
    val type: ProxyType = ProxyType.HTTP,
    val address: String = "localhost",
    val port: Int = 7890
) {
    val proxy by lazy {
        when (type) {
            ProxyType.SOCKS -> ProxyBuilder.socks(address, port)
            else -> ProxyBuilder.http("http://$address:$port/")
        }
    }
}
