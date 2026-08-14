package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.entity.GlacialArrowEntity;
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
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class GlacialArrowHandler {

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
        player.getPersistentData().putLong(CHARGE_START_KEY, player.level().getGameTime());
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
    // ========================================================================
    @SubscribeEvent
    public static void onArrowJoinWorld(EntityJoinLevelEvent event) {
        // 仅处理普通箭矢实体
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        // 排除已经是琉璃冰魄箭的情况（如子箭）
        if (arrow instanceof GlacialArrowEntity) return;

        // 获取发射者
        if (!(arrow.getOwner() instanceof Player player)) return;

        // 检查玩家主手弓是否有附魔
        ItemStack bow = player.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) {
            bow = player.getUseItem();
            if (!(bow.getItem() instanceof BowItem)) return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.GLACIAL_ARROW.get(), bow);
        if (enchantLevel <= 0) return;

        // 读取蓄力时间
        long startTime = player.getPersistentData().getLong(CHARGE_START_KEY);
        long chargeTicks = player.level().getGameTime() - startTime;
        if (chargeTicks < 0) chargeTicks = 0;

        // 判断蓄力阶段
        int threshold = Config.glacialArrowChargeThreshold;
        boolean isStage2 = chargeTicks >= threshold;

        // ========================================================================
        // 取消原版箭矢，替换为琉璃冰魄箭
        // ========================================================================
        event.setCanceled(true);

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
            // 二段蓄力：速度 × 配置倍率，伤害 × 配置倍率
            double speedMultiplier = Config.glacialArrowStage2Speed;
            double damageMultiplier = Config.glacialArrowStage2Damage;
            glacialArrow.setDeltaMovement(motion.scale(speedMultiplier));
            glacialArrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);
        } else {
            glacialArrow.setChargeStage(1);
            // 一段蓄力：速度 × 配置倍率，伤害 × 配置倍率
            double speedMultiplier = Config.glacialArrowStage1Speed;
            double damageMultiplier = Config.glacialArrowStage1Damage;
            glacialArrow.setDeltaMovement(motion.scale(speedMultiplier));
            glacialArrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);
        }

        // 复制原箭矢的其他属性
        glacialArrow.setXRot(arrow.getXRot());
        glacialArrow.setYRot(arrow.getYRot());
        glacialArrow.setNoGravity(arrow.isNoGravity());
        // 蓄力满弦箭默认不可拾取（防刷箭）
        glacialArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        // 加入世界
        arrow.level().addFreshEntity(glacialArrow);

        // 清理蓄力数据
        player.getPersistentData().remove(CHARGE_START_KEY);
    }
}