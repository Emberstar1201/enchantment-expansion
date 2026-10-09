package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 无名诗笺战利品注入：对所有原版宝箱战利品表（{@code minecraft:chests/*}，
 * 排除出生奖励箱）附加一个独立池——15% 概率开出一张无名诗笺。
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VersesLootHandler {
    /** 开出无名诗笺的概率。 */
    private static final float DROP_CHANCE = 0.15F;

    private VersesLootHandler() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!"minecraft".equals(name.getNamespace())
                || !name.getPath().startsWith("chests/")
                || name.getPath().equals("chests/spawn_bonus_chest")) {
            return;
        }
        event.getTable().addPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(ModItems.UNSIGNED_VERSES.get())
                        .when(LootItemRandomChanceCondition.randomChance(DROP_CHANCE)))
                .build());
    }
}
