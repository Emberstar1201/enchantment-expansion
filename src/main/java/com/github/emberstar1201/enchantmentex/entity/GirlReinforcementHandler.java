package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 少女阵亡增援（参考原版僵尸增援，但方向相反：用来帮玩家）。
 *
 * <p>当一只野生或被驯服的少女（丧尸娘 / 溺尸娘 / 幸存者少女）被「怪物」击杀时，
 * 在锚点玩家周围生成 4 只武装增援：随机三种类型之一、血量 50~100 随机、
 * 全套钻石盔甲 + 钻石剑，驯服归属锚点玩家，并优先攻击击杀少女的凶手。</p>
 *
 * <p>防刷设计：</p>
 * <ul>
 *   <li>增援自身被打上持久标记，被杀不再连锁触发下一波；</li>
 *   <li>按玩家 60 秒冷却 + 全局 60 秒冷却，同一时刻多只少女死亡只发一波；</li>
 *   <li>增援装备掉落概率全部置 0，不能靠击杀增援刷钻石装备；</li>
 *   <li>玩家本人击杀少女不触发（本机制定位是帮玩家，不是惩罚玩家）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GirlReinforcementHandler {

    /** 每次生成的增援数量。 */
    private static final int REINFORCE_COUNT = 4;
    /** 增援落点在锚点玩家周围的水平半径（格）。 */
    private static final int SPAWN_RADIUS = 12;
    /** 每只增援的落点尝试次数（全败则少生成一只，不影响其余增援）。 */
    private static final int SPAWN_ATTEMPTS = 12;
    /** 野生少女死亡时搜索锚点玩家的半径（格）；找不到玩家就不生成。 */
    private static final double PLAYER_SEARCH_RADIUS = 64.0D;
    /** 冷却时长（tick）：按玩家与全局各 60 秒。 */
    private static final int COOLDOWN_TICKS = 1200;
    /** 增援血量下限（含）。 */
    private static final double HP_MIN = 50.0D;
    /** 增援血量上限（含）。 */
    private static final double HP_MAX = 100.0D;
    /** 血量修饰符固定 UUID：只改 ADDITION 偏移，不破坏原版基础值，也不与其它模组冲突。 */
    private static final UUID HEALTH_MODIFIER_UUID =
            UUID.fromString("7c2f5b31-9d64-4a12-8b0e-6f4a91c2d5f8");
    private static final String HEALTH_MODIFIER_NAME = "girl_reinforcement_health";
    /**
     * 增援标记：写入实体持久数据（ForgeData，随存档保存）。
     * 带标记的少女死亡时不再触发下一波，防止「杀增援 → 再增援」的无限放大。
     */
    private static final String TAG_REINFORCEMENT = "GirlReinforcement";
    /** 增援到达提示的翻译键。 */
    private static final String KEY_SPAWNED = "chat.enchantment_expansion.girl_reinforcement.spawned";

    /** 按玩家的下次可触发时刻（level.getGameTime()，绝对时间）。 */
    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new HashMap<>();
    /** 全局下次可触发时刻：同 tick 多只少女死亡只发一波。 */
    private static long globalCooldownUntil = Long.MIN_VALUE;

    private GirlReinforcementHandler() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 仅服务端主世界逻辑处理；事件在 die() 内、实体移除前触发，此刻读凶手 / 生成实体都安全。
        if (!(event.getEntity().level() instanceof ServerLevel level) || level.isClientSide) {
            return;
        }
        // 死者必须是本模组三种少女之一（幸存者少女继承丧尸娘，instanceof 自动覆盖）。
        if (!(event.getEntity() instanceof ZombieGirlEntity)
                && !(event.getEntity() instanceof DrownedGirlEntity)) {
            return;
        }
        // 增援被杀不再连锁：带标记的直接放行。
        if (event.getEntity().getPersistentData().getBoolean(TAG_REINFORCEMENT)) {
            return;
        }
        // 凶手必须是怪物：玩家不是 Monster，天然不触发（帮玩家不惩罚玩家）；
        // 少女自身也继承自 Zombie/Drowned（Monster 子类），用 FriendlyGirlInventory 排除少女内战。
        if (!(event.getSource().getEntity() instanceof Monster killer)
                || killer instanceof FriendlyGirlInventory) {
            return;
        }
        // 锚点玩家：驯服个体找主人，野生个体找最近玩家；找不到就不生成。
        Player anchor = findAnchorPlayer(level, event.getEntity());
        if (anchor == null) {
            return;
        }

        long now = level.getGameTime();
        // 冷却先记后生成：同一 tick 内多只少女死亡时，第二只会被全局冷却挡住，只发一波。
        // 代价是全部落点失败时也消耗冷却，可接受（落点失败极罕见）。
        Long playerReady = PLAYER_COOLDOWNS.get(anchor.getUUID());
        if (playerReady != null && now < playerReady) {
            return;
        }
        if (now < globalCooldownUntil) {
            return;
        }
        PLAYER_COOLDOWNS.put(anchor.getUUID(), now + COOLDOWN_TICKS);
        globalCooldownUntil = now + COOLDOWN_TICKS;

        int spawned = 0;
        for (int i = 0; i < REINFORCE_COUNT; i++) {
            if (spawnReinforcement(level, anchor, killer)) {
                spawned++;
            }
        }
        if (spawned > 0) {
            anchor.displayClientMessage(Component.translatable(KEY_SPAWNED), false);
        }
    }

    /**
     * 找增援的锚点玩家：死者已驯服 → 主人（须在线、同维度、存活）；
     * 野生或主人不可用 → 死亡地点 64 格内最近玩家。找不到返回 null。
     */
    private static Player findAnchorPlayer(ServerLevel level, LivingEntity dead) {
        UUID ownerUuid = null;
        if (dead instanceof ZombieGirlEntity zombieGirl) {
            ownerUuid = zombieGirl.getOwnerUuid();
        } else if (dead instanceof DrownedGirlEntity drownedGirl) {
            ownerUuid = drownedGirl.getOwnerUuid();
        }
        if (ownerUuid != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null && owner.serverLevel() == level && owner.isAlive()) {
                return owner;
            }
        }
        return level.getNearestPlayer(dead.getX(), dead.getY(), dead.getZ(),
                PLAYER_SEARCH_RADIUS,
                candidate -> candidate.isAlive() && candidate.level() == level);
    }

    /**
     * 在锚点玩家周围生成一只增援少女。
     *
     * @return 是否成功生成
     */
    private static boolean spawnReinforcement(ServerLevel level, Player anchor, LivingEntity killer) {
        // 随机三种类型之一；三种少女都继承自 Zombie（溺尸娘 → Drowned → Zombie），
        // 统一按 Zombie 引用处理即可调用 setBaby 等公共方法。
        EntityType<? extends Zombie> type = switch (level.getRandom().nextInt(3)) {
            case 0 -> ModEntities.ZOMBIE_GIRL.get();
            case 1 -> ModEntities.DROWNED_GIRL.get();
            default -> ModEntities.SURVIVOR_GIRL.get();
        };
        BlockPos pos = findSpawnPos(level, anchor, type, type == ModEntities.DROWNED_GIRL.get());
        if (pos == null) {
            return false;
        }
        Zombie girl = type.create(level);
        if (girl == null) {
            return false;
        }
        girl.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        // MobSpawnType.EVENT：事件型生成，随机分配皮肤变种（少女各自的 finalizeSpawn）。
        girl.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        // 原版 Zombie.finalizeSpawn 会按难度概率随机幼年体（困难难度甚至生成鸡骑士），
        // 增援必须为成年：强制成人 + 下坐骑兜底。
        girl.setBaby(false);
        girl.stopRiding();

        // 全套钻石盔甲 + 钻石剑；掉落概率全部置 0，击杀增援刷不出钻石装备。
        girl.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        girl.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        girl.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        girl.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        girl.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR || slot.getType() == EquipmentSlot.Type.HAND) {
                girl.setDropChance(slot, 0.0F);
            }
        }

        // 血量 50~100 随机（MAX_HEALTH ADDITION 修饰符，与怪物强化同款做法）。
        RandomSource random = girl.getRandom();
        applyHealth(girl, HP_MIN + random.nextDouble() * (HP_MAX - HP_MIN));

        // 驯服归属锚点玩家（bondTo 内部会 setPersistenceRequired，不随距离消失）。
        if (girl instanceof ZombieGirlEntity zombieGirl) {
            zombieGirl.bondTo(anchor);
        } else if (girl instanceof DrownedGirlEntity drownedGirl) {
            drownedGirl.bondTo(anchor);
        }
        // 复仇：优先攻击凶手；凶手已死/失效则不设目标，回归少女普通的目标选择。
        if (killer.isAlive() && !killer.isRemoved()) {
            girl.setTarget(killer);
        }
        // 打上增援标记：被杀不再触发下一波。
        girl.getPersistentData().putBoolean(TAG_REINFORCEMENT, true);

        return level.addFreshEntity(girl);
    }

    /**
     * 找增援落点：玩家周围 ±12 格随机采样。
     * 陆地列取高度图顶端（排除水面），成功即用；溺尸娘额外尝试水面之下的水格。
     * 不走 ForgeEventFactory.checkSpawnPosition——少女属 MONSTER 类别，
     * SpawnPlacements 谓词要求黑暗光照，白天玩家基地会全部落点失败。
     *
     * @return 有效落点；重试全部失败返回 null（该只增援放弃）
     */
    private static BlockPos findSpawnPos(ServerLevel level, Player anchor,
                                         EntityType<? extends Zombie> type, boolean allowWater) {
        RandomSource random = level.getRandom();
        int baseX = Mth.floor(anchor.getX());
        int baseZ = Mth.floor(anchor.getZ());
        for (int i = 0; i < SPAWN_ATTEMPTS; i++) {
            int x = baseX + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;
            int z = baseZ + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;
            BlockPos ground = findGroundPos(level, x, z);
            if (ground != null && isPosValid(level, type, ground)) {
                return ground;
            }
            if (allowWater) {
                BlockPos water = findWaterPos(level, x, z);
                if (water != null && isPosValid(level, type, water)) {
                    return water;
                }
            }
        }
        return null;
    }

    /** 陆地落点：高度图顶端一格；地表是水面（河流/湖泊）时判为无效。 */
    private static BlockPos findGroundPos(ServerLevel level, int x, int z) {
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            return null;
        }
        return pos;
    }

    /** 水中落点：从水面向下找第一格「本格是水、脚下也是水」的位置（溺尸娘增援优先落水）。 */
    private static BlockPos findWaterPos(ServerLevel level, int x, int z) {
        int topY = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                level.getSeaLevel() + 1);
        int minY = level.getMinBuildHeight() + 1;
        for (int y = topY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            boolean isWater = level.getFluidState(pos).is(FluidTags.WATER);
            if (isWater && level.getFluidState(pos.below()).is(FluidTags.WATER)) {
                return pos;
            }
            // 空气（水面上方）继续向下；碰到非水固体说明该列不是水域，直接放弃。
            if (!isWater && !level.getBlockState(pos).isAir()) {
                return null;
            }
        }
        return null;
    }

    /** 落点最终校验：世界边界内 + 该位置无碰撞（增援模型能站进去）。 */
    private static boolean isPosValid(ServerLevel level, EntityType<? extends Zombie> type, BlockPos pos) {
        return level.getWorldBorder().isWithinBounds(pos)
                && level.noCollision(type.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
    }

    /**
     * 把增援最大血量调整到目标值（固定 UUID 的 ADDITION 修饰符 + 回满血）。
     * 与 {@code MobBuffHandler#applyHealth} 同款做法，本地独立实现避免改动既有类。
     */
    private static void applyHealth(Zombie girl, double targetHealth) {
        AttributeInstance maxHealth = girl.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.removeModifier(HEALTH_MODIFIER_UUID);
        double delta = targetHealth - maxHealth.getBaseValue();
        if (Math.abs(delta) > 0.001D) {
            maxHealth.addPermanentModifier(new AttributeModifier(
                    HEALTH_MODIFIER_UUID, HEALTH_MODIFIER_NAME, delta,
                    AttributeModifier.Operation.ADDITION));
        }
        girl.setHealth(girl.getMaxHealth());
    }
}
