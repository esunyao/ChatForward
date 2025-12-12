package cn.esuny.chatForward.util

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import org.slf4j.LoggerFactory

/**
 * JSON工具类（使用fastjson2）
 */
object JsonUtil {
    val logger = LoggerFactory.getLogger(JsonUtil::class.java)

    /**
     * 将对象转换为JSON字符串
     */
    fun toJsonString(obj: Any): String {
        return try {
            JSON.toJSONString(obj, JSONWriter.Feature.WriteMapNullValue)
        } catch (e: Exception) {
            logger.error("JSON序列化失败", e)
            "{}"
        }
    }

    /**
     * 将JSON字符串转换为对象
     */
    inline fun <reified T> parseObject(json: String): T? {
        return try {
            JSON.parseObject(json, T::class.java)
        } catch (e: Exception) {
            logger.error("JSON反序列化失败: $json", e)
            null
        }
    }

    /**
     * 将JSON字符串转换为JSONObject
     */
    fun parseObject(json: String): JSONObject? {
        return try {
            JSON.parseObject(json)
        } catch (e: Exception) {
            logger.error("JSON解析失败: $json", e)
            null
        }
    }

    /**
     * 将JSON字符串转换为列表
     */
    inline fun <reified T> parseArray(json: String): List<T> {
        return try {
            JSON.parseArray(json, T::class.java)
        } catch (e: Exception) {
            logger.error("JSON数组解析失败: $json", e)
            emptyList()
        }
    }

    /**
     * 构建玩家聊天事件JSON
     */
    fun buildPlayerChatEvent(player: String, message: String, server: String): String {
        val data = mapOf(
            "Mode" to "PlayerChatEvent",
            "player" to player,
            "message" to message,
            "server" to server
        )
        return toJsonString(data)
    }

    /**
     * 构建玩家离开事件JSON
     */
    fun buildPlayerLeftEvent(player: String): String {
        val data = mapOf(
            "Mode" to "PlayerLeftEvent",
            "player" to player
        )
        return toJsonString(data)
    }

    /**
     * 构建玩家加入事件JSON
     */
    fun buildPlayerJoinEvent(player: String, server: String): String {
        val data = mapOf(
            "Mode" to "PlayerJoinEvent",
            "player" to player,
            "server" to server
        )
        return toJsonString(data)
    }

    /**
     * 构建玩家切换服务器事件JSON
     */
    fun buildPlayerHandoffEvent(player: String, fromServer: String, toServer: String): String {
        val data = mapOf(
            "Mode" to "PlayerHandoffEvent",
            "player" to player,
            "fromserver" to fromServer,
            "toserver" to toServer
        )
        return toJsonString(data)
    }

    /**
     * 安全地获取JSON值
     */
    fun getString(json: JSONObject, key: String, defaultValue: String = ""): String {
        return json.getString(key) ?: defaultValue
    }

    /**
     * 安全地获取JSON整数值
     */
    fun getInt(json: JSONObject, key: String, defaultValue: Int = 0): Int {
        return json.getIntValue(key) ?: defaultValue
    }

    /**
     * 安全地获取JSON布尔值
     */
    fun getBoolean(json: JSONObject, key: String, defaultValue: Boolean = false): Boolean {
        return json.getBooleanValue(key) ?: defaultValue
    }

    /**
     * 检查JSON字符串是否有效
     */
    fun isValidJson(json: String): Boolean {
        return try {
            JSON.parseObject(json)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 美化JSON字符串（用于日志输出）
     */
    fun prettyJson(json: String): String {
        return try {
            val obj = JSON.parseObject(json)
            JSON.toJSONString(obj, JSONWriter.Feature.PrettyFormat)
        } catch (e: Exception) {
            json
        }
    }
}