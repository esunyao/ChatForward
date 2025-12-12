package cn.esuny.chatForward.websocket

import cn.esuny.chatForward.config.PluginConfig
import cn.esuny.chatForward.util.JsonUtil
import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket服务
 */
class WebSocketService(
    private val config: PluginConfig.WebSocketConfig
) {
    private val logger = LoggerFactory.getLogger(WebSocketService::class.java)
    private var webSocketClient: ChatWebSocketClient? = null
    private val isConnecting = AtomicBoolean(false)
    private var messageHandler: ((String) -> Unit)? = null

    /**
     * 设置消息处理器
     */
    fun setMessageHandler(handler: (String) -> Unit) {
        this.messageHandler = handler
        webSocketClient?.setMessageHandler(handler)
    }

    /**
     * 连接WebSocket服务器
     */
    fun connect(): Boolean {
        if (isConnecting.compareAndSet(false, true)) {
            return try {
                logger.info("正在连接WebSocket服务器: ${config.url}")

                // 创建WebSocket客户端
                val client = ChatWebSocketClient.create(config)
                
                // 设置消息处理器
                messageHandler?.let { client.setMessageHandler(it) }
                
                webSocketClient = client

                // 连接服务器
                client.connect()

                // 等待连接建立
                Thread.sleep(1000)

                if (client.isConnected()) {
                    logger.info("WebSocket连接成功建立")
                    true
                } else {
                    logger.error("WebSocket连接失败")
                    false
                }
            } catch (e: Exception) {
                logger.error("连接WebSocket服务器失败", e)
                false
            } finally {
                isConnecting.set(false)
            }
        } else {
            logger.warn("已有连接正在进行中")
            return false
        }
    }

    /**
     * 断开WebSocket连接
     */
    fun disconnect() {
        webSocketClient?.safeClose()
        webSocketClient = null
        logger.info("WebSocket连接已断开")
    }

    /**
     * 发送字典数据（兼容原有 send_dict 方法）
     */
    fun send_dict(data: Map<String, Any>): Boolean {
        val client = webSocketClient
        return if (client != null && client.isConnected()) {
            client.sendDict(data)
        } else {
            logger.warn("WebSocket未连接，无法发送消息")
            false
        }
    }

    /**
     * 发送JSON字符串
     */
    fun send_dict(json: String): Boolean {
        val client = webSocketClient
        return if (client != null && client.isConnected()) {
            client.sendDict(json)
        } else {
            logger.warn("WebSocket未连接，无法发送消息")
            false
        }
    }

    /**
     * 获取连接状态
     */
    fun isConnected(): Boolean {
        return webSocketClient?.isConnected() ?: false
    }

    /**
     * 获取WebSocket客户端
     */
    fun getClient(): ChatWebSocketClient? {
        return webSocketClient
    }

    /**
     * 重新连接
     */
    fun reconnect(): Boolean {
        logger.info("手动触发重新连接")
        disconnect()
        return connect()
    }

    /**
     * 关闭服务
     */
    fun shutdown() {
        disconnect()
        logger.info("WebSocket服务已关闭")
    }

    /**
     * 发送玩家聊天事件
     */
    fun sendPlayerChatEvent(player: String, message: String, server: String): Boolean {
        val data = mapOf(
            "Mode" to "PlayerChatEvent",
            "player" to player,
            "message" to message,
            "server" to server
        )
        return send_dict(data)
    }

    /**
     * 发送玩家离开事件
     */
    fun sendPlayerLeftEvent(player: String): Boolean {
        val data = mapOf(
            "Mode" to "PlayerLeftEvent",
            "player" to player
        )
        return send_dict(data)
    }

    /**
     * 发送玩家加入事件
     */
    fun sendPlayerJoinEvent(player: String, server: String): Boolean {
        val data = mapOf(
            "Mode" to "PlayerJoinEvent",
            "player" to player,
            "server" to server
        )
        return send_dict(data)
    }

    /**
     * 发送玩家切换服务器事件
     */
    fun sendPlayerHandoffEvent(player: String, fromServer: String, toServer: String): Boolean {
        val data = mapOf(
            "Mode" to "PlayerHandoffEvent",
            "player" to player,
            "fromserver" to fromServer,
            "toserver" to toServer
        )
        return send_dict(data)
    }

    /**
     * 发送带响应的请求（参考协议格式）
     * @param action 动作名称
     * @param params 参数
     * @return 响应数据，如果失败返回null
     */
    fun sendRequest(action: String, params: Map<String, Any>): Map<String, Any>? {
        val client = webSocketClient
        if (client == null || !client.isConnected()) {
            logger.warn("WebSocket未连接，无法发送请求")
            return null
        }

        val requestId = UUID.randomUUID().toString()
        val payload = mapOf(
            "action" to action,
            "params" to params,
            "echo" to requestId
        )

        return try {
            val json = JsonUtil.toJsonString(payload)
            if (client.sendDict(json)) {
                // 等待响应
                ResponsePool.getResponse(requestId, 10)
            } else {
                logger.error("发送请求失败")
                null
            }
        } catch (e: Exception) {
            logger.error("发送请求时发生错误: action=$action", e)
            null
        }
    }
}