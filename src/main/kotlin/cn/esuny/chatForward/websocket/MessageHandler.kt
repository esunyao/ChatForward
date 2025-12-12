package cn.esuny.chatForward.websocket

import cn.esuny.chatForward.config.PluginConfig
import cn.esuny.chatForward.util.JsonUtil
import com.alibaba.fastjson2.JSONObject
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import org.slf4j.LoggerFactory

/**
 * WebSocket消息处理器
 */
class MessageHandler(
    private val proxyServer: ProxyServer,
    private val config: PluginConfig,
    private val webSocketService: WebSocketService
) {
    private val logger = LoggerFactory.getLogger(MessageHandler::class.java)

    /**
     * 处理收到的WebSocket消息
     */
    fun handleMessage(message: String) {
        try {
            val json = JsonUtil.parseObject(message)
            if (json != null) {
                processJsonMessage(json)
            } else {
                logger.warn("无法解析的JSON消息: ${message.take(100)}...")
            }
        } catch (e: Exception) {
            logger.error("处理消息时发生错误: $message", e)
        }
    }

    /**
     * 处理JSON消息
     */
    private fun processJsonMessage(json: JSONObject) {
        val mode = JsonUtil.getString(json, "Mode")
        val action = JsonUtil.getString(json, "action")

        when {
            mode.isNotBlank() -> handleModeMessage(mode, json)
            action.isNotBlank() -> handleActionMessage(action, json)
            else -> logger.warn("未知的消息格式: ${JsonUtil.prettyJson(json.toJSONString())}")
        }
    }

    /**
     * 处理Mode类型的消息（从服务器接收的玩家事件）
     */
    private fun handleModeMessage(mode: String, json: JSONObject) {
        when (mode) {
            "PlayerChatEvent" -> handlePlayerChatEvent(json)
            "PlayerJoinEvent" -> handlePlayerJoinEvent(json)
            "PlayerLeftEvent" -> handlePlayerLeftEvent(json)
            "PlayerHandoffEvent" -> handlePlayerHandoffEvent(json)
            "PlayerListUpdate" -> handlePlayerListUpdate(json)
            else -> logger.warn("未知的Mode类型: $mode")
        }
    }

    /**
     * 处理Action类型的消息（服务器指令或响应）
     */
    private fun handleActionMessage(action: String, json: JSONObject) {
        when (action) {
            "broadcast" -> handleBroadcastMessage(json)
            "command" -> handleCommandMessage(json)
            "player_list" -> handlePlayerListRequest(json)
            "server_status" -> handleServerStatusRequest(json)
            "auth" -> handleAuthResponse(json) // 身份验证响应已在WebSocketClient中处理
            "ping" -> handlePingResponse(json) // 心跳响应已在WebSocketClient中处理
            else -> logger.warn("未知的Action类型: $action")
        }
    }

    /**
     * 处理玩家聊天事件
     */
    private fun handlePlayerChatEvent(json: JSONObject) {
        val player = JsonUtil.getString(json, "player")
        val message = JsonUtil.getString(json, "message")
        val server = JsonUtil.getString(json, "server")

        if (player.isNotBlank() && message.isNotBlank()) {
            // 获取服务器前缀
            val serverPrefix = config.chat.serverPrefixMapping[server] ?: server
            val formattedMessage = "${config.chat.mainPrefix} §r<$player> §7$message"

            // 广播到所有配置的服务器
            broadcastToServers(formattedMessage, server)
            logger.info("转发玩家聊天: $player@$server: $message")
        } else {
            logger.warn("玩家聊天事件缺少必要字段: player=$player, message=$message")
        }
    }

    /**
     * 处理玩家加入事件
     */
    private fun handlePlayerJoinEvent(json: JSONObject) {
        val player = JsonUtil.getString(json, "player")
        val server = JsonUtil.getString(json, "server")

        if (player.isNotBlank() && server.isNotBlank()) {
            val serverPrefix = config.chat.serverPrefixMapping[server] ?: server
            val message = "${config.chat.mainPrefix} §a玩家 $player §a加入了服务器 §r$serverPrefix"

            broadcastToServers(message, server)
            logger.info("玩家加入: $player 加入了 $server")
        }
    }

    /**
     * 处理玩家离开事件
     */
    private fun handlePlayerLeftEvent(json: JSONObject) {
        val player = JsonUtil.getString(json, "player")

        if (player.isNotBlank()) {
            val message = "${config.chat.mainPrefix} §c玩家 $player §c离开了服务器"

            broadcastToAllServers(message)
            logger.info("玩家离开: $player")
        }
    }

    /**
     * 处理玩家切换服务器事件
     */
    private fun handlePlayerHandoffEvent(json: JSONObject) {
        val player = JsonUtil.getString(json, "player")
        val fromServer = JsonUtil.getString(json, "fromserver")
        val toServer = JsonUtil.getString(json, "toserver")

        if (player.isNotBlank() && fromServer.isNotBlank() && toServer.isNotBlank()) {
            val fromPrefix = config.chat.serverPrefixMapping[fromServer] ?: fromServer
            val toPrefix = config.chat.serverPrefixMapping[toServer] ?: toServer
            val message = "${config.chat.mainPrefix} §e玩家 $player §e从 §r$fromPrefix §e切换到 §r$toPrefix"

            broadcastToServers(message, fromServer, toServer)
            logger.info("玩家切换服务器: $player 从 $fromServer 切换到 $toServer")
        }
    }

    /**
     * 处理玩家列表更新
     */
    private fun handlePlayerListUpdate(json: JSONObject) {
        // 这里可以处理玩家列表更新，例如更新本地缓存
        val server = JsonUtil.getString(json, "server")
        val players = json.getJSONArray("players")?.let { jsonArray ->
            (0 until jsonArray.size).map { jsonArray.getString(it) }
        } ?: emptyList()

        logger.debug("玩家列表更新: $server (${players.size} 名玩家)")
        // TODO: 更新本地玩家列表缓存
    }

    /**
     * 处理广播消息
     */
    private fun handleBroadcastMessage(json: JSONObject) {
        val message = JsonUtil.getString(json, "message")
        val target = JsonUtil.getString(json, "target", "all") // all, specific_servers

        if (message.isNotBlank()) {
            when (target) {
                "all" -> broadcastToAllServers(message)
                "specific_servers" -> {
                    val servers = json.getJSONArray("servers")?.let { jsonArray ->
                        (0 until jsonArray.size).map { jsonArray.getString(it) }
                    } ?: emptyList()
                    broadcastToSpecificServers(message, servers)
                }
                else -> logger.warn("未知的广播目标: $target")
            }
            logger.info("执行广播: $message (target: $target)")
        }
    }

    /**
     * 处理命令消息
     */
    private fun handleCommandMessage(json: JSONObject) {
        val command = JsonUtil.getString(json, "command")
        val executor = JsonUtil.getString(json, "executor", "console")

        if (command.isNotBlank()) {
            // 这里可以执行服务器命令
            logger.info("收到命令执行请求: $command (executor: $executor)")
            // TODO: 实现命令执行逻辑
        }
    }

    /**
     * 处理玩家列表请求
     */
    private fun handlePlayerListRequest(json: JSONObject) {
        val server = JsonUtil.getString(json, "server", "all")
        val requestId = JsonUtil.getString(json, "echo")

        if (requestId.isNotBlank()) {
            // 获取玩家列表并发送响应
            val playerList = getPlayerList(server)
            val response = mapOf(
                "action" to "player_list_response",
                "server" to server,
                "players" to playerList,
                "count" to playerList.size,
                "echo" to requestId
            )
            webSocketService.send_dict(response)
            logger.debug("响应玩家列表请求: $server (${playerList.size} 名玩家)")
        }
    }

    /**
     * 处理服务器状态请求
     */
    private fun handleServerStatusRequest(json: JSONObject) {
        val requestId = JsonUtil.getString(json, "echo")

        if (requestId.isNotBlank()) {
            // 获取服务器状态
            val serverStatus = getServerStatus()
            val response = mapOf(
                "action" to "server_status_response",
                "status" to serverStatus,
                "echo" to requestId
            )
            webSocketService.send_dict(response)
            logger.debug("响应服务器状态请求")
        }
    }

    /**
     * 处理身份验证响应（备用处理）
     */
    private fun handleAuthResponse(json: JSONObject) {
        val status = JsonUtil.getString(json, "status")
        logger.info("身份验证响应: $status")
    }

    /**
     * 处理心跳响应（备用处理）
     */
    private fun handlePingResponse(json: JSONObject) {
        logger.trace("收到心跳响应")
    }

    /**
     * 广播消息到所有配置的服务器
     */
    private fun broadcastToAllServers(message: String) {
        config.chat.serverPrefixMapping.keys.forEach { serverName ->
            broadcastToServer(serverName, message)
        }
    }

    /**
     * 广播消息到特定服务器
     */
    private fun broadcastToServers(message: String, vararg servers: String) {
        servers.forEach { serverName ->
            broadcastToServer(serverName, message)
        }
    }

    /**
     * 广播消息到指定的服务器列表
     */
    private fun broadcastToSpecificServers(message: String, servers: List<String>) {
        servers.forEach { serverName ->
            if (config.chat.serverPrefixMapping.containsKey(serverName)) {
                broadcastToServer(serverName, message)
            }
        }
    }

    /**
     * 广播消息到单个服务器
     */
    private fun broadcastToServer(serverName: String, message: String) {
        try {
            proxyServer.getServer(serverName).ifPresent { server ->
                server.sendMessage(Component.text(message))
            }
        } catch (e: Exception) {
            logger.error("广播消息到服务器失败: $serverName", e)
        }
    }

    /**
     * 获取玩家列表
     */
    private fun getPlayerList(server: String): List<String> {
        return try {
            when (server) {
                "all" -> proxyServer.allPlayers.map { it.username }
                else -> proxyServer.getServer(server)
                    .map { it.playersConnected.map { player -> player.username } }
                    .orElse(emptyList())
            }
        } catch (e: Exception) {
            logger.error("获取玩家列表失败: $server", e)
            emptyList()
        }
    }

    /**
     * 获取服务器状态
     */
    private fun getServerStatus(): Map<String, Any> {
        return try {
            val servers = config.chat.serverPrefixMapping.keys.associateWith { serverName ->
                val server = proxyServer.getServer(serverName)
                mapOf(
                    "online" to server.isPresent,
                    "player_count" to server.map { it.playersConnected.size }.orElse(0),
                    "players" to server.map { it.playersConnected.map { player -> player.username } }.orElse(emptyList())
                )
            }

            mapOf(
                "total_players" to proxyServer.playerCount,
                "servers" to servers,
                "online" to true,
                "timestamp" to System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error("获取服务器状态失败", e)
            emptyMap()
        }
    }
}