package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.entity.GlacialArrowEntity;
import com.mojang.logging.LogUtils;
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
// 琉璃冰魄箭 附魔事件处理器（简化版：取消二段蓄力，改为直接散射）
//
// 【职责】
//   1. 加速蓄力（LivingEntityUseItemEvent.Tick 中跳 tick 进度）
//   2. 箭矢加入世界时替换为自定义琉璃冰魄箭实体
//   3. 母箭射出时立即生成子箭（散射）
//
// 【蓄力加速方案】
//   参考云来弓法的实现：在 Tick 事件中根据蓄力倍率跳过额外 tick 进度，
//   使弓的蓄力速度提升至 2 倍（0.5s 蓄满，原版需 1s）
//
// 【取消二段蓄力】
//   不再追踪蓄力开始时间，不再区分一段/二段，直接散射。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GlacialArrowHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ========================================================================
    // 【效果一】加速蓄力：弓的蓄力速度提升至原版的 2 倍（0.5s 蓄满）
    // ========================================================================
    @SubscribeEvent
    public static void onBowUseTick(LivingEntityUseItemEvent.Tick event) {
        // 仅处理玩家
        if (!(event.getEntity() instanceof Player player)) return;
        // 仅处理弓
        if (!(event.getItem().getItem() instanceof BowItem)) return;

        // 检查弓上是否有琉璃冰魄箭附魔
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.GLACIAL_ARROW.get(), event.getItem());
        if (enchantLevel <= 0) return;

        // 获取蓄力加速倍率（默认 2.0 = 蓄力时间减半至 0.5s）
        double chargeMultiplier = GlacialArrowEnchantment.getChargeSpeedMultiplier();
        if (chargeMultiplier <= 1.0) return;

        // 倍率减 1 即为需要"额外跳过"的蓄力进度
        double extraProgress = chargeMultiplier - 1.0;

        // 整数部分：每 tick 直接跳 n 格进度
        int wholeSkips = (int) extraProgress;
        for (int i = 0; i < wholeSkips; i++) {
            int newDuration = event.getDuration() - 1;
            if (newDuration <= 0) newDuration = 1;
            event.setDuration(newDuration);
        }

        // 小数部分：累积计数处理（保证无精度丢失）
        float fractional = (float) (extraProgress - wholeSkips);
        if (fractional > 0) {
            String accumKey = "GlacialArrow_FractionalAccum_" + event.getItem().getDescriptionId();
            float accumulated = player.getPersistentData().getFloat(accumKey);
            accumulated += fractional;
            if (accumulated >= 1.0f) {
                int newDuration = event.getDuration() - 1;
                if (newDuration <= 0) newDuration = 1;
                event.setDuration(newDuration);
                accumulated -= 1.0f;
            }
            player.getPersistentData().putFloat(accumKey, accumulated);
        }
    }

    // ========================================================================
    // 【效果二】箭矢加入世界 → 替换为琉璃冰魄箭 + 直接散射
    //   ★ 取消二段蓄力：不再根据蓄力时间区分阶段，始终散射
    //   ★ 母箭速度：3.2 倍原版箭速度
    // ========================================================================
    @SubscribeEvent
    public static void onArrowJoinWorld(EntityJoinLevelEvent event) {
        // 仅服务端处理
        if (event.getLevel().isClientSide()) return;

        // 仅处理普通箭矢实体
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        // 排除已经是琉璃冰魄箭的情况
        if (arrow instanceof GlacialArrowEntity) return;

        // 获取发射者
        if (!(arrow.getOwner() instanceof Player player)) return;

        // 检查玩家手中的弓是否有附魔
        ItemStack bow = player.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) {
            bow = player.getUseItem();
            if (!(bow.getItem() instanceof BowItem)) return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.GLACIAL_ARROW.get(), bow);
        if (enchantLevel <= 0) return;

        // 取消原版箭矢
        event.setCanceled(true);

        // 获取原箭矢的数据
        Vec3 position = arrow.position();
        Vec3 motion = arrow.getDeltaMovement();

        // 创建自定义箭矢实体
        GlacialArrowEntity glacialArrow = new GlacialArrowEntity(
                arrow.level(), player,
                position.x, position.y, position.z);

        // 设置母箭速度和基础伤害
        double speedMultiplier = Config.glacialArrowSpeed;
        double damageMultiplier = Config.glacialArrowDamage;
        glacialArrow.setDeltaMovement(motion.scale(speedMultiplier));
        glacialArrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);

        // 设置母箭穿透次数
        int pierceCount = Config.glacialArrowPierceCount;
        glacialArrow.setMaxPierceCount(pierceCount);

        // 复制原箭矢的其他属性
        glacialArrow.setXRot(arrow.getXRot());
        glacialArrow.setYRot(arrow.getYRot());
        glacialArrow.setNoGravity(arrow.isNoGravity());
        glacialArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        // 加入世界
        boolean added = arrow.level().addFreshEntity(glacialArrow);

        if (added) {
            // 射出时立即生成子箭（散射）
            glacialArrow.spawnSubArrowsNow();
            LOGGER.debug("[GlacialArrow] 母箭已生成, 位置={}, 速度={}, 速度倍率={}",
                    glacialArrow.position(), glacialArrow.getDeltaMovement(), speedMultiplier);
        }
    }
}