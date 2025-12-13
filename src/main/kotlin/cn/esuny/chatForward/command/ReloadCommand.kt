package cn.esuny.chatForward.command

import cn.esuny.chatForward.ChatForward
import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.LoggerFactory

/**
 * Reload命令处理器
 */
class ReloadCommand(private val plugin: ChatForward) : SimpleCommand {
    private val logger = LoggerFactory.getLogger(ReloadCommand::class.java)

    /**
     * 执行命令
     */
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val args = invocation.arguments()

        if (args.isNotEmpty() && args[0] == "reload") {
            try {
                // 重新加载配置
                val success = plugin.reloadConfig()

                if (success) {
                    source.sendMessage(
                        Component.text("ChatForward配置已重新加载!")
                            .color(NamedTextColor.GREEN)
                    )
                    logger.info("配置已通过命令重新加载")
                } else {
                    source.sendMessage(
                        Component.text("重新加载配置失败，请检查控制台日志!")
                            .color(NamedTextColor.RED)
                    )
                    logger.error("通过命令重新加载配置失败")
                }
            } catch (e: Exception) {
                source.sendMessage(
                    Component.text("重新加载配置时发生错误: ${e.message}")
                        .color(NamedTextColor.RED)
                )
                logger.error("重新加载配置时发生错误", e)
            }
        } else {
            source.sendMessage(
                Component.text("用法: /chatforward reload")
                    .color(NamedTextColor.YELLOW)
            )
        }
    }

    /**
     * 命令补全
     */
    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        val args = invocation.arguments()
        return when {
            args.isEmpty() -> listOf("reload")
            args.size == 1 && "reload".startsWith(args[0]) -> listOf("reload")
            else -> emptyList()
        }
    }

    /**
     * 检查是否有权限执行命令
     */
    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        // 默认所有操作员都可以执行
        return invocation.source().hasPermission("chatforward.reload") ||
               invocation.source().hasPermission("chatforward.admin")
    }
}