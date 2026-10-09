package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.item.handler.GirlSummonHandler;
import com.github.emberstar1201.enchantmentex.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// ========================================================================
// 【归乡铃】（Home Bell）
//   右键使用：铃声把主人名下所有已驯服的少女（丧尸娘 / 溺尸娘 / 幸存者少女）
//   召回身边——包括位于未加载区块中的个体（详见 GirlSummonHandler）。
//   只召回与玩家同维度的少女，2 秒冷却防止连点。
// 注册ID：home_bell
// ========================================================================
public class HomeBellItem extends Item {
    /** 使用冷却：2 秒，防止连点造成区块反复强制加载。 */
    private static final int USE_COOLDOWN = 40;

    public HomeBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int count = GirlSummonHandler.summonAll(serverPlayer);
            player.getCooldowns().addCooldown(this, USE_COOLDOWN);
            // 自定义摇铃音效：音频文件 assets/enchantment_expansion/sounds/home_bell.ogg
            level.playSound(null, player.blockPosition(), ModSounds.HOME_BELL.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            serverPlayer.displayClientMessage(Component.translatable(
                    count > 0
                            ? "item.enchantment_expansion.home_bell.summoned"
                            : "item.enchantment_expansion.home_bell.none",
                    count), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
