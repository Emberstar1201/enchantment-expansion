# 丧尸娘世界存档卡死调试记录

状态：[OPEN]
会话：zombie-girl-world-freeze

## 症状

游戏存档再次卡死，用户提供了 Trae 长文本日志文件路径。

## 待验证假设

1. 两栖导航在水下路径上反复重算，导致服务器主线程卡死。
2. 溺尸目标选择器与其他亡灵目标选择器反复抢占，造成 AI tick 超时。
3. `travel`/游泳状态与导航更新互相触发，造成高频实体更新。
4. 和平难度下保留丧尸娘后，原版僵尸 AI 在和平模式中持续运行并卡住主线程。
5. 卡死来自丧尸娘之外的全局事件或存档锁，当前改动只是同时暴露了问题。

## 证据

- 用户提供的 `_12_09_正在...txt` 是 `runClient --scan` 的启动/编译输出，不是卡死现场日志。
- 日志第 130 行开始启动客户端，至第 999 行仍处于资源和配置加载阶段。
- 当前片段没有 `ServerHangWatchdog`、`A single server tick took`、线程转储、存档保存异常或卡死堆栈。
- 当前源码仍在 `ZombieGirlEntity#createNavigation` 使用 `AmphibiousPathNavigation`，这是需要运行时证据验证的重点嫌疑。

## 当前结论

已获得直接运行时证据：生成丧尸娘时服务端在构造器阶段抛出 `IllegalArgumentException: Unsupported mob type for DoorInteractGoal`。

证据链：

- `run/logs/latest.log:128-158`：第一次使用刷怪蛋生成丧尸娘时，异常从 `DoorInteractGoal` -> `BreakDoorGoal` -> `Zombie.<init>` -> `ZombieGirlEntity.<init>` 抛出。
- `run/logs/latest.log:159-220`、`:221-251`：随后重复生成操作再次出现相同异常，说明每次创建该实体都会触发，而不是一次性存档错误。
- `run/logs/latest.log:252-268`、`:355-371`：服务器仍能执行保存区块并完成保存，当前日志没有证明 Anvil 存档损坏或重力方块逻辑异常。
- 当前源码的 `ZombieGirlEntity` 使用自定义导航覆盖；该导航无法满足 `DoorInteractGoal` 对 `GroundPathNavigation` 的要求，是当前已确认的触发条件。

### 假设验证

| ID | 结论 | 证据 |
|---|---|---|
| A | 已确认 | 生成阶段直接出现 `Unsupported mob type for DoorInteractGoal`，调用栈指向实体构造器。 |
| B | 未确认 | 日志没有 AI tick 超时或 watchdog 线程栈。 |
| C | 未确认 | 当前日志只显示构造阶段异常，没有运动递归或 tick 超时证据。 |
| D | 未确认 | 日志中的世界不是和平难度清除过程。 |
| E | 暂时排除 | 保存过程正常完成，未见全局方块事件异常。 |

## 最小修复方向

让 `Zombie` 构造阶段使用原版兼容的地面导航，实体初始化完成后再切换到水下兼容的导航；或者不覆盖构造阶段所依赖的导航。修复必须避免重新引入全局方块 / Tick 事件。

## 变更记录

- 已完成日志证据收集，尚未修改业务逻辑。
- 下一步保留必要调试点并实施最小修复。
