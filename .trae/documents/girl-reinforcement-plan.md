# 少女阵亡增援机制实施计划

## 背景（Context）

用户需求：当一只野生或被驯服的少女（丧尸娘 / 溺尸娘 / 幸存者少女）被击杀时，在玩家周围生成约 4 只血量 50~100 不等、穿全套钻石盔甲 + 钻石剑的少女增援，它们优先攻击击杀少女的怪物。参考原版僵尸增援（Zombie reinforcement），但目的是帮玩家复仇/守护。

经代码探索确认，项目内已有全部可复用设施：

- [ZombieGirlEntity.java](file:///F:/MinecraftMods/enchantment-expansion/src/main/java/com/github/emberstar1201/enchantmentex/entity/ZombieGirlEntity.java) L1104 `bondTo(Player)`：直接建立驯服归属（tamed + ownerUuid + 持久化），不发消息。
- [FriendlyGirlPeacefulSpawnHandler.java](file:///F:/MinecraftMods/enchantment-expansion/src/main/java/com/github/emberstar1201/enchantmentex/entity/FriendlyGirlPeacefulSpawnHandler.java) L339-435：落点查找（高度图 + 排除水面）与生成校验（worldBorder + noCollision + finalizeSpawn + addFreshEntity）的完整参考。
- [MobBuffHandler.java](file:///F:/MinecraftMods/enchantment-expansion/src/main/java/com/github/emberstar1201/enchantmentex/MobBuffHandler.java) L429-442 `applyHealth`：MAX_HEALTH ADDITION 修饰符调血量模式（private，需在新类内自写同款助手，用自己的 modifier UUID）。
- [GirlCureHandler.java](file:///F:/MinecraftMods/enchantment-expansion/src/main/java/com/github/emberstar1201/enchantmentex/entity/GirlCureHandler.java)：`@Mod.EventBusSubscriber(Bus.FORGE)` 静态事件自注册模板，无需改主类。
- `ModEntities.ZOMBIE_GIRL / DROWNED_GIRL / SURVIVOR_GIRL`：`type.create(level)` 直接创建实体。
- 实体目标体系：targetSelector 0 受击仇恨、1 主人协作、2 天生仇恨敌对亡灵；`setTarget(killer)` 可直接指定复仇目标。

## 核心设计

新建 1 个处理器文件 + 修改 4 个语言文件，不改任何实体类与主类。

### 触发条件（LivingDeathEvent，仅服务端）

1. 死者：`instanceof ZombieGirlEntity || instanceof DrownedGirlEntity`（幸存者少女继承丧尸娘，自动覆盖）。
2. 死者持久数据 `getPersistentData().getBoolean("GirlReinforcement")` 为 true 时跳过——增援少女被杀不再连锁触发，防止刷怪放大。
3. 凶手 `damageSource.getEntity()`：必须是 `Monster` 实例；且排除玩家（本机制帮玩家，玩家击杀不触发）、排除少女自身（少女 extends Zombie/Drowned 也是 Monster，`instanceof FriendlyGirlInventory` 者跳过）。

### 玩家定位（增援锚点）

- 死者已驯服 → `getOwnerUuid()` → `server.getPlayerList().getPlayer(uuid)`，校验与死亡地点同维度。
- 野生 → `level.getNearestPlayer(...)` 半径 64 格。
- 找不到玩家则不生成。

### 冷却（防刷）

- 按玩家 UUID 冷却 1200 tick（60 秒），静态 `Map<UUID, Long>`；另加全局 gameTime 冷却，同类同 tick 多少女死亡只发一波。

### 生成流程（每只，共 4 只）

1. 随机三种类型之一（`ModEntities.ZOMBIE_GIRL/DROWNED_GIRL/SURVIVOR_GIRL` → `type.create(level)`）。
2. 落点：玩家周围 ±12 格随机，高度图 `MOTION_BLOCKING_NO_LEAVES` 取地表、排除水面（溺尸娘除外可落水）、`getWorldBorder().isWithinBounds` + `level.noCollision(type.getAABB(...))`；重试数次全败则跳过该只。**不走 `ForgeEventFactory.checkSpawnPosition`**——少女属 MONSTER 类别，SpawnPlacements 谓词要求黑暗光照，白天玩家基地会全部落点失败。
3. `moveTo` → `finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null, null)`（随机皮肤变种）→ **`setBaby(false)` + `stopRiding()`**（原版 `Zombie.finalizeSpawn` 会按难度随机幼年体甚至骑鸡，必须兜底）。
4. 装备：4 个盔甲槽 `Items.DIAMOND_HELMET/CHESTPLATE/LEGGINGS/BOOTS` + 主手 `Items.DIAMOND_SWORD`（`setItemSlot`），全部 `setDropChance(slot, 0.0F)` 防止刷钻石装备。
5. 血量：本地 `applyHealth` 副本（固定 UUID 的 MAX_HEALTH ADDITION 修饰符 + 回满血），50~100 随机。
6. `bondTo(player)`：增援驯服归属该玩家并持久化（不随距离消失）。
7. `setTarget(killer)`（killer 存活才设；凶手是亡灵时与天生仇恨目标一致；凶手死后回归普通目标选择）。
8. `level.addFreshEntity`。
9. 给锚点玩家发一条提示消息。

### 提示消息

新翻译键 `chat.enchantment_expansion.girl_reinforcement.spawned`，4 个 lang 文件各加一行（zh_cn / zh_tw / zh_hk / en_us），样式沿用现有前缀风格如 `§b【增援】§f...`。

## 文件变更清单

| 文件 | 变更 |
| --- | --- |
| `entity/GirlReinforcementHandler.java`（新建） | 事件监听 + 生成逻辑；顶部常量：`REINFORCE_COUNT=4`、`SPAWN_RADIUS=12`、`PLAYER_SEARCH_RADIUS=64`、`COOLDOWN_TICKS=1200`、`HP_MIN=50`、`HP_MAX=100`、`HEALTH_MODIFIER_UUID`、`TAG_REINFORCEMENT="GirlReinforcement"`；方法：`onLivingDeath`、`spawnReinforcement`、`findSpawnPos`、`applyHealth`（本地副本）、`sendNotice`；关键逻辑中文注释 |
| `lang/zh_cn.json` 等 4 个 | 各加 1 行增援提示翻译键 |

## 默认设定（可调整，实现前可提出）

- 玩家击杀少女 → 不触发增援（机制定位是帮玩家）。
- 增援驯服归属锚点玩家（永久、不消失、跟随主人体系）。
- 数量固定 4、血量 50~100 随机、冷却 60 秒，均为类顶部常量，暂不接入 ForgeConfigSpec 配置文件。
- 溺尸娘增援优先落水（玩家在水边时），落不到水则落地。

## 验证方式

1. `./gradlew.bat compileJava -q` 编译通过。
2. 场景 A（驯服线）：`/summon enchantment_expansion:zombie_girl` → 生肉驯服 → 拉一只僵尸杀掉她 → 确认主人身边生成 4 只成年、钻石全套、立即攻击该僵尸、聊天有提示。
3. 场景 B（野生 + 防连锁）：野生少女被杀 → 同样增援；再杀增援本身 → 不再连锁（标记 + 冷却生效）。
4. 场景 C：白天基地内触发，确认落点正常（验证未走黑暗谓词）；杀掉增援确认不掉落钻石装备。
