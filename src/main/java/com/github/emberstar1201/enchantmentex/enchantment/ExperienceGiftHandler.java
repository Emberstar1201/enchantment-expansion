package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 经验馈赠 Handler
//
// 监听 LivingExperienceDropEvent：
//   击杀生物时，如果击杀者主手武器带有经验馈赠附魔，
//   在原版经验掉落基础上额外增加 50%（向下取整，至少 +1）。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ExperienceGiftHandler {

    /** 额外经验倍率（50%） */
    private static final double BONUS_RATIO = 0.5;

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null) return;
        if (killer.level().isClientSide()) return;

        // 检查主手武器是否带有经验馈赠
        ItemStack weapon = killer.getItemBySlot(EquipmentSlot.MAINHAND);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.EXPERIENCE_GIFT.get(), weapon);
        if (level <= 0) return;

        int originalXp = event.getDroppedExperience();
        if (originalXp <= 0) return;

        // 额外 50%，至少 +1
        int bonus = Math.max(1, (int) (originalXp * BONUS_RATIO));
        event.setDroppedExperience(originalXp + bonus);

        // 仅服务端且为 ServerPlayer 时发送提示
        if (killer instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§b§l经验馈赠 §r§7额外获得 " + bonus + " 经验"),
                    true);
        }
    }
}
