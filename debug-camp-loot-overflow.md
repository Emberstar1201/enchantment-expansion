# 丧尸娘营地箱子物品溢出调试记录

- sessionId: camp-loot-overflow
- PROJECT_ROOT: F:\MinecraftMods\enchantment-expansion
- DEBUG_EVIDENCE_DIR: C:\Users\18087\AppData\Local\Temp\enchantment-expansion\camp-loot-overflow
- status: in_progress

## 用户现象

营地箱子在重启后出现物品掉落到箱子外，同时箱子重新补充战利品。

## 假设

| ID | 假设 | 验证点 | 状态 |
|---|---|---|---|
| H1 | 结构模板自身带有预填充箱子，放置后再次设置战利品表导致重复物品 | 结构模板 NBT、放置前后容器物品与 loot table 状态 | INCONCLUSIVE |
| H2 | `setLootTable` 在容器已有物品或已生成战利品时被重复调用 | 运行时记录容器坐标、旧 loot table、是否已有物品、调用次数 | INCONCLUSIVE |
| H3 | 同一营地重启后仍被重复放置，SavedData 去重未覆盖实际路径 | 记录候选区块、SavedData 命中状态、结构放置次数 | INCONCLUSIVE |
| H4 | 结构扫描到的容器数量与结构实际容器数量不一致，造成重复填充或放置时容器卸载 | 记录结构范围、扫描容器数量、每个容器坐标 | INCONCLUSIVE |

## 证据

尚未采集运行时证据。

## 变更

调试阶段禁止修改业务逻辑；首个业务代码变更只能是结构化 instrumentation。
