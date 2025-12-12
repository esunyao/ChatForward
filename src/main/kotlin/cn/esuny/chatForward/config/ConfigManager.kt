package cn.esuny.chatForward.config

import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.DumperOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

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
                logger.info("配置文件不存在，从资源文件复制默认配置: ${configFile.absolutePath}")
                copyDefaultConfigFromResources()
            }

            // 保存当前线程的上下文类加载器
            val originalClassLoader = Thread.currentThread().contextClassLoader
            try {
                // 设置为插件类加载器，使SnakeYAML能找到插件类
                Thread.currentThread().contextClassLoader = PluginConfig::class.java.classLoader

                // 创建YAML实例，不添加类型标签
                val yaml = Yaml()
                FileInputStream(configFile).use { input ->
                    yaml.loadAs(input, PluginConfig::class.java)
                }.also {
                    logger.info("配置加载成功")
                    logger.debug("WebSocket URL: ${it.websocket.url}")
                    logger.debug("MCDR命令前缀: ${it.chat.mcdrCommandPrefix}")
                    logger.debug("服务器映射数量: ${it.chat.serverPrefixMapping.size}")
                }
            } finally {
                // 恢复原始类加载器
                Thread.currentThread().contextClassLoader = originalClassLoader
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
        // 保存当前线程的上下文类加载器
        val originalClassLoader = Thread.currentThread().contextClassLoader
        return try {
            // 设置为插件类加载器
            Thread.currentThread().contextClassLoader = PluginConfig::class.java.classLoader

            // 创建Representer，不输出类型标签
            val representer = Representer(DumperOptions())
            representer.propertyUtils.isSkipMissingProperties = true

            val yaml = Yaml(representer)
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
        } finally {
            // 恢复原始类加载器
            Thread.currentThread().contextClassLoader = originalClassLoader
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
     * 从资源文件复制默认配置
     */
    private fun copyDefaultConfigFromResources(): Boolean {
        return try {
            val resourceStream = javaClass.classLoader.getResourceAsStream("config.yml")
                ?: throw IllegalStateException("未找到资源文件 config.yml")

            Files.copy(resourceStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.info("已从资源文件复制默认配置: ${configFile.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("从资源文件复制配置失败: ${e.message}", e)
            false
        }
    }

    /**
     * 创建默认配置（兼容旧版本）
     */
    private fun createDefaultConfig(): PluginConfig {
        // 先尝试从资源文件复制
        if (copyDefaultConfigFromResources()) {
            // 加载复制的配置
            return loadConfig()
        }

        // 如果复制失败，使用代码生成的默认配置
        logger.warn("无法从资源文件复制配置，使用代码生成的默认配置")
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