# Debug Session: runclient-crash

状态：[OPEN]

## 症状
执行 `runClient` 后进入游戏时客户端直接崩溃退出，Gradle 报告 `Process ... java.exe finished with non-zero exit value -1`。

## 假设
1. Java 进程被系统或 JVM 强制终止，Gradle 未获得 Java 异常堆栈。
2. 模组初始化、事件注册或 Mixin 应用阶段存在异常，真实堆栈位于 `logs/latest.log`。
3. 内存、显存或原生库初始化失败导致客户端进程退出。
4. 最近的末影龙、劫掠兽或经验掉落相关代码触发客户端加载阶段问题。

## 证据记录
- `crash-2026-09-21_22.03.05-server.txt:7`：`NoClassDefFoundError: Could not initialize class MobBuffHandler`。
- `crash-2026-09-21_22.03.05-server.txt:28-31`：根因是 `Invalid UUID string: a1b2c3d4-1111-4a01-000000000006`，发生在 `MobBuffHandler.java:78`。
- `latest.log`：同一非法 UUID 导致 `ExceptionInInitializerError`，随后集成服务器停止，客户端退出。
- 该问题不是 `mod_version`、内存不足或 Mixin 应用失败。

## 修复
- 修正 `MobBuffHandler` 中洞穴蜘蛛、灾厄、幻翼和劫掠兽相关 UUID，补齐标准 UUID 的第四段，使所有 UUID 符合 `8-4-4-4-12` 格式且保持唯一。

## 验证
- `compileJava`：成功。
- 修复后执行 `runClient`：成功进入世界，出现玩家登录日志，未再出现 `ExceptionInInitializerError` 或 `NoClassDefFoundError`。

## 后续日志分析
- 新日志 `22_21_53_....txt` 显示失败发生在 `:compileJava`，不是进入游戏后的模组运行时崩溃。
- 关键错误：`Could not delete output file: build\\fg_cache\\...\\forge-1.20.1-47.4.10-binpatched.jar`。
- 同一日志还显示 Gradle Build Scan 询问交互选项，可能导致构建流程停留或文件句柄未及时释放。

## 处理
- 停止 2 个 Gradle Daemon。
- 删除被占用的单个生成缓存文件 `forge-1.20.1-47.4.10-binpatched.jar`。
- 使用 `compileJava --no-scan` 重新编译。

## 验证
- `compileJava --no-scan`：`BUILD SUCCESSFUL`。
- 22 个 API 弃用警告属于既有警告，不是本次失败原因。

## 状态
- [FIXED_PENDING_USER_CONFIRMATION]
