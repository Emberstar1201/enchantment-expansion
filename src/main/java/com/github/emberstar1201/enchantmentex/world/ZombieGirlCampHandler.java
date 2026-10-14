package com.github.emberstar1201.enchantmentex.world;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.ModEntities;
import com.github.emberstar1201.enchantmentex.entity.SurvivorGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * 丧尸娘营地结构生成处理器。
 *
 * <p>使用<b>确定性哈希</b>决定营地候选区块；只有结构成功放置后才登记坐标，
 * {@code /ee locate camp} 因而只返回实际存在的营地，并可跨未加载区块查询。</p>
 *
 * <p><b>仅限平原类群系</b>：包括原版平原、向日葵平原、雪原、冰刺平原。</p>
 *
 * <p><b>关键设计：延迟执行。</b>结构放置在 {@link TickEvent.ServerTickEvent} 中执行，
 * 避免 {@link ChunkEvent.Load} 期间递归区块加载导致死锁。</p>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombieGirlCampHandler {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("enchantment_expansion-camp");
    private static final ResourceLocation CAMP_TEMPLATE =
            new ResourceLocation(EnchantmentExpansion.MODID, "zombie_girl_camp");
    private static final ResourceLocation CAMP_LOOT =
            new ResourceLocation(EnchantmentExpansion.MODID, "chests/zombie_girl_camp");
    private static final StructureProcessor IGNORE_STRUCTURE_BLOCKS = new StructureProcessor() {
        @Override
        public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader levelReader,
                BlockPos templatePos,
                BlockPos pos,
                StructureTemplate.StructureBlockInfo original,
                StructureTemplate.StructureBlockInfo processed,
                StructurePlaceSettings settings) {
            return processed.state().is(Blocks.STRUCTURE_BLOCK) ? null : processed;
        }

        @Override
        protected net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?> getType() {
            return net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.BLOCK_ROT;
        }
    };

    /** 区块候选间距；每个分区最多选中一个候选区块。 */
    static final int CHUNK_SPACING = 32;
    /** 指令搜索半径（区块）：10000 格 → 625 区块 */
    static final int SEARCH_RADIUS_CHUNKS = 625;
    private static final Set<Long> ATTEMPTED_CHUNKS = new HashSet<>();

    /** 延迟执行队列：ChunkEvent.Load 只入队，ServerTickEvent 中出队执行。 */
    private static final Queue<PendingCamp> PENDING = new ArrayDeque<>();
    private static final Queue<PlacingCamp> PLACING = new ArrayDeque<>();
    /** 每 tick 最多启动多少个营地，避免同时展开过多结构任务。 */
    private static final int MAX_PER_TICK = 1;
    /** 每个营地按高度切片；每个切片独占一个服务器 tick。 */
    private static final int SLICE_HEIGHT = 8;

    /** 新手保底营地：距出生点必须不足 100 格（取 96 留余量）。 */
    private static final int STARTER_CAMP_MAX_DISTANCE = 96;
    /** 新手保底营地：离出生点至少 12 格，避免压着出生平台。 */
    private static final int STARTER_CAMP_MIN_DISTANCE = 12;
    /** 新手保底营地：搜索前强制加载的区块半径（7 区块 = 112 格 ≥ 96 格）。 */
    private static final int STARTER_LOAD_RADIUS_CHUNKS = 7;
    /** 新手保底营地：每 tick 强制加载的区块数，摊平启动时的搜索成本。 */
    private static final int STARTER_CHUNKS_PER_TICK = 8;

    /** 出生点保底营地任务：启动后分 tick 加载区块，再搜索落点放置。 */
    private record StarterCampTask(ServerLevel level, BlockPos spawnPos,
                                   ArrayDeque<ChunkPos> toLoad) {}

    /** 进行中的保底营地任务；null 表示当前没有。 */
    private static StarterCampTask starterTask;

    private ZombieGirlCampHandler() {
    }

    /** 待生成的营地数据。 */
    private record PendingCamp(ServerLevel level, ChunkPos chunkPos) {}

    /** 正在跨 tick 放置的营地；{@code starter} 表示出生点保底营地。 */
    private record PlacingCamp(ServerLevel level, ChunkPos chunkPos, BlockPos origin,
                               Vec3i size, int nextSlice, int sliceCount, boolean starter) {}

    // ==================== 确定性哈希 ====================

    /** 每个 32×32 区块分区选定一个确定性的候选区块。 */
    private static boolean isCandidateChunk(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, CHUNK_SPACING);
        int regionZ = Math.floorDiv(chunkZ, CHUNK_SPACING);
        long regionHash = worldSeed;
        regionHash = regionHash * 128712L + regionX;
        regionHash = regionHash * 987541L + regionZ;
        int offsetX = (int) Math.floorMod(regionHash, CHUNK_SPACING);
        int offsetZ = (int) Math.floorMod(regionHash >>> 32, CHUNK_SPACING);
        return chunkX - regionX * CHUNK_SPACING == offsetX
                && chunkZ - regionZ * CHUNK_SPACING == offsetZ;
    }

    static boolean shouldHaveCamp(long worldSeed, int chunkX, int chunkZ) {
        if (!isCandidateChunk(worldSeed, chunkX, chunkZ)) {
            return false;
        }
        long hash = worldSeed ^ (long) chunkX * 128712L ^ (long) chunkZ * 987541L;
        double normalized = (double) (hash & 0xFFFFFFL) / (double) 0xFFFFFFL;
        return normalized < Config.zombieGirlCampSpawnChance;
    }

    // ==================== 群系检查 ====================

    private static boolean isAllowedCampBiome(ServerLevel level, BlockPos pos) {
        return isAllowedCampBiome(level.getBiome(pos));
    }

    static boolean isCampBiomeWithoutLoading(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getUncachedNoiseBiome(
                pos.getX() >> 2,
                pos.getY() >> 2,
                pos.getZ() >> 2
        );
        return isAllowedCampBiome(biome);
    }

    private static boolean isAllowedCampBiome(Holder<Biome> biomeHolder) {
        return biomeHolder.unwrapKey()
                .map(key -> {
                    String name = key.location().toString();
                    if (name.equals("minecraft:plains")
                            || name.equals("minecraft:sunflower_plains")
                            || name.equals("minecraft:snowy_plains")
                            || name.equals("minecraft:ice_spikes")) {
                        return true;
                    }
                    return name.startsWith("minecraft:")
                            && !name.contains("mangrove")
                            && (name.contains("forest")
                            || name.contains("taiga")
                            || name.contains("grove")
                            || name.contains("jungle"));
                })
                .orElse(false);
    }

    // ==================== 区块加载事件 ====================

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != ServerLevel.OVERWORLD
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        if (!isCandidateChunk(level.getSeed(), chunkPos.x, chunkPos.z)) {
            return;
        }

        // 出生点 96 格内是保底新手营地的专属区域，不参与普通生成，保证附近营地唯一。
        BlockPos spawnPos = level.getSharedSpawnPos();
        long dx = chunkPos.getMinBlockX() + 8 - spawnPos.getX();
        long dz = chunkPos.getMinBlockZ() + 8 - spawnPos.getZ();
        if (dx * dx + dz * dz
                < (long) STARTER_CAMP_MAX_DISTANCE * STARTER_CAMP_MAX_DISTANCE) {
            return;
        }

        long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        if (!ATTEMPTED_CHUNKS.add(chunkKey)) {
            return;
        }

        // 确定性概率门：每个分区先选一个候选区块，再按配置概率决定是否放置。
        if (!shouldHaveCamp(level.getSeed(), chunkPos.x, chunkPos.z)) {
            return;
        }

        // 入队延迟执行：不在此处调用 placeInWorld，避免递归区块加载导致死锁
        PENDING.add(new PendingCamp(level, chunkPos));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int started = 0;
        while (!PENDING.isEmpty() && started < MAX_PER_TICK) {
            PendingCamp camp = PENDING.poll();
            if (camp != null) {
                startCampPlacement(camp);
            }
            started++;
        }

        if (!PLACING.isEmpty()) {
            int active = PLACING.size();
            for (int i = 0; i < active; i++) {
                PlacingCamp camp = PLACING.poll();
                if (camp != null) {
                    continueCampPlacement(camp);
                }
            }
        }

        processStarterCampTask();
    }

    private static void startCampPlacement(PendingCamp camp) {
        ServerLevel level = camp.level();
        ChunkPos chunkPos = camp.chunkPos();
        Optional<StructureTemplate> optional = level.getStructureManager().get(CAMP_TEMPLATE);
        if (optional.isEmpty()) {
            return;
        }

        int x = chunkPos.getMinBlockX() + 8;
        int z = chunkPos.getMinBlockZ() + 8;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        if (surfaceY <= 60 || !isAllowedCampBiome(level, new BlockPos(x, surfaceY, z))) {
            return;
        }
        BlockPos origin = new BlockPos(x, surfaceY - 7, z);
        ZombieGirlCampSavedData savedData = ZombieGirlCampSavedData.get(level);
        if (savedData.hasGeneratedChunk(chunkPos)) {
            return;
        }
        BlockPos savedOrigin = savedData.getInProgress(chunkPos);
        if (savedOrigin != null) {
            origin = savedOrigin;
        } else {
            savedData.markInProgress(chunkPos, origin);
        }

        Vec3i size = optional.get().getSize();
        int sliceCount = Math.max(1, (size.getY() + SLICE_HEIGHT - 1) / SLICE_HEIGHT);
        PLACING.add(new PlacingCamp(level, chunkPos, origin, size, 0, sliceCount, false));
    }

    private static void continueCampPlacement(PlacingCamp camp) {
        ServerLevel level = camp.level();
        Optional<StructureTemplate> optional = level.getStructureManager().get(CAMP_TEMPLATE);
        if (optional.isEmpty()) {
            return;
        }

        StructureTemplate template = optional.get();
        int minY = camp.origin().getY() + camp.nextSlice() * SLICE_HEIGHT;
        int maxY = Math.min(camp.origin().getY() + camp.size().getY() - 1,
                minY + SLICE_HEIGHT - 1);
        BoundingBox slice = new BoundingBox(
                camp.origin().getX(), minY, camp.origin().getZ(),
                camp.origin().getX() + camp.size().getX() - 1, maxY,
                camp.origin().getZ() + camp.size().getZ() - 1);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setBoundingBox(slice)
                .addProcessor(IGNORE_STRUCTURE_BLOCKS);
        RandomSource random = level.getRandom();
        if (!template.placeInWorld(level, camp.origin(), camp.origin(), settings, random, 2)) {
            ZombieGirlCampSavedData.get(level).clearInProgress(camp.chunkPos());
            return;
        }

        if (camp.nextSlice() + 1 < camp.sliceCount()) {
            PLACING.add(new PlacingCamp(level, camp.chunkPos(), camp.origin(), camp.size(),
                    camp.nextSlice() + 1, camp.sliceCount(), camp.starter()));
            return;
        }

        finishCampPlacement(level, camp.chunkPos(), camp.origin(), camp.size(), random,
                camp.starter());
    }

    private static void finishCampPlacement(ServerLevel level, ChunkPos chunkPos, BlockPos origin,
                                            Vec3i size, RandomSource random, boolean starter) {
        ZombieGirlCampSavedData savedData = ZombieGirlCampSavedData.get(level);
        // 整体下移三格后，地基顶面约与地表齐平；营地坐标仍指向建筑内部可站立高度。
        savedData.addCamp(origin.above(7), chunkPos);
        if (starter) {
            // 保底新手营地放置完成，标记唯一性，之后服务器启动不再触发搜索。
            savedData.markStarterCampPlaced();
        }

        BlockPos end = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        BlockPos min = new BlockPos(Math.min(origin.getX(), end.getX()), Math.min(origin.getY(), end.getY()),
                Math.min(origin.getZ(), end.getZ()));
        BlockPos max = new BlockPos(Math.max(origin.getX(), end.getX()), Math.max(origin.getY(), end.getY()),
                Math.max(origin.getZ(), end.getZ()));

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                container.setLootTable(CAMP_LOOT, random.nextLong());
            }
        }

        net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(min, max);
        List<LivingEntity> girls = new ArrayList<>();
        girls.addAll(level.getEntitiesOfClass(ZombieGirlEntity.class, bounds, LivingEntity::isAlive));
        girls.addAll(level.getEntitiesOfClass(DrownedGirlEntity.class, bounds, LivingEntity::isAlive));

        // 模板自带的少女同样设置营地变种。
        for (int i = 0; i < girls.size() && i < 4; i++) {
            LivingEntity girl = girls.get(i);
            girl.getPersistentData().putInt("CampMaterialVariant", i);
            if (girl instanceof DrownedGirlEntity drownedGirl) {
                drownedGirl.setCampVariant(i);
            } else if (girl instanceof ZombieGirlEntity zombieGirl) {
                zombieGirl.setCampVariant(i);
            }
        }

        // 强制生成 4 只营地丧尸娘/溺尸娘（不依赖模板是否自带实体）。
        for (int i = 0; i < 4; i++) {
                BlockPos.MutableBlockPos spawnPos = new BlockPos.MutableBlockPos();
                boolean spawned = false;
                for (int attempt = 0; attempt < 10 && !spawned; attempt++) {
                    int sx = min.getX() + random.nextInt(Math.max(1, max.getX() - min.getX() + 1));
                    int sz = min.getZ() + random.nextInt(Math.max(1, max.getZ() - min.getZ() + 1));
                    int sy = min.getY() + random.nextInt(Math.max(1, max.getY() - min.getY() + 1));
                    spawnPos.set(sx, sy, sz);
                    if (level.noCollision(net.minecraft.world.entity.EntityType.ZOMBIE.getAABB(
                            sx + 0.5D, sy, sz + 0.5D))) {
                        Mob mob;
                        if (random.nextInt(3) == 0) {
                            mob = ModEntities.DROWNED_GIRL.get().create(level);
                        } else {
                            mob = ModEntities.ZOMBIE_GIRL.get().create(level);
                        }
                        if (mob != null) {
                            mob.moveTo(sx + 0.5D, sy, sz + 0.5D,
                                    random.nextFloat() * 360.0F, 0.0F);
                            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                                    MobSpawnType.STRUCTURE, null, null);
                            mob.getPersistentData().putInt("CampMaterialVariant", i);
                            if (mob instanceof DrownedGirlEntity drownedGirl) {
                                drownedGirl.setCampVariant(i);
                            } else if (mob instanceof ZombieGirlEntity zombieGirl) {
                                zombieGirl.setCampVariant(i);
                            }
                            level.addFreshEntity(mob);
                            spawned = true;
                        }
                    }
                }
            }

        // 大概率在营地外围刷新 1~2 只幸存者少女。
        trySpawnCampSurvivors(level, origin, size, random);
    }

    // ==================== 出生点保底新手营地 ====================

    /**
     * 服务器启动后触发：若存档尚未放置保底新手营地，
     * 建立分 tick 加载任务，在出生点周围搜索合适落点并放置唯一一个营地。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (level == null || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }
        if (ZombieGirlCampSavedData.get(level).isStarterCampPlaced()) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        ArrayDeque<ChunkPos> toLoad = new ArrayDeque<>();
        int centerX = spawn.getX() >> 4;
        int centerZ = spawn.getZ() >> 4;
        for (int cx = centerX - STARTER_LOAD_RADIUS_CHUNKS;
             cx <= centerX + STARTER_LOAD_RADIUS_CHUNKS; cx++) {
            for (int cz = centerZ - STARTER_LOAD_RADIUS_CHUNKS;
                 cz <= centerZ + STARTER_LOAD_RADIUS_CHUNKS; cz++) {
                toLoad.add(new ChunkPos(cx, cz));
            }
        }
        starterTask = new StarterCampTask(level, spawn.immutable(), toLoad);
    }

    /** 每 tick 强制加载少量搜索区块；全部就绪后搜索落点并启动放置。 */
    private static void processStarterCampTask() {
        if (starterTask == null) {
            return;
        }
        ServerLevel level = starterTask.level();
        ArrayDeque<ChunkPos> toLoad = starterTask.toLoad();
        for (int i = 0; i < STARTER_CHUNKS_PER_TICK && !toLoad.isEmpty(); i++) {
            ChunkPos chunkPos = toLoad.poll();
            // 显式加载到 FULL 状态；临时加载票据会在无人靠近后自动卸载。
            level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
        }
        if (!toLoad.isEmpty()) {
            return;
        }
        BlockPos best = findStarterCampPos(level, starterTask.spawnPos());
        starterTask = null;
        if (best != null) {
            startStarterCampPlacement(level, best);
        }
        // 找不到落点则不标记，下次服务器启动重试。
    }

    /**
     * 在出生点周围搜索保底营地落点，按优先级取离出生点最近的列：
     * 0 = Y&gt;70 且群系允许；1 = Y&gt;70；2 = 任意非水陆地（兜底）。
     *
     * @return 落点（Y 为高度图地表值）；找不到返回 null
     */
    private static BlockPos findStarterCampPos(ServerLevel level, BlockPos spawn) {
        ZombieGirlCampSavedData savedData = ZombieGirlCampSavedData.get(level);
        BlockPos best = null;
        int bestPriority = Integer.MAX_VALUE;
        long bestDistSq = Long.MAX_VALUE;
        long maxDistSq = (long) STARTER_CAMP_MAX_DISTANCE * STARTER_CAMP_MAX_DISTANCE;
        long minDistSq = (long) STARTER_CAMP_MIN_DISTANCE * STARTER_CAMP_MIN_DISTANCE;
        for (int x = spawn.getX() - STARTER_CAMP_MAX_DISTANCE;
             x <= spawn.getX() + STARTER_CAMP_MAX_DISTANCE; x++) {
            for (int z = spawn.getZ() - STARTER_CAMP_MAX_DISTANCE;
                 z <= spawn.getZ() + STARTER_CAMP_MAX_DISTANCE; z++) {
                long dx = x - spawn.getX();
                long dz = z - spawn.getZ();
                long distSq = dx * dx + dz * dz;
                if (distSq < minDistSq || distSq > maxDistSq) {
                    continue;
                }
                ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
                if (savedData.hasGeneratedChunk(chunkPos)
                        || savedData.getInProgress(chunkPos) != null) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                // 避开水面：脚位或脚下一格是水则跳过。
                if (level.getFluidState(feet).is(FluidTags.WATER)
                        || level.getFluidState(feet.below()).is(FluidTags.WATER)) {
                    continue;
                }
                int priority;
                if (y > 70 && isAllowedCampBiome(level, feet)) {
                    priority = 0;
                } else if (y > 70) {
                    priority = 1;
                } else if (y > 63) {
                    priority = 2;
                } else {
                    continue;
                }
                if (priority > bestPriority
                        || (priority == bestPriority && distSq >= bestDistSq)) {
                    continue;
                }
                bestPriority = priority;
                bestDistSq = distSq;
                best = feet;
            }
        }
        return best;
    }

    /** 在指定地表位置启动保底营地的分片放置，流程与普通营地一致。 */
    private static void startStarterCampPlacement(ServerLevel level, BlockPos surfacePos) {
        Optional<StructureTemplate> optional = level.getStructureManager().get(CAMP_TEMPLATE);
        if (optional.isEmpty()) {
            return;
        }
        int x = surfacePos.getX();
        int z = surfacePos.getZ();
        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        ZombieGirlCampSavedData savedData = ZombieGirlCampSavedData.get(level);
        if (savedData.hasGeneratedChunk(chunkPos)) {
            return;
        }
        BlockPos origin;
        BlockPos savedOrigin = savedData.getInProgress(chunkPos);
        if (savedOrigin != null) {
            // 崩溃重放：沿用已保存的原点，避免高度图变化导致错位。
            origin = savedOrigin;
        } else {
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            origin = new BlockPos(x, surfaceY - 7, z);
            savedData.markInProgress(chunkPos, origin);
        }
        Vec3i size = optional.get().getSize();
        int sliceCount = Math.max(1, (size.getY() + SLICE_HEIGHT - 1) / SLICE_HEIGHT);
        PLACING.add(new PlacingCamp(level, chunkPos, origin, size, 0, sliceCount, true));
    }

    /**
     * 大概率（80%）在营地外围 ±16 格刷新 1~2 只幸存者少女。
     * 她被营地丧尸娘攻击受伤后，增援机制会直接在她附近刷出武装少女帮忙。
     */
    private static void trySpawnCampSurvivors(ServerLevel level, BlockPos origin,
                                              Vec3i size, RandomSource random) {
        if (random.nextFloat() >= 0.8F) {
            return;
        }
        int count = 1 + random.nextInt(2);
        int centerX = origin.getX() + size.getX() / 2;
        int centerZ = origin.getZ() + size.getZ() / 2;
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = centerX + random.nextInt(33) - 16;
                int z = centerZ + random.nextInt(33) - 16;
                BlockPos pos = new BlockPos(x,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                // 避开水面，且落点必须能容纳实体。
                if (level.getFluidState(pos).is(FluidTags.WATER)
                        || level.getFluidState(pos.below()).is(FluidTags.WATER)
                        || !level.noCollision(ModEntities.SURVIVOR_GIRL.get().getAABB(
                        x + 0.5D, pos.getY(), z + 0.5D))) {
                    continue;
                }
                SurvivorGirlEntity survivor = ModEntities.SURVIVOR_GIRL.get().create(level);
                if (survivor == null) {
                    return;
                }
                survivor.moveTo(x + 0.5D, pos.getY(), z + 0.5D,
                        random.nextFloat() * 360.0F, 0.0F);
                survivor.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                        MobSpawnType.STRUCTURE, null, null);
                // 原版 finalizeSpawn 可能随机幼年体，强制成年兜底。
                survivor.setBaby(false);
                survivor.stopRiding();
                level.addFreshEntity(survivor);
                break;
            }
        }
    }
}
