# ChatForward - Minecraft Velocity 插件

ChatForward 是一个用于 Minecraft Velocity 代理服务器的插件，它可以将玩家事件（聊天、加入、离开、切换服务器）通过 WebSocket 实时转发到外部服务器，并支持从外部服务器接收广播指令。

## 功能特性

- **玩家事件转发**：实时转发玩家聊天、加入、离开、切换服务器事件到 WebSocket 服务器
- **MCDR 命令过滤**：自动跳过以配置的 MCDR 命令前缀开头的聊天消息
- **WebSocket 通信**：双向 WebSocket 通信，支持事件发送和指令接收
- **配置热重载**：支持通过 `/chatforward reload` 命令热重载配置
- **心跳检测**：自动 WebSocket 心跳检测和重连机制
- **服务器状态查询**：支持查询服务器状态和玩家列表

## 安装要求

- Velocity 3.4.0 或更高版本
- Java 17 或更高版本
- 外部 WebSocket 服务器（用于接收事件和发送指令）

## 快速开始

### 1. 构建插件

```bash
./gradlew shadowJar
```

构建完成后，插件 JAR 文件位于 `build/libs/` 目录下。

### 2. 安装插件

1. 将生成的 `ChatForward-1.0-SNAPSHOT-all.jar` 复制到 Velocity 服务器的 `plugins/` 目录
2. 重启 Velocity 服务器
3. 插件会自动在 `plugins/ChatForward/` 目录下生成默认配置文件

### 3. 配置插件

编辑 `plugins/ChatForward/config.yml` 文件：

```yaml
websocket:
  # WebSocket服务器地址
  url: "ws://localhost:8080/chat"

  # 身份验证Token
  token: "your_secret_token_here"

  # 重连间隔(毫秒)
  reconnectInterval: 5000

  # 最大重连尝试次数
  maxReconnectAttempts: 10

  # 心跳检测间隔(毫秒)
  heartbeatInterval: 30000

  # 心跳超时时间(毫秒)
  heartbeatTimeout: 60000

chat:
  # MCDR命令前缀列表（以这些前缀开头的聊天消息不会被转发）
  mcdrCommandPrefix:
    - "!!"
    - "##"
```

### 4. 启动插件

重启 Velocity 服务器或使用 `/chatforward reload` 命令加载配置。

## 使用说明

### 命令

- `/chatforward reload` 或 `/cf reload` - 重新加载插件配置

### 权限

- `chatforward.reload` - 允许使用重载命令
- `chatforward.admin` - 管理员权限（包含重载权限）

### WebSocket 通信协议

#### 发送到 WebSocket 服务器的事件

插件会发送以下 JSON 格式的事件到 WebSocket 服务器：

**玩家聊天事件**
```json
{
  "Mode": "PlayerChatEvent",
  "player": "玩家名",
  "message": "聊天内容",
  "server": "服务器名"
}
```

**玩家加入事件**
```json
{
  "Mode": "PlayerJoinEvent",
  "player": "玩家名",
  "server": "服务器名"
}
```

**玩家离开事件**
```json
{
  "Mode": "PlayerLeftEvent",
  "player": "玩家名"
}
```

**玩家切换服务器事件**
```json
{
  "Mode": "PlayerHandoffEvent",
  "player": "玩家名",
  "fromserver": "原服务器",
  "toserver": "目标服务器"
}
```

#### 从 WebSocket 服务器接收的指令

插件可以接收以下 JSON 格式的指令：

**广播消息**
```json
{
  "action": "broadcast",
  "message": "广播内容",
  "target": "all"  # 或 "specific_servers"
}
```

当 `target` 为 `"specific_servers"` 时，需要指定 `servers` 数组：
```json
{
  "action": "broadcast",
  "message": "广播内容",
  "target": "specific_servers",
  "servers": ["lobby", "survival"]
}
```

**查询玩家列表**
```json
{
  "action": "player_list",
  "server": "all",  # 或具体服务器名
  "echo": "请求ID"
}
```

响应格式：
```json
{
  "action": "player_list_response",
  "server": "all",
  "players": ["玩家1", "玩家2"],
  "count": 2,
  "echo": "请求ID"
}
```

**查询服务器状态**
```json
{
  "action": "server_status",
  "echo": "请求ID"
}
```

响应格式：
```json
{
  "action": "server_status_response",
  "status": {
    "total_players": 10,
    "servers": {
      "lobby": {
        "online": true,
        "player_count": 5,
        "players": ["玩家1", "玩家2"]
      }
    },
    "online": true,
    "timestamp": 1672502400000
  },
  "echo": "请求ID"
}
```

## 开发

### 项目结构

```
src/main/kotlin/cn/esuny/chatForward/
├── ChatForward.kt              # 主插件类
├── command/
│   └── ReloadCommand.kt       # 重载命令处理器
├── config/
│   ├── ConfigManager.kt       # 配置管理器
│   └── PluginConfig.kt        # 配置数据类
├── listeners/
│   ├── PlayerChatListener.kt  # 玩家聊天监听器
│   ├── PlayerJoinListener.kt  # 玩家加入监听器
│   ├── PlayerLeaveListener.kt # 玩家离开监听器
│   └── PlayerSwitchServerListener.kt # 玩家切换服务器监听器
└── websocket/
    ├── MessageHandler.kt      # WebSocket消息处理器
    ├── WebSocketService.kt    # WebSocket服务
    ├── WebSocketClient.kt     # WebSocket客户端
    └── ResponsePool.kt        # 响应池
```

### 构建

```bash
# 编译项目
./gradlew compileKotlin

# 构建带依赖的Shadow JAR
./gradlew shadowJar

# 运行测试
./gradlew test

# 清理构建
./gradlew clean
```

### 依赖

- Velocity API 3.4.0-SNAPSHOT
- Kotlin 标准库
- FastJSON2 - JSON 处理
- SnakeYAML - YAML 配置解析
- Java-WebSocket - WebSocket 客户端

## 故障排除

### 常见问题

1. **WebSocket 连接失败**
   - 检查 WebSocket 服务器地址是否正确
   - 确保 WebSocket 服务器正在运行
   - 检查防火墙设置

2. **事件未转发**
   - 检查玩家消息是否以 MCDR 命令前缀开头
   - 查看日志文件中的错误信息
   - 确认 WebSocket 连接状态

3. **配置重载失败**
   - 检查配置文件语法是否正确
   - 查看控制台错误日志

### 日志

插件日志位于 Velocity 日志文件中，可以通过以下命令查看：
```bash
# 查看实时日志
tail -f logs/latest.log
```

日志级别：
- `DEBUG`: 详细调试信息（事件转发、连接状态等）
- `INFO`: 重要操作信息（插件启动、配置重载等）
- `WARN`: 警告信息（连接失败、配置问题等）
- `ERROR`: 错误信息（异常、严重问题等）

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交 Issue 和 Pull Request。

## 联系方式

如有问题或建议，请通过以下方式联系：
- 提交 GitHub Issue
- 查看项目文档