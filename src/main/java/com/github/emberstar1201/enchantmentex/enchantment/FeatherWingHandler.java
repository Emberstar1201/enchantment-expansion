package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 羽翼 Handler
//
// 监听 LivingHurtEvent：
//   当玩家鞘翅飞行中撞墙受到伤害时，如果胸甲（鞘翅）带有羽翼附魔，
//   取消该伤害。
//
// 原版鞘翅撞墙伤害源为 DamageSource.FLY_INTO_WALL，
// 在 LivingHurtEvent 中可通过 damageSource.is(FLY_INTO_WALL) 判断。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FeatherWingHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (!(event.getEntity() instanceof Player player)) return;

        // 判断是否为撞墙伤害（FLY_INTO_WALL 在 1.20.1 中通过 damageSource 的 msgId 判断）
        // 1.20.1 中 DamageSource.FLY_INTO_WALL 是静态字段，但通过 is() 判断更安全
        // 使用 ResourceLocation 比较 damageType
        var source = event.getSource();
        if (!source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)) {
            return;
        }

        // 检查胸甲（鞘翅）是否带有羽翼附魔
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FEATHER_WING.get(), chestplate);
        if (level <= 0) return;

        // 取消撞墙伤害
        event.setCanceled(true);

        // ActionBar 提示
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§7§l羽翼 §r§b护体，撞墙伤害已免疫"),
                    true);
        }
    }
}
