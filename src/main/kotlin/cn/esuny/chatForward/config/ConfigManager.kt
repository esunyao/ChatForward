package cn.esuny.chatForward.config

import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 配置管理器
 */
class ConfigManager(
    private val proxyServer: ProxyServer,
    private val logger: Logger
) {
    private val pluginDataDir: Path
    private val configFile: File

    init {
        // 获取插件数据目录
        val plugin = proxyServer.pluginManager.getPlugin("chatforward")
            .orElseThrow { IllegalStateException("ChatForward 插件未找到") }

        val source = plugin.description.source
            .orElseThrow { IllegalStateException("无法获取插件源目录") }

        // source 是 Path 类型，获取其父目录
        val pluginJarDir = source.parent
            ?: throw IllegalStateException("无法获取插件JAR目录")

        // 插件数据目录：插件JAR所在目录/ChatForward
        pluginDataDir = pluginJarDir.resolve("ChatForward")
        configFile = pluginDataDir.toFile().resolve("config.yml")

        // 确保目录存在
        Files.createDirectories(pluginDataDir)
    }

    /**
     * 加载配置
     */
    fun loadConfig(): PluginConfig {
        return try {
            if (!configFile.exists()) {
                logger.info("配置文件不存在，创建默认配置: ${configFile.absolutePath}")
                createDefaultConfig()
            }

            val yaml = Yaml()
            FileInputStream(configFile).use { input ->
                yaml.loadAs(input, PluginConfig::class.java)
            }.also {
                logger.info("配置加载成功")
                logger.debug("WebSocket URL: ${it.websocket.url}")
                logger.debug("MCDR命令前缀: ${it.chat.mcdrCommandPrefix}")
                logger.debug("服务器映射数量: ${it.chat.serverPrefixMapping.size}")
            }
        } catch (e: Exception) {
            logger.error("加载配置文件失败: ${e.message}", e)
            logger.warn("使用默认配置")
            PluginConfig.default()
        }
    }

    /**
     * 保存配置
     */
    fun saveConfig(config: PluginConfig): Boolean {
        return try {
            val yaml = Yaml()
            val yamlString = yaml.dump(config)

            FileWriter(configFile).use { writer ->
                writer.write("# ChatForward 配置文件\n")
                writer.write("# 自动生成于 ${java.time.LocalDateTime.now()}\n\n")
                writer.write(yamlString)
            }

            logger.info("配置保存成功: ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("保存配置文件失败: ${e.message}", e)
            false
        }
    }

    /**
     * 重新加载配置
     */
    fun reloadConfig(): PluginConfig {
        logger.info("重新加载配置...")
        return loadConfig()
    }

    /**
     * 获取玩家个性设置文件路径
     */
    fun getPlayerPersonalityFile(): File {
        return pluginDataDir.resolve("player_personality.json").toFile()
    }

    /**
     * 获取插件数据目录
     */
    fun getPluginDataDir(): Path {
        return pluginDataDir
    }

    /**
     * 创建默认配置
     */
    private fun createDefaultConfig(): PluginConfig {
        val defaultConfig = PluginConfig.default()
        saveConfig(defaultConfig)
        return defaultConfig
    }

    /**
     * 检查配置是否有效
     */
    fun validateConfig(config: PluginConfig): List<String> {
        val errors = mutableListOf<String>()

        // 验证WebSocket配置
        if (config.websocket.url.isBlank()) {
            errors.add("WebSocket URL 不能为空")
        }
        if (config.websocket.token.isBlank()) {
            errors.add("WebSocket Token 不能为空")
        }
        if (config.websocket.reconnectInterval < 1000) {
            errors.add("重连间隔不能小于1000ms")
        }
        if (config.websocket.maxReconnectAttempts < 0) {
            errors.add("最大重连尝试次数不能为负数")
        }
        if (config.websocket.heartbeatInterval < 5000) {
            errors.add("心跳间隔不能小于5000ms")
        }
        if (config.websocket.heartbeatTimeout < 10000) {
            errors.add("心跳超时时间不能小于10000ms")
        }

        // 验证聊天配置
        if (config.chat.mainPrefix.isBlank()) {
            errors.add("主前缀不能为空")
        }
        if (config.chat.serverPrefixMapping.isEmpty()) {
            errors.add("服务器前缀映射不能为空")
        }

        // 验证存储配置
        if (config.storage.playerPersonalityFile.isBlank()) {
            errors.add("玩家个性设置文件名不能为空")
        }

        return errors
    }
}