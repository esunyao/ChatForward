package cn.esuny.chatForward.listeners

import cn.esuny.chatForward.websocket.WebSocketService
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.proxy.Player
import org.slf4j.LoggerFactory

/**
 * 玩家加入事件监听器
 */
class PlayerJoinListener(
    private val webSocketService: WebSocketService
) {
    private val logger = LoggerFactory.getLogger(PlayerJoinListener::class.java)

    /**
     * 处理玩家加入事件
     */
    @Subscribe
    fun onPlayerJoin(event: PostLoginEvent) {
        val player = event.player

        // 获取玩家初始连接的服务器（通常是登录服务器）
        val serverName = getPlayerInitialServerName(player)

        if (serverName != null) {
            // 发送玩家加入事件到WebSocket服务器
            val success = webSocketService.sendPlayerJoinEvent(
                player = player.username,
                server = serverName
            )

            if (success) {
                logger.debug("已转发玩家加入事件: ${player.username} 加入了 $serverName")
            } else {
                logger.warn("转发玩家加入事件失败: ${player.username}")
            }
        } else {
            logger.warn("无法获取玩家 ${player.username} 的初始服务器信息，跳过加入事件转发")
        }
    }

    /**
     * 获取玩家初始连接的服务器名称
     * 注意：PostLoginEvent触发时，玩家可能还没有连接到具体服务器
     * 这里使用登录服务器的名称作为默认值
     */
    private fun getPlayerInitialServerName(player: Player): String? {
        return try {
            // 首先尝试获取当前服务器
            player.currentServer
                .map { it.serverInfo.name }
                .orElse("login") // 如果还没有连接到具体服务器，使用"login"作为默认值
        } catch (e: Exception) {
            logger.error("获取玩家初始服务器信息失败: ${player.username}", e)
            "login" // 发生异常时使用默认值
        }
    }
}