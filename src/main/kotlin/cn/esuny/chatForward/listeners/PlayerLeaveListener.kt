package cn.esuny.chatForward.listeners

import cn.esuny.chatForward.websocket.WebSocketService
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.proxy.Player
import org.slf4j.LoggerFactory

/**
 * 玩家离开事件监听器
 */
class PlayerLeaveListener(
    private val webSocketService: WebSocketService
) {
    private val logger = LoggerFactory.getLogger(PlayerLeaveListener::class.java)

    /**
     * 处理玩家离开事件
     */
    @Subscribe
    fun onPlayerLeave(event: DisconnectEvent) {
        val player = event.player

        // 发送玩家离开事件到WebSocket服务器
        val success = webSocketService.sendPlayerLeftEvent(
            player = player.username
        )

        if (success) {
            logger.info("已转发玩家离开事件: ${player.username}")
        } else {
            logger.warn("转发玩家离开事件失败: ${player.username}")
        }
    }
}