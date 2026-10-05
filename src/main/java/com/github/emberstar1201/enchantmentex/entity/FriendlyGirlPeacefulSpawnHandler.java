package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 友好丧尸娘的和平难度自然生成处理器。
 *
 * <p>两种实体仍保留 {@link MobCategory#MONSTER}，以继续使用原版怪物容量、刷新距离和生成类别。
 * 处理器有两种模式（见 {@link Mode}）：和平难度下原版 MONSTER 刷怪循环整体关闭，
 * 由 PEACEFUL 模式高频补刷且个体持久化；简单/普通/困难难度下原版自然刷怪照常运行，
 * BOOST 模式每隔 20 秒绕过权重池与全局怪物上限直接补少量个体（不持久化），
 * 保证玩家在任意难度都能稳定遇到友好亡灵。两种模式都不会打开全局怪物开关，
 * 也不会让僵尸、骷髅等原版怪物在和平难度出现。</p>
 *
 * <p>生成流程刻意对齐原版 {@code NaturalSpawner.spawnCategoryForPosition} 已验证的做法：
 * 强制加载目标区块 → 高度图取点 → 单一的 Forge 位置判定（即 SpawnPlacements 注册的谓词）
 * → 无碰撞检查 → finalizeSpawn → addFreshEntity，避免多重前置判定互相矛盾导致永远失败。</p>
 *
 * <p>补刷出的个体一律 {@code setPersistenceRequired()}：友好亡灵不按怪物规则随机消失，
 * 否则玩家离开 32~128 格后她会被立刻 despawn，永远在视野之外生死循环；密度由每玩家
 * 64 格 8 只的局部上限控制。</p>
 */
public final class FriendlyGirlPeacefulSpawnHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("enchantment_expansion-friend-spawn");

    /**
     * 友好亡灵的两种定期补量模式。
     *
     * @param intervalTicks 两次补量的间隔（tick，20t=1秒）
     * @param minChunkDist  候选区块距玩家的最近区块数
     * @param maxChunkDist  候选区块距玩家的最远区块数
     * @param candidates    每周期尝试多少个不同方向的候选区块
     * @param localCap      玩家 64 格内两种友好实体的数量上限
     * @param persistent    补出的个体是否持久化（和平为 true；非和平为 false，按怪物规则自然消失）
     * @param label         日志中的模式名
     */
    private enum Mode {
        /** 和平难度：原版怪物循环整体关闭，只能靠本补量；高频、持久化、上限略高。 */
        PEACEFUL(200L, 2, 4, 4, 8, true, "和平补刷"),
        /**
         * 非和平难度的「定期补量」：原版自然刷怪照常运行，本模式每隔一段时间
         * 绕过刷怪权重池与全局怪物上限，直接在玩家周围补少量友好实体，
         * 解决权重竞争/玩家快速移动导致长期遇不到的问题；不持久化，玩家远离后正常消失。
         */
        BOOST(400L, 2, 3, 3, 6, false, "非和平补量");

        private final long intervalTicks;
        private final int minChunkDist;
        private final int maxChunkDist;
        private final int candidates;
        private final int localCap;
        private final boolean persistent;
        private final String label;

        Mode(long intervalTicks, int minChunkDist, int maxChunkDist, int candidates,
             int localCap, boolean persistent, String label) {
            this.intervalTicks = intervalTicks;
            this.minChunkDist = minChunkDist;
            this.maxChunkDist = maxChunkDist;
            this.candidates = candidates;
            this.localCap = localCap;
            this.persistent = persistent;
            this.label = label;
        }
    }

    private static final int LOCAL_CAP_RADIUS = 64;
    /** 每个候选区块内，每种实体的位置尝试次数（在整个 16×16 区块内随机选列）。 */
    private static final int GROUND_POSITION_ATTEMPTS = 6;
    private static final int WATER_POSITION_ATTEMPTS = 8;
    /**
     * 判定一个区块「是否含水域、是否高频河流群系」时的采样列数。
     * 平原中的河流往往只占区块内几格宽，单点采样（旧实现）几乎必判成陆地；
     * 10 列采样对宽度约 6 格（占比 ~37%）的河漏检率已降到 1% 左右。
     */
    private static final int CHUNK_WATER_SCAN_COLUMNS = 10;
    /** 世界出生点保护半径（24 格），与原版自然刷怪一致。 */
    private static final double WORLD_SPAWN_EXCLUSION_SQR = 576.0D;

    private FriendlyGirlPeacefulSpawnHandler() {
    }

    /**
     * 普通丧尸娘的地面生成规则。
     *
     * <p>非和平难度的夜晚、洞穴和阴影位置仍走原版怪物规则；和平难度与白天露天位置则使用友好实体规则。
     * 生物群系刷怪权重已与原版僵尸对齐（95）；白天露天额外经过 80% 概率门，
     * 使白天有效权重（约 76）略低于夜晚（95），昼夜两个时期都会出现。</p>
     */
    public static boolean checkZombieGirlSpawnRules(
            EntityType<ZombieGirlEntity> entityType,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random) {
        if (!Mob.checkMobSpawnRules(entityType, level, spawnType, pos, random)) {
            return false;
        }

        // 刷怪蛋、刷怪笼、命令等非自然来源不受昼夜和和平难度限制；
        // 是否允许仍由上方的碰撞、方块实体类型等基础位置规则把关。
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }

        // 海洋与河流群系不自然刷新丧尸娘。丧尸娘的群系权重（95）覆盖 is_overworld，
        // 而该标签包含海洋/河流；原版这些水域的怪物池几乎只有溺尸，一旦放入权重 95
        // 的丧尸娘（其谓词在夜间水下或白天透水光照下都可能放行），约九成刷怪名额会被
        // 海底丧尸娘占走，溺尸娘（权重 5）几乎永远轮不到。原版普通僵尸也不进水域群系，
        // 这里与原版保持一致；和平补刷器同样走本谓词，因此水域只会补出溺尸娘。
        if (level.getBiome(pos).is(BiomeTags.IS_OCEAN)
                || level.getBiome(pos).is(BiomeTags.IS_RIVER)) {
            return false;
        }

        // 非和平难度的常规黑暗刷新完全交回原版，避免改变夜晚僵尸类怪物的标准行为。
        if (level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && Monster.checkMonsterSpawnRules(entityType, level, spawnType, pos, random)) {
            return true;
        }

        // 和平难度下，夜晚和黑暗区域按正常概率生成。
        if (Monster.isDarkEnoughToSpawn(level, pos, random)) {
            return true;
        }

        // 白天露天位置：80% 概率放行（有效刷怪权重约 95×0.8≈76，
        // 比夜晚的满权重 95「低一点点」，既能白天常见又保持昼夜层次感）。
        return spawnType == MobSpawnType.NATURAL
                && level.getLevel().isDay()
                && level.canSeeSky(pos)
                && random.nextInt(10) < 8;
    }

    /**
     * 溺尸娘的水中生成规则。
     *
     * <p>非和平难度完全复刻原版溺尸：黑暗判定、河流 1/15、普通水域 1/40 且低于海平面 5 格。</p>
     *
     * <p>和平难度大幅放宽：作为友好亡灵，只要求脚部与脚下一格都是水（碰撞规则仍生效），
     * 不再要求黑暗、不再掷 1/15 与 1/40 概率门——因此白天阳光直射的浅水河面也会出现，
     * 配合和平补刷器让玩家在水域活动时能稳定遇到她们。</p>
     */
    public static boolean checkDrownedGirlSpawnRules(
            EntityType<DrownedGirlEntity> entityType,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random) {
        if (!level.getFluidState(pos.below()).is(FluidTags.WATER)
                || !Mob.checkMobSpawnRules(entityType, level, spawnType, pos, random)) {
            return false;
        }

        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }

        // 自然生成时实体脚部必须位于水中。
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }

        // 和平难度放宽：去掉亮度与原版概率门，白天浅水也可生成。
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return true;
        }

        // 非和平难度沿用原版溺尸规则。
        if (!Monster.isDarkEnoughToSpawn(level, pos, random)) {
            return false;
        }

        if (level.getBiome(pos).is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)) {
            // 河流等高频溺尸水域：沿用原版 1/15 概率。
            return random.nextInt(15) == 0;
        }

        // 普通海洋等水域：沿用原版 1/40 概率，并要求位于海平面以下至少 5 格。
        return random.nextInt(40) == 0
                && pos.getY() < level.getSeaLevel() - 5;
    }

    /**
     * 玩家进入世界时打印一行诊断日志：当前维度、难度、怪物生成规则，
     * 以及和平补刷器是否处于工作状态。用于一眼确认「是否真的切到了和平难度」
     * （菜单中「简单」紧邻「和平」，极易点错而不自知）。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)
                || serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        boolean mobSpawning = serverLevel.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        Mode mode = serverLevel.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
                ? Mode.PEACEFUL : Mode.BOOST;
        LOGGER.info("玩家 {} 进入主世界：难度={}，生物生成规则 doMobSpawning={}，友好亡灵定期补量={}（每{}秒一次）",
                player.getGameProfile().getName(),
                serverLevel.getDifficulty(),
                mobSpawning,
                mobSpawning ? mode.label + "启用" : "关闭",
                mode.intervalTicks / 20L);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.haveTime()) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD
                    || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                continue;
            }
            // 和平走 PEACEFUL 补刷；简单/普通/困难走 BOOST 定期补量，两种模式各自按间隔触发。
            Mode mode = level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
                    ? Mode.PEACEFUL : Mode.BOOST;
            if (level.getGameTime() % mode.intervalTicks != 0L) {
                continue;
            }

            for (ServerPlayer player : level.players()) {
                trySpawnNearPlayer(level, player, mode);
            }
        }
    }

    private static void trySpawnNearPlayer(ServerLevel level, ServerPlayer player, Mode mode) {
        RandomSource random = level.getRandom();

        // 局部容量：与玩家 64 格内已存在的友好实体总数（含两种模式补出的个体与和平持久个体）。
        AABB searchArea = player.getBoundingBox().inflate(LOCAL_CAP_RADIUS);
        int nearbyCount = level.getEntitiesOfClass(Mob.class, searchArea,
                mob -> mob instanceof ZombieGirlEntity || mob instanceof DrownedGirlEntity).size();
        if (nearbyCount >= mode.localCap) {
            return;
        }

        // 每个周期朝多个不同方向掷候选区块：单个区块若落在水域/峡谷等非法地形，
        // 本周期还有其它方向兜底，避免长时间零产出。成功补刷 1 只即结束本周期，
        // 密度交给各模式的 localCap 控制。
        for (int attempt = 0; attempt < mode.candidates; attempt++) {
            int chunkX = player.chunkPosition().x
                    + (random.nextBoolean() ? 1 : -1)
                    * (mode.minChunkDist + random.nextInt(mode.maxChunkDist - mode.minChunkDist + 1));
            int chunkZ = player.chunkPosition().z
                    + (random.nextBoolean() ? 1 : -1)
                    * (mode.minChunkDist + random.nextInt(mode.maxChunkDist - mode.minChunkDist + 1));
            if (trySpawnInChunk(level, new ChunkPos(chunkX, chunkZ), random, mode)) {
                return;
            }
        }
    }

    /**
     * 在指定区块内尝试补刷一只友好亡灵。
     *
     * @return 本区块是否成功补刷
     */
    private static boolean trySpawnInChunk(ServerLevel level, ChunkPos chunkPos, RandomSource random, Mode mode) {
        if (!level.isNaturalSpawningAllowed(chunkPos)
                || !level.getWorldBorder().isWithinBounds(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ())) {
            return false;
        }
        // 强制加载目标区块：不再依赖「区块恰好在缓存里」。低视距 / 低模拟距离时，
        // getChunkNow 可能长期返回 null 导致一次补刷都不发生（旧实现的核心隐患）。
        level.getChunk(chunkPos.x, chunkPos.z);

        // 在整个区块内随机采样多列，识别其中是否存在水域、以及水域是否属于
        // 「高频溺尸群系」（河流/冻河/滴水石洞穴）。河流是独立生物群系，
        // 即使只从平原中穿过一条几格宽的水道，水面列的群系仍是 minecraft:river；
        // 旧实现只看单个随机点，基点落在平原上就整区块按陆地处理，
        // 是「平原旁河流看不到溺尸娘」的直接原因。
        boolean foundWater = false;
        boolean highFrequencyWater = false;
        for (int i = 0; i < CHUNK_WATER_SCAN_COLUMNS; i++) {
            int sampleX = chunkPos.getMinBlockX() + random.nextInt(16);
            int sampleZ = chunkPos.getMinBlockZ() + random.nextInt(16);
            BlockPos waterColumn = findWaterPosition(level, sampleX, sampleZ);
            if (waterColumn != null) {
                foundWater = true;
                if (level.getBiome(waterColumn).is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)) {
                    highFrequencyWater = true;
                }
            }
        }

        // 种类权重与生物群系修饰保持一致：水域中溺尸娘必须占绝对优势
        // （丧尸娘谓词已拒绝海洋/河流，但先选中她会浪费一轮找点）：
        // 高频水域（河流等）500，普通水域（海洋等）200，丧尸娘 95；
        // 区块内完全没水时溺尸娘权重为 0，只刷丧尸娘。
        int drownedWeight = !foundWater ? 0 : highFrequencyWater ? 500 : 200;
        int zombieGirlWeight = 95;
        boolean preferDrowned = foundWater
                && random.nextInt(zombieGirlWeight + drownedWeight) < drownedWeight;

        // 优先权重选中的种类，失败再试另一种：位置判定含原版溺尸概率门（非和平），
        // 单点可能被拒，由同区块多次选列兜底。
        if (preferDrowned) {
            return trySpawnType(level, ModEntities.DROWNED_GIRL.get(), chunkPos, random, mode)
                    || trySpawnType(level, ModEntities.ZOMBIE_GIRL.get(), chunkPos, random, mode);
        }
        return trySpawnType(level, ModEntities.ZOMBIE_GIRL.get(), chunkPos, random, mode)
                || (foundWater && trySpawnType(level, ModEntities.DROWNED_GIRL.get(), chunkPos, random, mode));
    }

    private static boolean trySpawnType(
            ServerLevel level, EntityType<?> type, ChunkPos chunkPos, RandomSource random, Mode mode) {
        boolean drownedGirl = type == ModEntities.DROWNED_GIRL.get();
        if (!drownedGirl && type != ModEntities.ZOMBIE_GIRL.get()) {
            return false;
        }

        int attempts = drownedGirl ? WATER_POSITION_ATTEMPTS : GROUND_POSITION_ATTEMPTS;
        for (int i = 0; i < attempts; i++) {
            // 在整个 16×16 区块内随机选列，而不是围绕单一基点的 5×5 小窗：
            // 窄河在区块中只占几列，小窗几乎不可能恰好罩住河面。
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            BlockPos pos = drownedGirl
                    ? findWaterPosition(level, x, z)
                    : findGroundPosition(level, x, z);
            if (pos != null && spawnAt(level, type, pos, random, mode)) {
                return true;
            }
        }
        return false;
    }

    /** 地表位置：取高度图顶端空气格（脚下方块即地表）。 */
    private static BlockPos findGroundPosition(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        // 高度图顶端落在水面（河流 / 湖泊）时不是陆地位置，交给溺尸娘的尝试处理。
        if (level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            return null;
        }
        return pos;
    }

    /** 水中位置：从水面向下找到第一格「本格是水、脚下也是水」的位置。 */
    private static BlockPos findWaterPosition(ServerLevel level, int x, int z) {
        int topY = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), level.getSeaLevel() + 1);
        int minY = level.getMinBuildHeight() + 1;
        for (int y = topY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            boolean isWater = level.getFluidState(pos).is(FluidTags.WATER);
            if (isWater && level.getFluidState(pos.below()).is(FluidTags.WATER)) {
                return pos;
            }
            // 空气（水面上方）继续向下；一旦遇到非水固体方块说明该列不是水域，直接放弃。
            if (!isWater && !level.getBlockState(pos).isAir()) {
                return null;
            }
        }
        return null;
    }

    private static boolean spawnAt(
            ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource random, Mode mode) {
        if (pos.distSqr(level.getSharedSpawnPos()) <= WORLD_SPAWN_EXCLUSION_SQR
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.noCollision(type.getAABB(
                        pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))) {
            return false;
        }

        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return false;
        }

        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);

        // 唯一的位置判定入口：触发 Forge PositionCheck 事件后执行 SpawnPlacements 谓词，
        // 与原版自然刷怪完全同路（和平的放宽逻辑写在谓词里）。
        if (!ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
            mob.discard();
            return false;
        }

        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.NATURAL, null, null);
        // 和平模式补出的个体设为「持久化」，不参与原版怪物的随机/远距离消失：
        // 否则她在玩家 32~128 格之间会被定时 despawn，玩家走开/飞远后个体立刻消失，
        // 表现就是「从来没见到过」。非和平 BOOST 模式不持久化，玩家远离后按怪物规则
        // 自然消失，避免世界里塞满不可清理的怪物；数量仍受各模式 localCap 约束。
        if (mode.persistent) {
            mob.setPersistenceRequired();
        }
        if (!level.addFreshEntity(mob)) {
            mob.discard();
            return false;
        }

        LOGGER.info("{}友好亡灵：{} 于 [{}, {}, {}]",
                mode.label, type.getDescriptionId(), pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
}
