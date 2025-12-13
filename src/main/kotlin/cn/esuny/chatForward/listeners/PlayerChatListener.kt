package cn.esuny.chatForward.listeners

import cn.esuny.chatForward.config.PluginConfig
import cn.esuny.chatForward.websocket.WebSocketService
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.proxy.Player
import org.slf4j.LoggerFactory

/**
 * 玩家聊天事件监听器
 */
class PlayerChatListener(
    private val webSocketService: WebSocketService,
    private val config: PluginConfig
) {
    private val logger = LoggerFactory.getLogger(PlayerChatListener::class.java)

    /**
     * 处理玩家聊天事件
     */
    @Subscribe
    fun onPlayerChat(event: PlayerChatEvent) {
        val player = event.player
        val message = event.message

        // 检查消息是否以MCDR命令前缀开头
        if (startsWithMcdrCommandPrefix(message)) {
            logger.debug("跳过MCDR命令消息: ${player.username}: $message")
            return
        }

        // 获取玩家当前服务器
        val serverName = getPlayerServerName(player)

        if (serverName != null) {
            // 发送玩家聊天事件到WebSocket服务器
            val success = webSocketService.sendPlayerChatEvent(
                player = player.username,
                message = message,
                server = serverName
            )

            if (success) {
                logger.debug("已转发玩家聊天事件: ${player.username}@$serverName: $message")
            } else {
                logger.warn("转发玩家聊天事件失败: ${player.username}@$serverName")
            }
        } else {
            logger.warn("无法获取玩家 ${player.username} 的服务器信息，跳过聊天事件转发")
        }
    }

    /**
     * 检查消息是否以MCDR命令前缀开头
     */
    private fun startsWithMcdrCommandPrefix(message: String): Boolean {
        return config.chat.mcdrCommandPrefix.any { prefix ->
            message.startsWith(prefix)
        }
    }

    /**
     * 获取玩家所在服务器名称
     */
    private fun getPlayerServerName(player: Player): String? {
        return try {
            player.currentServer
                .map { it.serverInfo.name }
                .orElse(null)
        } catch (e: Exception) {
            logger.error("获取玩家服务器信息失败: ${player.username}", e)
            null
        }
    }
}