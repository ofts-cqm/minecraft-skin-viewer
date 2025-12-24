package top.e404.skin.server

import java.util.concurrent.ConcurrentHashMap

class RateLimiter(
    private val minIntervalMs: Int,
    private val maxEntries: Int,
    private val cleanCutoffTime: Int
) {

    private val lastAccessMap = ConcurrentHashMap<String, Long>()

    /**
     * @return true if allowed, false if rate-limited
     */
    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastAccessMap[key]

        if (last != null && now - last < minIntervalMs) {
            return false
        }

        cleanup()

        lastAccessMap[key] = now
        return true
    }

    private fun cleanup() {
        val cutoff = System.currentTimeMillis() - cleanCutoffTime

        if (lastAccessMap.size > maxEntries) {
            lastAccessMap.entries.removeIf { it.value < cutoff }
        }
    }
}
