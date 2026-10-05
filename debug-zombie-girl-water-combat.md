# Debug Session: zombie-girl-water-combat
- **Status**: [OPEN]
- **Issue**: 修复丧尸娘材质丢失，并让其在水上不减速、主动下潜攻击溺尸直到击杀目标。
- **Debug Server**: Pending
- **Log File**: .dbg/trae-debug-log-zombie-girl-water-combat.ndjson

## Reproduction Steps
1. 启动 Minecraft Forge 1.20.1 模组环境。
2. 生成或遇到丧尸娘，观察实体材质。
3. 将丧尸娘引入水面并观察移动速度。
4. 在水中生成溺尸，观察丧尸娘是否下潜、持续攻击并完成击杀。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | 丧尸娘实体渲染器或模型注册缺失导致材质回退 | High | Low | Pending |
| B | 纹理资源路径、文件名或资源命名空间不一致 | High | Low | Pending |
| C | 水中移动控制未覆盖水面和下潜状态 | High | Med | Pending |
| D | 目标选择未将溺尸作为持续优先目标 | High | Med | Pending |
| E | 导航或攻击目标切换导致击杀前中断 | Med | Med | Pending |

## Log Evidence
未能在本轮自动化启动中进入可交互世界，因此没有采集到实体运行时日志。静态资源核对确认原代码引用的 `textures/entity/zombie_girl/` 子目录不存在，而四个 PNG 实际位于 `textures/entity/`；实体行为代码确认 `FloatGoal` 优先级会压制水下追击。

## Verification Conclusion
修复后已通过 `gradlew compileJava`。需要在游戏内生成丧尸娘并让其接触水域与溺尸完成最终行为确认。
