package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 将星尘手绳以 1% 概率加入所有命名为 chests/* 的战利品箱。
 *
 * 使用 LootTableLoadEvent 注入而不是覆盖原版表，能同时兼容原版和其他模组对战利品表的修改。
 */
public final class EmberstarCordBraceletLootHandler {
    private static final float LOOT_CHANCE = 0.01F;

    private EmberstarCordBraceletLootHandler() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();
        if (!id.getPath().startsWith("chests/")) {
            return;
        }

        LootPool pool = LootPool.lootPool()
                .name("emberstar_cord_bracelet")
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(ModItems.EMBERSTAR_CORD_BRACELET.get())
                        .when(LootItemRandomChanceCondition.randomChance(LOOT_CHANCE)))
                .build();
        event.getTable().addPool(pool);
    }
}
