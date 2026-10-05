# Debug Session: world-save-gravity-freeze
- **Status**: [OPEN]
- **Issue**: 上次丧尸娘水下行为改写后，世界存档卡死，重力方块失效。
- **Log File**: .dbg/trae-debug-log-world-save-gravity-freeze.ndjson

## Hypotheses
| ID | Hypothesis | Evidence |
|----|------------|----------|
| A | runClient 进程或存档锁未释放，导致世界保存/加载卡住 | 部分确认：日志存在 `OverlappingFileLockException`，并有重复打开同一世界的记录；当前没有 Java 进程在运行 |
| B | 两栖导航或目标 AI 造成主线程寻路循环，导致 tick 卡死 | 排除为持续寻路循环：日志没有 `ServerHangWatchdog`；但两栖导航在实体构造时引发异常 |
| C | 丧尸娘加载时导航初始化异常，导致实体/区块保存失败 | 确认：日志第 228-233 行显示 `Unsupported mob type for DoorInteractGoal`，调用链为 `Zombie -> ZombieGirlEntity`，原因是两栖导航不满足门交互目标要求 |
| D | 重力方块失效是主线程卡顿的连带表现，不是独立方块回归 | 可能：可见日志有实体创建失败和后续服务端严重超时，但没有直接的重力方块事件证据 |

## Evidence
- `run/logs/latest.log:228-233`：刷怪物品包处理时生成丧尸娘触发 `IllegalArgumentException: Unsupported mob type for DoorInteractGoal`。
- `run/logs/latest.log:150-155`：本地连接因 `ConcurrentModificationException` 断开，并出现世界锁重叠。
- `run/logs/latest.log:281-313`：退出期间 mailbox rejected execution，随后报告约 185,147,630ms 的服务器延迟。
- 移除 `ZombieGirlEntity#createNavigation` 中的 `AmphibiousPathNavigation`，恢复 Zombie 默认地面导航；强制 `gradlew compileJava --rerun-tasks` 成功。
- 调试插桩仅在构造器成功后执行，无法观测此次构造期崩溃，已移除，避免污染业务代码。

## Conclusion
根因是两栖导航覆写与 Zombie 父类构造期创建的 `BreakDoorGoal` 不兼容，导致丧尸娘无法生成；世界锁重叠来自同时/重复打开同一存档，日志显示关服/线程异常连锁。已移除不兼容导航并强制编译成功。需要在关闭所有游戏实例后启动一次客户端，用临时世界验证丧尸娘生成和重力方块，再确认原存档可正常保存。
