package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** 模组专用创造模式分区。 */
public final class ModCreativeModeTabs {
    private ModCreativeModeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(),
                    EnchantmentExpansion.MODID);

    public static final RegistryObject<CreativeModeTab> ENCHANTMENT_BOOKS = TABS.register(
            "enchantment_books", () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable(
                            "itemGroup.enchantment_expansion.enchantment_books"))
                    .icon(() -> new ItemStack(Items.ENCHANTED_BOOK))
                    .displayItems((parameters, output) -> {
                        ModEnchantments.ENCHANTMENTS.getEntries().forEach(entry -> output.accept(
                                EnchantedBookItem.createForEnchantment(
                                        new EnchantmentInstance(entry.get(), entry.get().getMaxLevel()))));
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> ITEMS = TABS.register(
            "items", () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable(
                            "itemGroup.enchantment_expansion.items"))
                    .icon(() -> new ItemStack(ModItems.END_STAR.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.END_STAR.get());
                        output.accept(ModItems.OCEAN_STAR.get());
                        output.accept(ModItems.LIFE_STAR.get());
                        output.accept(ModItems.VOID_STAR.get());
                        output.accept(ModItems.STARLIGHT_STAR.get());
                        output.accept(ModItems.ETERNAL_TOTEM.get());
                        output.accept(ModItems.DAWN_STAR.get());
                        output.accept(ModItems.ENHANCEMENT_SCROLL.get());
                        output.accept(ModItems.UNOWNED_STARDUST.get());
                        output.accept(ModItems.HOME_BELL.get());
                        output.accept(ModItems.CAMP_LOCATOR.get());
                        output.accept(ModItems.AFFECTION_STAGE_UP.get());
                        output.accept(ModItems.AFFECTION_STAGE_DOWN.get());
                        output.accept(ModItems.UNSIGNED_VERSES.get());
                        output.accept(ModItems.EMBERSTAR_CORD_BRACELET.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> WEAPONS = TABS.register(
            "weapons", () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable(
                            "itemGroup.enchantment_expansion.weapons"))
                    .icon(() -> new ItemStack(ModItems.SWORD_OF_THE_FREE_WILL_ENHANCED.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SWORD_OF_THE_FREE_WILL.get());
                        output.accept(ModItems.SWORD_OF_THE_FREE_WILL_ENHANCED.get());
                        output.accept(ModItems.RESIDUAL_SCYTHE.get());
                        output.accept(ModItems.LUOHONGYU.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
