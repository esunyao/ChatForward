package cn.esuny.chatForward.listeners

import cn.esuny.chatForward.websocket.WebSocketService
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.Player
import org.slf4j.LoggerFactory

/**
 * 玩家切换服务器事件监听器
 */
class PlayerSwitchServerListener(
    private val webSocketService: WebSocketService
) {
    private val logger = LoggerFactory.getLogger(PlayerSwitchServerListener::class.java)

    /**
     * 处理玩家切换服务器事件
     */
    @Subscribe
    fun onPlayerSwitchServer(event: ServerConnectedEvent) {
        val player = event.player
        val previousServer = event.previousServer
        val newServer = event.server

        // 只有当玩家从其他服务器切换过来时才发送事件（不是初次连接）
        if (previousServer != null) {
            val fromServerName = getServerName(previousServer)
            val toServerName = getServerName(newServer)

            if (fromServerName != null && toServerName != null) {
                // 发送玩家切换服务器事件到WebSocket服务器
                val success = webSocketService.sendPlayerHandoffEvent(
                    player = player.username,
                    fromServer = fromServerName,
                    toServer = toServerName
                )

                if (success) {
                    logger.info("已转发玩家切换服务器事件: ${player.username} 从 $fromServerName 切换到 $toServerName")
                } else {
                    logger.warn("转发玩家切换服务器事件失败: ${player.username}")
                }
            } else {
                logger.warn("无法获取服务器名称，跳过玩家切换服务器事件: ${player.username}")
            }
        } else {
            // 这是玩家初次连接到服务器，已经在PlayerJoinListener中处理
            val toServerName = getServerName(newServer)
            logger.debug("玩家初次连接服务器: ${player.username} -> $toServerName")
        }
    }

    /**
     * 获取服务器名称
     * 使用Any类型避免导入问题，通过反射获取名称
     */
    private fun getServerName(server: Any): String? {
        return try {
            // 尝试通过反射获取serverInfo属性
            val serverInfoField = server.javaClass.getMethod("getServerInfo").invoke(server)
            val nameField = serverInfoField.javaClass.getMethod("getName").invoke(serverInfoField)
            nameField.toString()
        } catch (e: Exception) {
            // 如果反射失败，尝试直接调用toString并提取名称
            try {
                val serverString = server.toString()
                // 尝试从字符串中提取服务器名称
                // 例如: RegisteredServer{serverInfo=ServerInfo{name='lobby', ...}}
                val pattern = "name='([^']+)'".toRegex()
                val match = pattern.find(serverString)
                match?.groupValues?.get(1)
            } catch (e2: Exception) {
                logger.error("获取服务器名称失败", e2)
                null
            }
        }
    }
}