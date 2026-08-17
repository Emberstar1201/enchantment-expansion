package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.entity.GlacialArrowEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 琉璃冰魄箭 附魔事件处理器
//
// 【职责】
//   1. 追踪玩家拉弓蓄力时间（PersistentData）
//   2. 在箭矢加入世界时替换为自定义琉璃冰魄箭实体
//   3. 二段蓄力时在玩家周围生成冰晶粒子提示
//
// 【蓄力追踪方案】
//   使用玩家 PersistentData 存储蓄力开始时刻（游戏 tick）：
//     - Start 事件：记录 GlacialArrow_ChargeStart = 当前游戏时间
//     - Tick 事件：计算已蓄力时间，二段蓄力时生成提示粒子
//     - EntityJoinLevelEvent：读取蓄力时间，判断阶段
//
// 【事件总线说明】
//   必须指定 bus = Bus.FORGE，因为 EntityJoinLevelEvent 和
//   LivingEntityUseItemEvent 都在 MinecraftForge.EVENT_BUS 上
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GlacialArrowHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // PersistentData 键名
    private static final String CHARGE_START_KEY = "GlacialArrow_ChargeStart";

    // ========================================================================
    // 【Step 1】开始拉弓 → 记录蓄力开始时间
    // ========================================================================
    @SubscribeEvent
    public static void onBowUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getItem().getItem() instanceof BowItem)) return;

        // 检查弓上是否有琉璃冰魄箭附魔
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.GLACIAL_ARROW.get(), event.getItem());
        if (enchantLevel <= 0) return;

        // 记录蓄力开始时刻（游戏 tick）
        long gameTime = player.level().getGameTime();
        player.getPersistentData().putLong(CHARGE_START_KEY, gameTime);
        LOGGER.debug("[GlacialArrow] 开始拉弓, gameTime={}, enchantLevel={}", gameTime, enchantLevel);
    }

    // ========================================================================
    // 【Step 2】蓄力中 → 二段蓄力提示粒子
    // ========================================================================
    @SubscribeEvent
    public static void onBowUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getItem().getItem() instanceof BowItem)) return;

        // 检查附魔
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.GLACIAL_ARROW.get(), event.getItem());
        if (enchantLevel <= 0) return;

        // 读取蓄力开始时间
        long startTime = player.getPersistentData().getLong(CHARGE_START_KEY);
        if (startTime <= 0) return;

        // 计算已蓄力 tick 数
        long chargeTicks = player.level().getGameTime() - startTime;
        int threshold = Config.glacialArrowChargeThreshold;

        // 二段蓄力提示：客户端生成冰晶粒子围绕弓身
        if (chargeTicks >= threshold && player.level().isClientSide()) {
            double x = player.getX();
            double y = player.getY() + 1.0; // 胸部高度
            double z = player.getZ();

            // 随机围绕玩家生成冰晶粒子
            for (int i = 0; i < 2; i++) {
                double offsetX = (player.getRandom().nextDouble() - 0.5) * 1.2;
                double offsetZ = (player.getRandom().nextDouble() - 0.5) * 1.2;
                double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.8;
                player.level().addParticle(ParticleTypes.SNOWFLAKE,
                        x + offsetX, y + offsetY, z + offsetZ,
                        0, 0, 0);
            }
        }
    }

    // ========================================================================
    // 【Step 3】箭矢加入世界 → 替换为琉璃冰魄箭
    //   ★ 仅在服务端执行（客户端箭矢由服务端同步）
    //   ★ 添加全方位调试日志追踪每个步骤
    // ========================================================================
    @SubscribeEvent
    public static void onArrowJoinWorld(EntityJoinLevelEvent event) {
        // 仅服务端处理（客户端箭矢由服务端同步，无需额外操作）
        if (event.getLevel().isClientSide()) return;

        // 仅处理普通箭矢实体
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        // 排除已经是琉璃冰魄箭的情况（母箭替换后、子箭生成时都会再次触发此事件）
        if (arrow instanceof GlacialArrowEntity) return;

        LOGGER.info("[GlacialArrow] EntityJoinLevelEvent 触发! 实体={}, 位置={}",
                arrow.getClass().getSimpleName(), arrow.position());

        // 获取发射者
        if (!(arrow.getOwner() instanceof Player player)) {
            LOGGER.debug("[GlacialArrow] 箭矢无 Player 发射者, owner={}, 跳过", arrow.getOwner());
            return;
        }

        LOGGER.info("[GlacialArrow] 箭矢发射者 = {}", player.getName().getString());

        // 检查玩家主手弓是否有附魔（弓射出后仍在主手）
        ItemStack bow = player.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) {
            // 兼容：尝试 useItem（部分场景下弓不在主手）
            bow = player.getUseItem();
            if (!(bow.getItem() instanceof BowItem)) {
                LOGGER.debug("[GlacialArrow] 玩家主手/useItem 都不是弓, 跳过");
                return;
            }
            LOGGER.debug("[GlacialArrow] 弓来自 useItem 而非主手");
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.GLACIAL_ARROW.get(), bow);
        if (enchantLevel <= 0) {
            LOGGER.debug("[GlacialArrow] 弓上无 glacial_arrow 附魔, 跳过");
            return;
        }

        LOGGER.info("[GlacialArrow] 附魔检测通过! enchantLevel={}", enchantLevel);

        // 读取蓄力时间（默认 0，需要 Start 事件先设置过）
        long startTime = player.getPersistentData().getLong(CHARGE_START_KEY);
        long gameTime = player.level().getGameTime();
        long chargeTicks = gameTime - startTime;
        if (startTime == 0) chargeTicks = 0; // 从未蓄力：归零为一段
        if (chargeTicks < 0) chargeTicks = 0;

        // 判断蓄力阶段
        int threshold = Config.glacialArrowChargeThreshold;
        boolean isStage2 = chargeTicks >= threshold;

        LOGGER.info("[GlacialArrow] 蓄力: startTime={}, gameTime={}, chargeTicks={}, threshold={}, isStage2={}",
                startTime, gameTime, chargeTicks, threshold, isStage2);

        // ========================================================================
        // 取消原版箭矢，替换为琉璃冰魄箭
        // ========================================================================
        event.setCanceled(true);
        LOGGER.info("[GlacialArrow] 原版箭矢已取消，开始创建 GlacialArrowEntity");

        // 获取原箭矢的数据
        Vec3 position = arrow.position();
        Vec3 motion = arrow.getDeltaMovement();

        // 创建自定义箭矢实体
        GlacialArrowEntity glacialArrow = new GlacialArrowEntity(
                arrow.level(), player,
                position.x, position.y, position.z);

        // 设置蓄力阶段
        if (isStage2) {
            glacialArrow.setChargeStage(2);
            double speedMultiplier = Config.glacialArrowStage2Speed;
            double damageMultiplier = Config.glacialArrowStage2Damage;
            glacialArrow.setDeltaMovement(motion.scale(speedMultiplier));
            glacialArrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);
            LOGGER.info("[GlacialArrow] 二段蓄力母箭: speedMult={}, dmgMult={}, baseDmg={}",
                    speedMultiplier, damageMultiplier, glacialArrow.getBaseDamage());
        } else {
            glacialArrow.setChargeStage(1);
            double speedMultiplier = Config.glacialArrowStage1Speed;
            double damageMultiplier = Config.glacialArrowStage1Damage;
            glacialArrow.setDeltaMovement(motion.scale(speedMultiplier));
            glacialArrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);
            LOGGER.info("[GlacialArrow] 一段蓄力母箭: speedMult={}, dmgMult={}", speedMultiplier, damageMultiplier);
        }

        // 设置母箭穿透次数
        int pierceCount = Config.glacialArrowPierceCount;
        glacialArrow.setMaxPierceCount(pierceCount);
        LOGGER.info("[GlacialArrow] 穿透次数={}", pierceCount);

        // 复制原箭矢的其他属性
        glacialArrow.setXRot(arrow.getXRot());
        glacialArrow.setYRot(arrow.getYRot());
        glacialArrow.setNoGravity(arrow.isNoGravity());
        glacialArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        // 加入世界
        boolean added = arrow.level().addFreshEntity(glacialArrow);
        LOGGER.info("[GlacialArrow] 母箭 addFreshEntity 结果={}, 位置={}, 速度={}",
                added, glacialArrow.position(), glacialArrow.getDeltaMovement());

        // 二段蓄力：箭矢射出时立即生成子箭（分裂）
        if (isStage2) {
            LOGGER.info("[GlacialArrow] ★★★ 开始调用 spawnSubArrowsNow() ★★★");
            LOGGER.info("[GlacialArrow] subCount配置值={}, owner={}",
                    Config.glacialArrowSubCount, glacialArrow.getOwner());

            glacialArrow.spawnSubArrowsNow();

            LOGGER.info("[GlacialArrow] spawnSubArrowsNow() 调用完成");
        } else {
            LOGGER.info("[GlacialArrow] 一段蓄力，不生成子箭");
        }

        // 清理蓄力数据
        player.getPersistentData().remove(CHARGE_START_KEY);
    }
}