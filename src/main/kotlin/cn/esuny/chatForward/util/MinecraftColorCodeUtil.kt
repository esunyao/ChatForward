package cn.esuny.chatForward.util

import org.slf4j.LoggerFactory

/**
 * Minecraft颜色代码工具类
 */
object MinecraftColorCodeUtil {
    private val logger = LoggerFactory.getLogger(MinecraftColorCodeUtil::class.java)

    // Minecraft颜色代码映射
    private val colorCodeMap = mapOf(
        '0' to "black",        // §0
        '1' to "dark_blue",    // §1
        '2' to "dark_green",   // §2
        '3' to "dark_aqua",    // §3
        '4' to "dark_red",     // §4
        '5' to "dark_purple",  // §5
        '6' to "gold",         // §6
        '7' to "gray",         // §7
        '8' to "dark_gray",    // §8
        '9' to "blue",         // §9
        'a' to "green",        // §a
        'b' to "aqua",         // §b
        'c' to "red",          // §c
        'd' to "light_purple", // §d
        'e' to "yellow",       // §e
        'f' to "white"         // §f
    )

    // 格式代码映射
    private val formatCodeMap = mapOf(
        'k' to "obfuscated",   // §k
        'l' to "bold",         // §l
        'm' to "strikethrough",// §m
        'n' to "underline",    // §n
        'o' to "italic",       // §o
        'r' to "reset"         // §r
    )

    /**
     * 替换Minecraft颜色代码（兼容原有 replaceColorCode 方法）
     */
    fun replaceColorCode(input: String): String {
        return try {
            val result = StringBuilder()
            var i = 0
            while (i < input.length) {
                if (input[i] == '§' && i + 1 < input.length) {
                    val code = input[i + 1].lowercaseChar()
                    when {
                        colorCodeMap.containsKey(code) -> {
                            // 颜色代码，转换为HTML颜色或保留
                            result.append(convertColorCode(code))
                            i += 2
                        }
                        formatCodeMap.containsKey(code) -> {
                            // 格式代码，可以忽略或转换为HTML标签
                            result.append(convertFormatCode(code))
                            i += 2
                        }
                        else -> {
                            // 无效代码，保留原样
                            result.append(input[i])
                            i++
                        }
                    }
                } else {
                    result.append(input[i])
                    i++
                }
            }
            result.toString()
        } catch (e: Exception) {
            logger.error("处理颜色代码时发生错误: $input", e)
            input
        }
    }

    /**
     * 移除所有Minecraft颜色代码
     */
    fun stripColorCodes(input: String): String {
        return try {
            val result = StringBuilder()
            var i = 0
            while (i < input.length) {
                if (input[i] == '§' && i + 1 < input.length) {
                    // 跳过颜色代码
                    i += 2
                } else {
                    result.append(input[i])
                    i++
                }
            }
            result.toString()
        } catch (e: Exception) {
            logger.error("移除颜色代码时发生错误: $input", e)
            input
        }
    }

    /**
     * 检查字符串是否包含颜色代码
     */
    fun containsColorCodes(input: String): Boolean {
        return input.contains("§")
    }

    /**
     * 获取颜色代码数量
     */
    fun countColorCodes(input: String): Int {
        var count = 0
        var i = 0
        while (i < input.length) {
            if (input[i] == '§' && i + 1 < input.length) {
                val code = input[i + 1].lowercaseChar()
                if (colorCodeMap.containsKey(code) || formatCodeMap.containsKey(code)) {
                    count++
                }
                i += 2
            } else {
                i++
            }
        }
        return count
    }

    /**
     * 转换颜色代码为HTML颜色（可选）
     */
    private fun convertColorCode(code: Char): String {
        // 这里可以根据需要将颜色代码转换为HTML颜色
        // 例如：§a -> <span style="color: #55FF55"> 或保留原样
        // 目前先保留原样，以便在WebSocket消息中保持颜色信息
        return "§$code"
    }

    /**
     * 转换格式代码（可选）
     */
    private fun convertFormatCode(code: Char): String {
        // 这里可以根据需要将格式代码转换为HTML标签
        // 例如：§l -> <b> 或保留原样
        // 目前先保留原样
        return "§$code"
    }

    /**
     * 将颜色代码转换为友好的名称
     */
    fun getColorName(code: Char): String {
        return colorCodeMap[code.lowercaseChar()] ?: "unknown"
    }

    /**
     * 将格式代码转换为友好的名称
     */
    fun getFormatName(code: Char): String {
        return formatCodeMap[code.lowercaseChar()] ?: "unknown"
    }

    /**
     * 验证颜色代码是否有效
     */
    fun isValidColorCode(code: Char): Boolean {
        return colorCodeMap.containsKey(code.lowercaseChar())
    }

    /**
     * 验证格式代码是否有效
     */
    fun isValidFormatCode(code: Char): Boolean {
        return formatCodeMap.containsKey(code.lowercaseChar())
    }

    /**
     * 获取所有有效的颜色代码
     */
    fun getValidColorCodes(): List<Char> {
        return colorCodeMap.keys.toList()
    }

    /**
     * 获取所有有效的格式代码
     */
    fun getValidFormatCodes(): List<Char> {
        return formatCodeMap.keys.toList()
    }

    /**
     * 转义颜色代码（用于日志输出）
     */
    fun escapeColorCodes(input: String): String {
        return input.replace("§", "&sect;")
    }
}