package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class AffectionStageUpItem extends Item {
    private static final int VAGUE_THRESHOLD = 30;
    private static final int EXACT_THRESHOLD = 70;
    private static final int MAX_AFFECTION = 100;

    public AffectionStageUpItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity target,
            InteractionHand hand) {
        if (!(target instanceof FriendlyGirlInventory girl)
                || !girl.isTamed()
                || !girl.isOwnedBy(player)) {
            return InteractionResult.PASS;
        }

        if (!target.level().isClientSide) {
            int current = girl.getAffection();
            int next = current < VAGUE_THRESHOLD
                    ? VAGUE_THRESHOLD
                    : current < EXACT_THRESHOLD
                            ? EXACT_THRESHOLD
                            : MAX_AFFECTION;
            girl.setAffection(next);
            player.displayClientMessage(Component.translatable(
                    next == MAX_AFFECTION && current == MAX_AFFECTION
                            ? "item.enchantment_expansion.affection_stage_up.max"
                            : "item.enchantment_expansion.affection_stage_up.success",
                    next), true);
        }

        return InteractionResult.sidedSuccess(target.level().isClientSide);
    }
}
