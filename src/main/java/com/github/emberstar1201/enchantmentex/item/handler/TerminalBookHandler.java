package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 终界之书的开局赠送处理器。
 *
 * 使用 PlayerLoggedInEvent 而不是客户端事件，确保服务器存档、局域网和单人游戏都能正常发放。
 */
public final class TerminalBookHandler {
    private static final String PATCHOULI_MOD_ID = "patchouli";
    private static final String BOOK_ID = EnchantmentExpansion.MODID + ":enchantment_expansion";
    private static final String PATCHOULI_BOOK_TAG = "patchouli:book";
    private static final String BOOK_GIVEN_TAG = "enchantment_expansion_terminal_book_given";

    private TerminalBookHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ModList.get().isLoaded(PATCHOULI_MOD_ID)) {
            return;
        }

        if (player.getPersistentData().getBoolean(BOOK_GIVEN_TAG)) {
            return;
        }

        Item guideBook = ForgeRegistries.ITEMS.getValue(new ResourceLocation(PATCHOULI_MOD_ID, "guide_book"));
        if (guideBook == null) {
            return;
        }

        CompoundTag tag = new CompoundTag();
        // Patchouli 1.20.1 要求书籍 ID 直接放在带命名空间的 NBT 键中。
        tag.putString(PATCHOULI_BOOK_TAG, BOOK_ID);

        ItemStack book = new ItemStack(guideBook);
        book.setTag(tag);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.getPersistentData().putBoolean(BOOK_GIVEN_TAG, true);
    }
}
