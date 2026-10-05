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

        Item guideBook = ForgeRegistries.ITEMS.getValue(new ResourceLocation(PATCHOULI_MOD_ID, "guide_book"));
        if (guideBook == null) {
            return;
        }

        if (player.getPersistentData().getBoolean(BOOK_GIVEN_TAG)) {
            return;
        }

        // 兼容旧存档：即使玩家的领取标记丢失，只要背包里已有本模组终界之书，也不再重复发放。
        if (hasTerminalBook(player, guideBook)) {
            player.getPersistentData().putBoolean(BOOK_GIVEN_TAG, true);
            return;
        }

        CompoundTag tag = new CompoundTag();
        // Patchouli 1.20.1 要求书籍 ID 直接放在带命名空间的 NBT 键中。
        tag.putString(PATCHOULI_BOOK_TAG, BOOK_ID);

        // 先标记再发放：即使发放过程中服务器异常，下次登录也不会重复领取。
        // 标记保存在玩家 ForgeData 中，跨重登、跨死亡（Forge 会在重生时复制）均保留。
        player.getPersistentData().putBoolean(BOOK_GIVEN_TAG, true);

        ItemStack book = new ItemStack(guideBook);
        book.setTag(tag);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    private static boolean hasTerminalBook(ServerPlayer player, Item guideBook) {
        for (ItemStack stack : player.getInventory().items) {
            if (isTerminalBook(stack, guideBook)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (isTerminalBook(stack, guideBook)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isTerminalBook(stack, guideBook)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTerminalBook(ItemStack stack, Item guideBook) {
        return stack.is(guideBook)
                && stack.hasTag()
                && BOOK_ID.equals(stack.getTag().getString(PATCHOULI_BOOK_TAG));
    }
}
