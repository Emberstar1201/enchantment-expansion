# 调试记录：runClient cancelled

状态：[OPEN]

## 问题现象

执行 Forge 1.20.1 项目的 `:runClient` 任务失败，Gradle 输出：

```text
Execution failed for task ':runClient'.
> Build cancelled while executing task ':runClient'
```

同时出现 Gradle Build Scan Terms of Use 未接受的提示。

## 当前假设

1. `runClient` 被用户或 IDE 终止，`Build cancelled` 是结果而非根因。
2. Gradle Build Scan 条款交互导致任务流程被中断。
3. Patchouli 资源或软前置配置在启动阶段触发异常，但当前输出被截断。
4. Java、Gradle 或 ForgeGradle 子进程启动/内存问题导致任务异常退出。
5. IDE 的运行配置主动取消了 Gradle 任务。

## 调试计划

1. 不修改业务逻辑，收集完整 `runClient` 输出。
2. 检查 Gradle 配置是否启用了 Build Scan，以及运行任务是否能独立完成。
3. 根据日志确认或排除假设。
4. 只有获得证据后再实施最小修复。
