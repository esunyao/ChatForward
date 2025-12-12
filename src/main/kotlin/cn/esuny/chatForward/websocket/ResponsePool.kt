package cn.esuny.chatForward.websocket

import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * WebSocket响应池
 * 用于处理异步请求-响应模式
 */
object ResponsePool {
    private val logger = LoggerFactory.getLogger(ResponsePool::class.java)
    private val responseMap = ConcurrentHashMap<String, CompletableFuture<Map<String, Any>>>()
    private val responseTimeMap = ConcurrentHashMap<String, Long>()

    /**
     * 等待响应
     * @param requestId 请求ID（echo字段）
     * @param timeoutSeconds 超时时间（秒），默认10秒
     * @return 响应数据
     */
    fun getResponse(requestId: String, timeoutSeconds: Long = 10): Map<String, Any> {
        val future = CompletableFuture<Map<String, Any>>()
        responseMap[requestId] = future
        responseTimeMap[requestId] = System.currentTimeMillis()

        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("等待响应超时: requestId=$requestId, timeout=${timeoutSeconds}s")
            responseMap.remove(requestId)
            responseTimeMap.remove(requestId)
            throw e
        } catch (e: Exception) {
            logger.error("获取响应失败: requestId=$requestId", e)
            responseMap.remove(requestId)
            responseTimeMap.remove(requestId)
            throw e
        }
    }

    /**
     * 设置响应
     * @param requestId 请求ID（echo字段）
     * @param response 响应数据
     */
    fun putResponse(requestId: String, response: Map<String, Any>) {
        val future = responseMap.remove(requestId)
        responseTimeMap.remove(requestId)
        
        if (future != null) {
            future.complete(response)
            logger.trace("响应已设置: requestId=$requestId")
        } else {
            logger.warn("未找到对应的请求: requestId=$requestId")
        }
    }

    /**
     * 清理超时的响应
     * @param timeoutSeconds 超时时间（秒）
     */
    fun cleanTimeoutResponses(timeoutSeconds: Long = 30) {
        val now = System.currentTimeMillis()
        val timeout = timeoutSeconds * 1000
        
        val toRemove = responseTimeMap.entries.filter { (_, time) ->
            now - time > timeout
        }.map { it.key }

        toRemove.forEach { requestId ->
            val future = responseMap.remove(requestId)
            responseTimeMap.remove(requestId)
            future?.cancel(true)
            logger.warn("清理超时响应: requestId=$requestId")
        }

        if (toRemove.isNotEmpty()) {
            logger.info("已清理 ${toRemove.size} 个超时响应")
        }
    }

    /**
     * 获取当前等待中的响应数量
     */
    fun getPendingCount(): Int {
        return responseMap.size
    }

    /**
     * 清除所有响应
     */
    fun clear() {
        responseMap.values.forEach { it.cancel(true) }
        responseMap.clear()
        responseTimeMap.clear()
        logger.info("已清除所有响应")
    }
}

