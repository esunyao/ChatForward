package cn.esuny.chatForward.websocket

import cn.esuny.chatForward.config.PluginConfig
import cn.esuny.chatForward.util.JsonUtil
import com.alibaba.fastjson2.JSONObject
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * ChatForward WebSocket客户端
 */
class ChatWebSocketClient(
    private val serverUri: URI,
    private val config: PluginConfig.WebSocketConfig
) : WebSocketClient(serverUri) {
    private val logger = LoggerFactory.getLogger(ChatWebSocketClient::class.java)
    private val messageQueue = ConcurrentLinkedQueue<String>()
    private var reconnectAttempts = 0
    private var scheduledExecutor: ScheduledExecutorService? = null
    private var isConnecting = false
    private var messageHandler: ((String) -> Unit)? = null
    private var lastHeartbeatTime: Long = 0
    private var lastPongTime: Long = 0
    private var isAuthenticated = false

    init {
        connectionLostTimeout = 60 // 连接丢失超时时间（秒）
    }

    /**
     * 设置消息处理器
     */
    fun setMessageHandler(handler: (String) -> Unit) {
        this.messageHandler = handler
    }

    /**
     * 连接建立时的回调
     */
    override fun onOpen(handshakedata: ServerHandshake?) {
        logger.info("WebSocket连接已建立: ${serverUri}")
        reconnectAttempts = 0
        isConnecting = false
        isAuthenticated = false
        lastHeartbeatTime = System.currentTimeMillis()
        lastPongTime = System.currentTimeMillis()

        // 发送身份验证
        sendAuthentication()

        // 启动心跳检测
        startHeartbeat()

        // 启动响应清理任务
        startResponseCleanup()

        // 启动心跳超时检测
        startHeartbeatTimeoutCheck()
    }

    /**
     * 收到消息时的回调
     */
    override fun onMessage(message: String) {
        logger.debug("收到WebSocket消息: $message")

        try {
            val json = JsonUtil.parseObject(message)
            if (json != null) {
                // 检查是否是身份验证响应
                if (!isAuthenticated) {
                    val authResult = handleAuthenticationResponse(json)
                    if (authResult) {
                        logger.info("身份验证成功")
                        // 发送队列中的消息
                        flushMessageQueue()
                        return
                    } else {
                        logger.error("身份验证失败，关闭连接")
                        closeConnection(1008, "Authentication failed")
                        return
                    }
                }

                // 检查是否是心跳响应
                if (handleHeartbeatResponse(json)) {
                    return
                }

                // 检查是否是响应消息（包含echo字段）
                val echo = json.getString("echo")
                if (echo != null) {
                    // 这是对之前请求的响应
                    val responseMap = json.toMap()
                    ResponsePool.putResponse(echo, responseMap)
                    logger.trace("处理响应消息: echo=$echo")
                    return
                }

                // 处理其他类型的消息（如服务器推送的消息）
                messageHandler?.invoke(message)
            }
        } catch (e: Exception) {
            logger.error("处理WebSocket消息失败", e)
        }
    }

    /**
     * 连接关闭时的回调
     */
    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        logger.warn("WebSocket连接关闭: code=$code, reason=$reason, remote=$remote")

        // 停止心跳检测
        stopHeartbeat()

        // 如果不是主动关闭，则尝试重连
        if (code != 1000 && !isClosing) {
            scheduleReconnect()
        }
    }

    /**
     * 发生错误时的回调
     */
    override fun onError(ex: Exception) {
        logger.error("WebSocket错误", ex)
    }

    /**
     * 发送字典数据（兼容原有 send_dict 方法）
     */
    fun sendDict(data: Map<String, Any>): Boolean {
        return try {
            val json = com.alibaba.fastjson2.JSON.toJSONString(data)
            sendDict(json)
        } catch (e: Exception) {
            logger.error("JSON序列化失败", e)
            false
        }
    }

    /**
     * 发送JSON字符串
     */
    fun sendDict(json: String): Boolean {
        return if (isOpen) {
            send(json)
            true
        } else {
            // 连接未建立，将消息加入队列
            messageQueue.offer(json)
            logger.debug("连接未建立，消息加入队列: ${json.take(50)}...")
            false
        }
    }

    /**
     * 获取连接状态
     */
    fun isConnected(): Boolean {
        return isOpen
    }

    /**
     * 获取重连尝试次数
     */
    fun getReconnectAttempts(): Int {
        return reconnectAttempts
    }

    /**
     * 获取队列中的消息数量
     */
    fun getQueueSize(): Int {
        return messageQueue.size
    }

    /**
     * 安全关闭连接
     */
    fun safeClose() {
        try {
            close()
        } catch (e: Exception) {
            logger.error("关闭WebSocket连接时发生错误", e)
        } finally {
            stopHeartbeat()
        }
    }

    /**
     * 刷新消息队列（发送队列中的所有消息）
     */
    private fun flushMessageQueue() {
        var sentCount = 0
        while (messageQueue.isNotEmpty() && isOpen) {
            val message = messageQueue.poll()
            if (message != null) {
                try {
                    send(message)
                    sentCount++
                } catch (e: Exception) {
                    logger.error("发送队列消息失败，重新加入队列", e)
                    messageQueue.offer(message) // 重新加入队列
                    break
                }
            }
        }
        if (sentCount > 0) {
            logger.info("已发送队列中的 $sentCount 条消息")
        }
    }

    /**
     * 安排重连
     */
    private fun scheduleReconnect() {
        if (isConnecting || reconnectAttempts >= config.maxReconnectAttempts) {
            logger.warn("已达到最大重连尝试次数(${config.maxReconnectAttempts})或正在连接中")
            return
        }

        reconnectAttempts++
        val delay = config.reconnectInterval * reconnectAttempts // 指数退避

        logger.info("计划在 ${delay}ms 后重连 (尝试 $reconnectAttempts/${config.maxReconnectAttempts})")

        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor?.schedule({
            try {
                isConnecting = true
                logger.info("正在尝试重连...")
                reconnect()
            } catch (e: Exception) {
                logger.error("重连失败", e)
                isConnecting = false
                scheduleReconnect() // 继续尝试重连
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    /**
     * 启动心跳检测（兼容旧版本）
     */
    private fun startHeartbeat() {
        // 现在由 startHeartbeatTimeoutCheck 处理心跳
    }

    /**
     * 停止心跳检测
     */
    private fun stopHeartbeat() {
        scheduledExecutor?.shutdownNow()
        scheduledExecutor = null
    }

    /**
     * 启动响应清理任务
     */
    private fun startResponseCleanup() {
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor?.scheduleAtFixedRate({
            try {
                ResponsePool.cleanTimeoutResponses(30) // 清理30秒前的超时响应
            } catch (e: Exception) {
                logger.error("清理超时响应失败", e)
            }
        }, 60, 60, TimeUnit.SECONDS) // 每60秒清理一次
    }

    /**
     * 发送身份验证
     */
    private fun sendAuthentication() {
        try {
            val authData = mapOf(
                "action" to "auth",
                "token" to config.token,
                "timestamp" to System.currentTimeMillis()
            )
            val json = com.alibaba.fastjson2.JSON.toJSONString(authData)
            send(json)
            logger.info("已发送身份验证请求")
        } catch (e: Exception) {
            logger.error("发送身份验证失败", e)
        }
    }

    /**
     * 处理身份验证响应
     */
    private fun handleAuthenticationResponse(json: JSONObject): Boolean {
        val action = json.getString("action")
        val status = json.getString("status")

        if (action == "auth" && status == "ok") {
            isAuthenticated = true
            return true
        }

        return false
    }

    /**
     * 处理心跳响应
     */
    private fun handleHeartbeatResponse(json: JSONObject): Boolean {
        val action = json.getString("action")

        if (action == "pong") {
            lastPongTime = System.currentTimeMillis()
            logger.trace("收到心跳响应")
            return true
        }

        return false
    }

    /**
     * 发送心跳
     */
    private fun sendHeartbeat() {
        if (!isAuthenticated) {
            return
        }

        try {
            val heartbeatData = mapOf(
                "action" to "ping",
                "timestamp" to System.currentTimeMillis()
            )
            val json = com.alibaba.fastjson2.JSON.toJSONString(heartbeatData)
            send(json)
            lastHeartbeatTime = System.currentTimeMillis()
            logger.trace("发送心跳")
        } catch (e: Exception) {
            logger.error("发送心跳失败", e)
        }
    }

    /**
     * 启动心跳超时检测
     */
    private fun startHeartbeatTimeoutCheck() {
        stopHeartbeat() // 先停止现有的心跳

        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor?.scheduleAtFixedRate({
            if (isOpen && isAuthenticated) {
                val now = System.currentTimeMillis()
                val timeSinceLastPong = now - lastPongTime

                if (timeSinceLastPong > config.heartbeatTimeout) {
                    logger.warn("心跳超时 (${timeSinceLastPong}ms > ${config.heartbeatTimeout}ms)，重新连接")
                    scheduleReconnect()
                } else if (now - lastHeartbeatTime > config.heartbeatInterval) {
                    sendHeartbeat()
                }
            }
        }, config.heartbeatInterval, config.heartbeatInterval, TimeUnit.MILLISECONDS)
    }

    /**
     * 关闭连接
     */
    override fun closeConnection(code: Int, reason: String?) {
        try {
            close(code, reason)
        } catch (e: Exception) {
            logger.error("关闭连接时发生错误", e)
        }
    }

    companion object {
        /**
         * 创建WebSocket客户端
         */
        fun create(config: PluginConfig.WebSocketConfig): ChatWebSocketClient {
            return try {
                val uri = URI(config.url)
                ChatWebSocketClient(uri, config)
            } catch (e: Exception) {
                throw IllegalArgumentException("无效的WebSocket URL: ${config.url}", e)
            }
        }
    }
}