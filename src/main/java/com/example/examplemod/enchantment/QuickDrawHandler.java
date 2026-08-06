package com.example.examplemod.enchantment;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.examplemod.ExampleMod.MODID;

// 速射附魔事件处理器
// 处理两个核心效果：1. 缩短弓蓄力时间  2. 提升箭矢飞行速度
@Mod.EventBusSubscriber(modid = MODID)
public class QuickDrawHandler {

    // ========================================================================
    // 【效果一】缩短弓的蓄力时间（加速拉弓）
    // 原理：在玩家"使用物品"的每tick（LivingEntityUseItemEvent.Tick），
    //       根据附魔等级跳过额外tick进度，等效于蓄力速度变快。
    // 例如：倍率1.5倍意味着，每现实1tick，游戏内的蓄力进度前进1.5tick。
    // ========================================================================
    @SubscribeEvent
    public static void onBowUseTick(LivingEntityUseItemEvent.Tick event) {
        // 仅处理玩家实体，避免影响骷髅等其他使用弓的生物
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack usingItem = event.getItem();
        // 仅处理弓类物品的使用
        if (!(usingItem.getItem() instanceof BowItem)) {
            return;
        }

        // 获取弓上"速射"附魔的等级
        int enchantLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.QUICK_DRAW.get(), usingItem);
        if (enchantLevel <= 0) {
            return;
        }

        // 获取蓄力加速倍率（查表法，每个等级对应独立数值）
        double chargeMultiplier = QuickDrawEnchantment.getChargeSpeedMultiplier(enchantLevel);

        // 倍率 <= 1.0 时无需加速
        if (chargeMultiplier <= 1.0) {
            return;
        }

        // 【核心逻辑】：倍率减1即为需要"额外跳过"的进度
        // 例如倍率=1.5，则每次额外推进0.5tick的蓄力进度
        // 对整数部分直接累加多次，小数部分通过累积计数处理（防止精度丢失）
        double extraProgress = chargeMultiplier - 1.0;

        // 整数部分：每tick直接多次推进（常见倍率1.5倍以上才会有整数跳）
        int wholeSkips = (int) extraProgress;
        // 通过循环多次触发"推进使用物品"效果
        for (int i = 0; i < wholeSkips; i++) {
            // 手动执行一次使用物品tick（相当于蓄力进度+1）
            // Forge原版通过LivingEntity#tick()中触发，此处我们手动模拟
            // 更稳妥的方式是直接修改duration（剩余使用时长）
            int newDuration = event.getDuration() - 1;
            if (newDuration <= 0) {
                newDuration = 1; // 防止直接使用完成，交给原版判定
            }
            event.setDuration(newDuration);
        }

        // 小数部分：使用累积计数器（利用玩家PersistentData存储累积值）
        // 例如0.3倍小数，则每tick累积0.3，到>=1.0时额外多跳1tick
        float fractional = (float) (extraProgress - wholeSkips);
        if (fractional > 0) {
            String accumKey = "QuickDraw_FractionalAccum_" + usingItem.getDescriptionId();
            float accumulated = player.getPersistentData().getFloat(accumKey);
            accumulated += fractional;
            if (accumulated >= 1.0f) {
                // 累积满1 tick，额外跳1格进度
                int newDuration = event.getDuration() - 1;
                if (newDuration <= 0) {
                    newDuration = 1;
                }
                event.setDuration(newDuration);
                accumulated -= 1.0f; // 减去消耗的1.0，保留余数继续累积
            }
            player.getPersistentData().putFloat(accumKey, accumulated);
        }
    }

    // ========================================================================
    // 【效果二】提升箭矢射出后的飞行速度
    // 原理：在箭矢实体加入世界（EntityJoinLevelEvent）时，
    //       判断其发射者（owner）是否手持带有速射附魔的弓，
    //       若是，则将箭矢的运动向量（motion）直接乘以速度倍率。
    // 实现方式：修改 AbstractArrow#setDeltaMovement（即motion x/y/z）
    // ========================================================================
    @SubscribeEvent
    public static void onArrowJoinWorld(EntityJoinLevelEvent event) {
        // 仅处理箭矢类实体（包含普通箭、光谱箭、药箭等所有AbstractArrow子类）
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        // 获取箭矢的发射者（owner），必须是玩家
        if (!(arrow.getOwner() instanceof Player player)) {
            return;
        }

        // 获取玩家主手使用的弓（即发射这支箭时用的弓）
        // 注意：玩家在箭发射后的短暂时间内主手仍持有弓，此事件在服务端和客户端都会触发
        ItemStack bow = player.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) {
            // 如果主手不是弓，尝试检查玩家"使用中"的物品（某些极端情况）
            bow = player.getUseItem();
            if (!(bow.getItem() instanceof BowItem)) {
                return;
            }
        }

        // 获取速射附魔等级
        int enchantLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.QUICK_DRAW.get(), bow);
        if (enchantLevel <= 0) {
            return;
        }

        // 获取箭矢飞行速度倍率（查表法，与蓄力加速为两套独立数值）
        double flightMultiplier = QuickDrawEnchantment.getFlightSpeedMultiplier(enchantLevel);
        if (flightMultiplier <= 1.0) {
            return;
        }

        // 【核心】将箭矢的飞行速度整体乘以倍率
        // 这样x/y/z三个方向都等比例放大，弹道更直、飞行更快
        // 原方向向量不变，仅改变模长（速度大小）
        arrow.setDeltaMovement(
                arrow.getDeltaMovement().x * flightMultiplier,
                arrow.getDeltaMovement().y * flightMultiplier,
                arrow.getDeltaMovement().z * flightMultiplier
        );

        // 同时同步箭矢的基础伤害：原版箭伤害与速度正相关（粗略公式 damage = speed * 0.6）
        // 这里将基础伤害也乘以飞行倍率，让高速箭造成更高伤害，更符合直觉
        // 注意：这是可选增强，如果不想要伤害加成可注释掉下一行
        arrow.setBaseDamage(arrow.getBaseDamage() * flightMultiplier);
    }

    // ========================================================================
    // 辅助方法：获取玩家身上速射附魔等级（支持主手和副手，虽然副手弓几乎不会用）
    // 目前只在主手生效，因为弓的使用必须在主手
    // ========================================================================
    private static int getQuickDrawLevel(LivingEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        return net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(ModEnchantments.QUICK_DRAW.get(), mainHand);
    }
}
