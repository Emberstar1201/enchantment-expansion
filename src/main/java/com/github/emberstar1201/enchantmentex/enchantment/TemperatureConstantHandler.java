package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.TemperatureConstantConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【温度恒定】附魔事件处理器
//
// 实现（纯事件，无需 Mixin）：
//   ① 冰冻防御：LivingHurtEvent 拦截 DamageTypeTags.IS_FREEZING（细雪冻伤），
//      并在 PlayerTickEvent 中把冻结计时 setTicksFrozen(0) 清零——
//      双保险：即使伤害已发出，也不会继续累积冰冻。
//   ② 高温防御：LivingHurtEvent 拦截 DamageTypeTags.IS_FIRE
//      （火焰、岩浆、灼热地面、营火等）。
//
// 判定：任意护甲部位（头/胸/腿/靴）带「温度恒定」即视为全局生效。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TemperatureConstantHandler {

    // ========================================================================
    // ① 伤害拦截：冰冻 / 高温伤害源直接取消
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // 全局开关
        if (!TemperatureConstantConfig.enabled) return;
        // 任意护甲部位有附魔？
        if (!hasEnchant(player)) return;

        // 冰冻伤害：细雪冻伤
        if (TemperatureConstantConfig.preventFreeze && event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            event.setCanceled(true);
            player.setTicksFrozen(0); // 顺带清零冻结计时
            return;
        }

        // 高温伤害：火焰/岩浆/灼热地面等
        if (TemperatureConstantConfig.preventHeat && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // ② 冻结计时清零：每 tick 把 frozen 计时清 0（防止慢慢冻僵动画与伤害）
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;

        // 全局开关 + 防冻开关 + 有附魔时才清零
        if (TemperatureConstantConfig.enabled
                && TemperatureConstantConfig.preventFreeze
                && hasEnchant(player)
                && player.getTicksFrozen() > 0) {
            player.setTicksFrozen(0);
        }
    }

    // ========================================================================
    // 工具：检测玩家任意护甲部位是否有「温度恒定」
    // ========================================================================
    private static boolean hasEnchant(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            int level = EnchantmentHelper.getTagEnchantmentLevel(
                    ModEnchantments.TEMPERATURE_CONSTANT.get(), stack);
            if (level > 0) return true;
        }
        return false;
    }
}