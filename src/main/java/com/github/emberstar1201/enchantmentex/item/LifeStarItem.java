package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.GirlCureHandler;
import com.github.emberstar1201.enchantmentex.entity.SurvivorGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 【生命之星】自定义物品类
//
// 特性：
//   - 无附魔光效（isFoil=false），描述已隐藏（不重写 appendHoverText）
//   - 手持效果：生命值上限+30 (20→50)、回血速度×2、饥饿盾
//   - 盔甲嵌入：铁砧合并后效果相同
//   - 工作台合成：9种花卉（严格颜色匹配）
//   - 治愈：右键丧尸娘 / 溺尸娘开始净化（1~2 分钟过渡动画，参考僵尸村民治疗），
//     由 GirlCureHandler 推进三阶段粒子，结束时变身幸存者少女
//     （物品不消耗，120 秒冷却）
// ========================================================================
public class LifeStarItem extends Item {

    /** 治愈冷却：120 秒 = 2400 tick（防止短时间反复开始净化）。 */
    private static final int CURE_COOLDOWN_TICKS = 2400;

    public LifeStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFireResistant() {
        return false;  // 生命之星无防火需求
    }

    @Override
    public boolean canBeDepleted() {
        return false;  // 无耐久概念
    }

    /**
     * 右键丧尸娘 / 溺尸娘 → 开始净化，等待期结束后变成幸存者少女。
     * 生命之星是可重复使用的圣器，净化不消耗物品，只进入 120 秒冷却。
     * 已治愈的幸存者少女不能再次净化（PASS 放行给她自身的交互逻辑），
     * 正在净化中的个体也不重复开始（无冷却、无效果）。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                 LivingEntity target, InteractionHand hand) {
        // 幸存者少女继承自丧尸娘，必须显式排除，防止对人类形态重复「治愈」挡掉开背包等交互
        boolean convertible = !(target instanceof SurvivorGirlEntity)
                && (target instanceof ZombieGirlEntity || target instanceof DrownedGirlEntity);
        if (!convertible) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(this) || GirlCureHandler.isCuring(target)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            // 客户端先返回成功播放挥手动作；真正的净化只在服务端推进
            return InteractionResult.SUCCESS;
        }

        // 开始净化：此后由 GirlCureHandler 每 tick 推进粒子，到点完成变身
        if (player instanceof ServerPlayer serverPlayer
                && GirlCureHandler.startCuring(target, serverPlayer)) {
            // 生命之星不消耗，仅加冷却（创造 / 生存模式都加）
            player.getCooldowns().addCooldown(this, CURE_COOLDOWN_TICKS);
        }
        return InteractionResult.CONSUME;
    }
}
