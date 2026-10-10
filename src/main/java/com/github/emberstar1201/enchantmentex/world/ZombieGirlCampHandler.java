package com.github.emberstar1201.enchantmentex.world;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.ModEntities;
import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
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
 * <p>使用<b>确定性哈希</b>决定营地位置：世界种子 + 区块坐标 → 固定结果，
 * 使 {@code /ee locate camp} 指令能在未加载区块中直接预测营地位置。</p>
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

    /** 营地最小间距（方块）：2000 格 → 2000/16 = 125 个区块 */
    static final int CHUNK_SPACING = 125;
    /** 指令搜索半径（区块）：10000 格 → 625 区块 */
    static final int SEARCH_RADIUS_CHUNKS = 625;
    private static final Set<Long> ATTEMPTED_CHUNKS = new HashSet<>();

    /** 延迟执行队列：ChunkEvent.Load 只入队，ServerTickEvent 中出队执行。 */
    private static final Queue<PendingCamp> PENDING = new ArrayDeque<>();
    /** 每 tick 最多放置多少个营地，防止单 tick 产生过大开销。 */
    private static final int MAX_PER_TICK = 2;

    private ZombieGirlCampHandler() {
    }

    /** 待生成的营地数据。 */
    private record PendingCamp(ServerLevel level, ChunkPos chunkPos) {}

    // ==================== 确定性哈希 ====================

    /**
     * 确定性营地判定：用世界种子 + 区块坐标计算哈希，
     * 归一化后与概率配置比较。同一世界种子的营地分布固定不变。
     */
    static boolean shouldHaveCamp(long worldSeed, int chunkX, int chunkZ) {
        long hash = worldSeed;
        hash = hash * 128712L + chunkX;
        hash = hash * 987541L + chunkZ;
        // 取低 24 位归一化到 [0,1)，避免负数干扰
        double normalized = (double) (hash & 0xFFFFFFL) / (double) 0xFFFFFFL;
        return normalized < Config.zombieGirlCampSpawnChance;
    }

    // ==================== 群系检查 ====================

    /**
     * 检查指定位置是否在平原类群系中。
     * 在区块已加载时使用 {@link ServerLevel#getBiome} 查询。
     */
    private static boolean isPlainsBiome(ServerLevel level, BlockPos pos) {
        return isAllowedPlainsBiome(level.getBiome(pos));
    }

    static boolean isPlainsBiomeWithoutLoading(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getUncachedNoiseBiome(
                pos.getX() >> 2,
                pos.getY() >> 2,
                pos.getZ() >> 2
        );
        return isAllowedPlainsBiome(biome);
    }

    private static boolean isAllowedPlainsBiome(Holder<Biome> biomeHolder) {
        return biomeHolder.unwrapKey()
                .map(key -> key.location().toString())
                .filter(name -> name.equals("minecraft:plains")
                        || name.equals("minecraft:sunflower_plains")
                        || name.equals("minecraft:snowy_plains")
                        || name.equals("minecraft:ice_spikes"))
                .isPresent();
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
        if (Math.floorMod(chunkPos.x, CHUNK_SPACING) != 0
                || Math.floorMod(chunkPos.z, CHUNK_SPACING) != 0) {
            return;
        }

        long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        if (!ATTEMPTED_CHUNKS.add(chunkKey)) {
            return;
        }

        // 群系检查：仅在平原类群系生成营地（区块已加载，可安全调用 getBiome）
        BlockPos samplePos = new BlockPos(chunkPos.getMinBlockX() + 8, 64, chunkPos.getMinBlockZ() + 8);
        if (!isPlainsBiome(level, samplePos)) {
            return;
        }

        // 确定性概率门：替换 level.random，使营地位置可预测
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
        if (PENDING.isEmpty()) {
            return;
        }
        int processed = 0;
        while (!PENDING.isEmpty() && processed < MAX_PER_TICK) {
            PendingCamp camp = PENDING.poll();
            if (camp != null) {
                tryPlaceCamp(camp);
            }
            processed++;
        }
    }

    private static void tryPlaceCamp(PendingCamp camp) {
        ServerLevel level = camp.level();
        ChunkPos chunkPos = camp.chunkPos();

        Optional<StructureTemplate> optional = level.getStructureManager().get(CAMP_TEMPLATE);
        if (optional.isEmpty()) {
            return;
        }

        int x = chunkPos.getMinBlockX() + 8;
        int z = chunkPos.getMinBlockZ() + 8;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos origin = new BlockPos(x, y, z);
        StructureTemplate template = optional.get();
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false);
        RandomSource random = level.getRandom();
        if (!template.placeInWorld(level, origin, origin, settings, random, 2)) {
            return;
        }
        // 放置成功后记录营地原点
        ZombieGirlCampSavedData.get(level).addCamp(origin);

        Vec3i size = template.getSize();
        BlockPos end = origin.offset(size.getX(), size.getY(), size.getZ());
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

        // 如果结构模板中已有模组实体，直接设置营地变种；否则主动生成4个丧尸娘/溺尸娘
        if (girls.isEmpty()) {
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
        } else {
            for (int i = 0; i < girls.size() && i < 4; i++) {
                LivingEntity girl = girls.get(i);
                girl.getPersistentData().putInt("CampMaterialVariant", i);
                if (girl instanceof DrownedGirlEntity drownedGirl) {
                    drownedGirl.setCampVariant(i);
                } else if (girl instanceof ZombieGirlEntity zombieGirl) {
                    zombieGirl.setCampVariant(i);
                }
            }
        }
    }
}
