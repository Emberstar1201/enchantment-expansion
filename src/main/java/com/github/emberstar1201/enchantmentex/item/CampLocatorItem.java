package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.world.ZombieGirlCampSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CampLocatorItem extends Item {
    public CampLocatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.serverLevel().dimension() != Level.OVERWORLD) {
                serverPlayer.displayClientMessage(
                        Component.translatable("item.enchantment_expansion.camp_locator.wrong_dimension"), false);
            } else {
                BlockPos nearest = ZombieGirlCampSavedData.get(serverPlayer.serverLevel())
                        .findNearest(serverPlayer.blockPosition());
                if (nearest == null) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("item.enchantment_expansion.camp_locator.none"), false);
                } else {
                    int distance = (int) Math.sqrt(nearest.distSqr(serverPlayer.blockPosition()));
                    serverPlayer.displayClientMessage(Component.translatable(
                            "item.enchantment_expansion.camp_locator.found",
                            nearest.getX(), nearest.getY(), nearest.getZ(), distance), false);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
