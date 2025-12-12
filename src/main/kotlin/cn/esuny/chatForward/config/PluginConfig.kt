package cn.esuny.chatForward.config

import kotlin.jvm.JvmOverloads

/**
 * ChatForward 插件配置数据类
 */
@JvmOverloads
data class PluginConfig @JvmOverloads constructor(
    val websocket: WebSocketConfig = WebSocketConfig(),
    val chat: ChatConfig = ChatConfig(),
    val storage: StorageConfig = StorageConfig()
) {
    /**
     * WebSocket 配置
     */
    data class WebSocketConfig(
        val url: String = "ws://localhost:8080/chat",
        val token: String = "",
        val reconnectInterval: Long = 5000,
        val maxReconnectAttempts: Int = 10,
        val heartbeatInterval: Long = 30000, // 心跳间隔（毫秒）
        val heartbeatTimeout: Long = 60000   // 心跳超时时间（毫秒）
    )

    /**
     * 聊天相关配置
     */
    data class ChatConfig(
        val mainPrefix: String = "§8[§a群组§8]§r",
        val mcdrCommandPrefix: List<String> = listOf("!!", "##"),
        val serverPrefixMapping: Map<String, String> = mapOf(
            "lobby" to "§6大厅",
            "survival" to "§2生存",
            "creative" to "§b创造",
            "minigame" to "§e小游戏"
        )
    )

    /**
     * 存储配置
     */
    data class StorageConfig(
        val playerPersonalityFile: String = "player_personality.json"
    )

    companion object {
        /**
         * 创建默认配置
         */
        fun default(): PluginConfig {
            return PluginConfig(
                websocket = WebSocketConfig(),
                chat = ChatConfig(),
                storage = StorageConfig()
            )
        }
    }
}