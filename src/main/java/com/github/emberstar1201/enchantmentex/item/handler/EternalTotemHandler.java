package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【永恒图腾】事件处理器
//
// 核心机制（仅在主手或副手持有时生效）：
//   - 监听 LivingDamageEvent（HIGH 优先级，先于其他减伤逻辑）
//   - 当本次伤害 ≥ 当前生命值时，判定为"致命伤"：
//       1. 取消本次伤害（玩家不死）
//       2. 将生命值回满到 player.getMaxHealth()
//          （若手持/嵌入生命之星，会自动按加成后的上限回满）
//       3. 消耗图腾 1 点耐久；耐久耗尽则图腾破碎消失
//
// 注意：
//   - 仅服务端判定，避免客户端重复触发。
//   - 虚空伤害（DamageTypes.FELL_OUT_OF_WORLD）也走本事件，因此会被拦截；
//     但玩家若持续处于虚空，每 tick 都会重新结算并消耗 1 点耐久，
//     即 20 点耐久在虚空中约可抵挡 1 秒（20 tick），这是预期行为。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EternalTotemHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 本次伤害若不足以致死，不触发
        if (event.getAmount() < player.getHealth()) return;

        // 优先判定主手，其次副手
        ItemStack totem = null;
        InteractionHand hand = null;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (isUsableEternalTotem(mainHand)) {
            totem = mainHand;
            hand = InteractionHand.MAIN_HAND;
        } else if (isUsableEternalTotem(offHand)) {
            totem = offHand;
            hand = InteractionHand.OFF_HAND;
        }

        if (totem == null) return;

        // 1. 取消本次致命伤害
        event.setCanceled(true);

        // 2. 回满生命值（getMaxHealth 已包含生命之星的上限加成，天然联动）
        player.setHealth(player.getMaxHealth());

        // 3. 消耗 1 点耐久；耐久耗尽时 hurtAndBreak 会自动 shrink 并广播破碎动画
        final InteractionHand finalHand = hand;
        totem.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(finalHand));
    }

    /**
     * 判断物品是否为"可用的永恒图腾"：
     *   - 物品本身是永恒图腾
     *   - 耐久尚未耗尽（damageValue < maxDamage）
     */
    private static boolean isUsableEternalTotem(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(ModItems.ETERNAL_TOTEM.get())
                && stack.getDamageValue() < stack.getMaxDamage();
    }
}
