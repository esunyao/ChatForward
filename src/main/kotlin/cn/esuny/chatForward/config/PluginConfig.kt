package cn.esuny.chatForward.config

import kotlin.jvm.JvmOverloads

/**
 * ChatForward 插件配置数据类
 */
data class PluginConfig constructor(
    var websocket: WebSocketConfig = WebSocketConfig(),
    var chat: ChatConfig = ChatConfig(),
    var storage: StorageConfig = StorageConfig()
) {
    // 无参二次构造函数，供 SnakeYAML 或其他需要无参构造的反射工具使用
    constructor() : this(WebSocketConfig(), ChatConfig(), StorageConfig())
    /**
     * WebSocket 配置
     */
    data class WebSocketConfig(
        var url: String = "ws://localhost:8080/chat",
        var token: String = "",
        var reconnectInterval: Long = 5000,
        var maxReconnectAttempts: Int = 10,
        var heartbeatInterval: Long = 30000, // 心跳间隔（毫秒）
        var heartbeatTimeout: Long = 60000   // 心跳超时时间（毫秒）
    )

    /**
     * 聊天相关配置
     */
    data class ChatConfig(
        var mainPrefix: String = "§8[§a群组§8]§r",
        var mcdrCommandPrefix: List<String> = listOf("!!", "##"),
        var serverPrefixMapping: Map<String, String> = mapOf(
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
        var playerPersonalityFile: String = "player_personality.json"
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