package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 归乡铃（home_bell）的后端：持续记录每只已驯服少女的最后位置，
 * 摇铃时把主人名下所有少女——包括躺在<b>未加载区块</b>里的——
 * 强制加载对应区块后拉回到主人身边。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>每只已驯服少女每 {@value #TRACK_INTERVAL} tick 把「维度 + 坐标 + 主人」
 *       写进主世界 SavedData，位置精确到区块即可（强制加载以区块为单位）；</li>
 *   <li>召唤时对记录中的区块执行同步强制加载
 *       （{@code getChunk(x, z, FULL, true)}），实体随区块一并加载后即可传送；</li>
 *   <li>记录失效（实体已死 / 已被星尘收容）的条目在召唤时自动清理；</li>
 *   <li>已加载实体再扫一遍兜底，刚驯服还没来得及记录的个体也能被找到；</li>
 *   <li>只召回与玩家<b>同维度</b>的少女。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GirlSummonHandler {
    /** SavedData 存档文件名（data/enchantment_expansion_girl_summon.dat）。 */
    private static final String DATA_NAME = "enchantment_expansion_girl_summon";
    /** 位置记录间隔（2 秒），区块粒度下足够精确。 */
    private static final int TRACK_INTERVAL = 40;
    /** 传送落点随机尝试次数（与丧尸娘 teleportToOwner 同款）。 */
    private static final int TELEPORT_ATTEMPTS = 16;

    private GirlSummonHandler() {
    }

    /** 定期把已驯服少女的位置写入 SavedData（幸存者少女是 ZombieGirlEntity 子类，自动覆盖）。 */
    @SubscribeEvent
    public static void onGirlTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity.tickCount % TRACK_INTERVAL != 0
                || !(entity instanceof FriendlyGirlInventory girl)) {
            return;
        }
        UUID owner = ownerUuidOf(entity);
        if (!girl.isTamed() || owner == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        getData(level.getServer()).track(
                entity.getUUID(), owner, level.dimension(), entity.blockPosition());
    }

    /** 从具体实体类读主人 UUID（接口未暴露该字段，幸存者走丧尸娘分支）。 */
    @Nullable
    private static UUID ownerUuidOf(LivingEntity entity) {
        if (entity instanceof ZombieGirlEntity zombie) {
            return zombie.getOwnerUuid();
        }
        if (entity instanceof DrownedGirlEntity drowned) {
            return drowned.getOwnerUuid();
        }
        return null;
    }

    /**
     * 召唤玩家名下所有同维度少女到身边，返回成功召回的数量。
     * 先按记录强制加载未加载区块召回，再扫已加载实体兜底。
     */
    public static int summonAll(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        SummonData data = getData(server);
        Set<UUID> summoned = new HashSet<>();
        List<UUID> stale = new ArrayList<>();

        // 1) 按位置记录召回：包含未加载区块中的个体
        for (Map.Entry<UUID, SummonData.Entry> entry : new HashMap<>(data.entries).entrySet()) {
            SummonData.Entry info = entry.getValue();
            if (!info.owner().equals(player.getUUID()) || info.dim() != level.dimension()) {
                continue;
            }
            // 同步强制加载目标区块，其中的实体随区块一并进入世界
            level.getChunk(entry.getValue().pos().getX() >> 4, entry.getValue().pos().getZ() >> 4,
                    ChunkStatus.FULL, true);
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                // 区块加载后仍找不到：已死亡 / 被星尘收容，清理失效记录
                stale.add(entry.getKey());
                continue;
            }
            if (trySummon(entity, player)) {
                summoned.add(entity.getUUID());
            }
        }
        stale.forEach(data::remove);

        // 2) 兜底：已加载但尚未被记录的个体（例如 2 秒记录窗口内刚驯服的）
        List<Entity> loaded = new ArrayList<>();
        level.getAllEntities().forEach(loaded::add);
        for (Entity entity : loaded) {
            if (!summoned.contains(entity.getUUID()) && trySummon(entity, player)) {
                summoned.add(entity.getUUID());
            }
        }
        return summoned.size();
    }

    /** 校验并传送单只少女；不是玩家名下的驯服个体则返回 false。 */
    private static boolean trySummon(Entity entity, ServerPlayer player) {
        if (!(entity instanceof Mob mob) || !(entity instanceof FriendlyGirlInventory girl)
                || !girl.isTamed() || !girl.isOwnedBy(player) || !mob.isAlive()) {
            return false;
        }
        // 睡着的先唤起，避免带着睡姿传送到床边之外悬空躺平
        if (mob.isSleeping()) {
            mob.stopSleeping();
        }
        mob.getNavigation().stop();
        mob.setTarget(null);
        Vec3 spot = findTeleportSpot(mob, player);
        mob.teleportTo(spot.x, spot.y, spot.z);
        mob.fallDistance = 0.0F;
        return true;
    }

    /**
     * 在主人附近找一个安全落点：16 次随机尝试（水平 ±3 格），
     * 要求脚下非实心、下方有支撑；找不到就传送到主人脚下。
     */
    private static Vec3 findTeleportSpot(Mob mob, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int dx = mob.getRandom().nextInt(7) - 3;
            int dz = mob.getRandom().nextInt(7) - 3;
            mutable.set(player.getBlockX() + dx, player.getBlockY(), player.getBlockZ() + dz);
            while (level.getBlockState(mutable).blocksMotion()
                    && mutable.getY() < level.getMaxBuildHeight()) {
                mutable.move(0, 1, 0);
            }
            if (!level.getBlockState(mutable).isSolid()
                    && !level.getBlockState(mutable.below()).isAir()) {
                x = mutable.getX() + 0.5D;
                y = mutable.getY();
                z = mutable.getZ() + 0.5D;
                break;
            }
        }
        return new Vec3(x, y, z);
    }

    private static SummonData getData(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(SummonData::load, SummonData::new, DATA_NAME);
    }

    /** 每只已驯服少女的最后已知位置（区块级精度即可），随存档持久化。 */
    private static final class SummonData extends SavedData {
        private final Map<UUID, Entry> entries = new HashMap<>();

        private record Entry(UUID owner, ResourceKey<Level> dim, BlockPos pos) {
        }

        void track(UUID id, UUID owner, ResourceKey<Level> dim, BlockPos pos) {
            Entry old = this.entries.get(id);
            if (old == null || !old.owner().equals(owner) || old.dim() != dim
                    || !old.pos().equals(pos)) {
                this.entries.put(id, new Entry(owner, dim, pos));
                this.setDirty();
            }
        }

        void remove(UUID id) {
            if (this.entries.remove(id) != null) {
                this.setDirty();
            }
        }

        private static SummonData load(CompoundTag tag) {
            SummonData data = new SummonData();
            ListTag list = tag.getList("Girls", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                data.entries.put(
                        entry.getUUID("Id"),
                        new Entry(entry.getUUID("Owner"),
                                ResourceKey.create(Registries.DIMENSION,
                                        new ResourceLocation(entry.getString("Dim"))),
                                BlockPos.of(entry.getLong("Pos"))));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Entry> entry : this.entries.entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putUUID("Id", entry.getKey());
                item.putUUID("Owner", entry.getValue().owner());
                item.putString("Dim", entry.getValue().dim().location().toString());
                item.putLong("Pos", entry.getValue().pos().asLong());
                list.add(item);
            }
            tag.put("Girls", list);
            return tag;
        }
    }
}
