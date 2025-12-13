package cn.esuny.chatForward

import cn.esuny.chatForward.command.ReloadCommand
import cn.esuny.chatForward.config.ConfigManager
import cn.esuny.chatForward.config.PluginConfig
import cn.esuny.chatForward.listeners.*
import cn.esuny.chatForward.websocket.MessageHandler
import cn.esuny.chatForward.websocket.WebSocketService
import com.google.inject.Inject
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

@Plugin(
    id = "chatforward", name = "ChatForward", version = BuildConstants.VERSION, authors = ["Esuny"]
)
class ChatForward @Inject constructor(
    private val proxyServer: ProxyServer,
    private val logger: Logger
) {
    private lateinit var configManager: ConfigManager
    private lateinit var pluginConfig: PluginConfig
    private lateinit var webSocketService: WebSocketService
    private lateinit var messageHandler: MessageHandler

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        logger.info("ChatForward 插件初始化中...")

        try {
            // 初始化配置管理器
            configManager = ConfigManager(proxyServer, logger)

            // 加载配置
            pluginConfig = configManager.loadConfig()

            // 验证配置
            val errors = configManager.validateConfig(pluginConfig)
            if (errors.isNotEmpty()) {
                errors.forEach { error -> logger.error("配置错误: $error") }
                logger.error("配置验证失败，插件将不会启动")
                return
            }

            // 初始化WebSocket服务
            webSocketService = WebSocketService(pluginConfig.websocket)

            // 初始化消息处理器
            messageHandler = MessageHandler(proxyServer, pluginConfig, webSocketService)
            webSocketService.setMessageHandler { message -> messageHandler.handleMessage(message) }

            // 连接WebSocket服务器
            val connected = webSocketService.connect()
            if (connected) {
                logger.info("ChatForward 插件启动成功")
                logger.debug("WebSocket服务器: ${pluginConfig.websocket.url}")
                logger.debug("监控所有服务器")
            } else {
                logger.error("WebSocket连接失败，插件功能受限")
            }

            // 注册事件监听器
            registerListeners()

            // 注册命令
            registerCommands()

        } catch (e: Exception) {
            logger.error("插件初始化失败", e)
        }
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        logger.info("ChatForward 插件关闭中...")

        try {
            // 关闭WebSocket连接
            webSocketService.shutdown()
            logger.info("WebSocket连接已关闭")
        } catch (e: Exception) {
            logger.error("关闭插件时发生错误", e)
        }

        logger.info("ChatForward 插件已关闭")
    }

    /**
     * 注册事件监听器
     */
    private fun registerListeners() {
        try {
            // 首先取消注册所有已注册的监听器
            proxyServer.eventManager.unregisterListeners(this)

            // 创建监听器实例
            val playerChatListener = PlayerChatListener(webSocketService, pluginConfig)
            val playerJoinListener = PlayerJoinListener(webSocketService)
            val playerLeaveListener = PlayerLeaveListener(webSocketService)
            val playerSwitchServerListener = PlayerSwitchServerListener(webSocketService)

            // 注册监听器到事件管理器
            proxyServer.eventManager.register(this, playerChatListener)
            proxyServer.eventManager.register(this, playerJoinListener)
            proxyServer.eventManager.register(this, playerLeaveListener)
            proxyServer.eventManager.register(this, playerSwitchServerListener)

            logger.debug("事件监听器注册完成")
            logger.debug("已注册监听器: 玩家聊天、玩家加入、玩家离开、玩家切换服务器")
        } catch (e: Exception) {
            logger.error("注册事件监听器失败", e)
        }
    }

    /**
     * 注册命令
     */
    private fun registerCommands() {
        try {
            // 创建reload命令
            val reloadCommand = ReloadCommand(this)

            // 获取命令管理器并注册命令
            val commandManager = proxyServer.commandManager
            commandManager.register("chatforward", reloadCommand, "cf")

            logger.debug("命令注册完成")
            logger.debug("已注册命令: /chatforward reload (别名: /cf)")
        } catch (e: Exception) {
            logger.error("注册命令失败", e)
        }
    }

    /**
     * 获取WebSocket服务状态
     */
    fun getWebSocketStatus(): String {
        return if (::webSocketService.isInitialized) {
            if (webSocketService.isConnected()) "已连接" else "未连接"
        } else {
            "未初始化"
        }
    }

    /**
     * 重新加载配置
     */
    fun reloadConfig(): Boolean {
        return try {
            val oldConfig = pluginConfig
            pluginConfig = configManager.reloadConfig()

            val errors = configManager.validateConfig(pluginConfig)
            if (errors.isNotEmpty()) {
                errors.forEach { error -> logger.error("配置错误: $error") }
                pluginConfig = oldConfig // 恢复旧配置
                return false
            }

            // 重新初始化WebSocket服务
            webSocketService.shutdown()
            webSocketService = WebSocketService(pluginConfig.websocket)

            // 先创建新的消息处理器
            messageHandler = MessageHandler(proxyServer, pluginConfig, webSocketService)
            webSocketService.setMessageHandler { message -> messageHandler.handleMessage(message) }

            val connected = webSocketService.connect()
            if (!connected) {
                logger.warn("重新连接WebSocket服务器失败")
            }

            // 重新注册事件监听器（因为PlayerChatListener使用了配置）
            registerListeners()

            logger.info("配置重新加载成功")
            true
        } catch (e: Exception) {
            logger.error("重新加载配置失败", e)
            false
        }
    }
}
