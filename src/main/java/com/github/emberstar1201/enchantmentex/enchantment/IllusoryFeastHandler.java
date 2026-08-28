package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.IllusoryFeastConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// 画饼充饥命中处理：服务端确认有效伤害后才恢复饥饿值，避免客户端重复执行。
public class IllusoryFeastHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide() || !IllusoryFeastConfig.isEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ILLUSORY_FEAST.get(), weapon);
        if (enchantLevel <= 0) {
            return;
        }

        // FoodData.eat 会自动将饥饿值限制为 20，饱食度也会按原版规则处理。
        attacker.getFoodData().eat(
                IllusoryFeastConfig.getFood(enchantLevel),
                IllusoryFeastConfig.getSaturation(enchantLevel)
        );
    }
}
