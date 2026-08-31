package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

// ========================================================================
// 羽翼附魔：战利品注入
//
// 向末地城和末地船宝箱注入羽翼附魔书。
// 每个宝箱有 20% 概率刷出（bonusRolls=0.2 等效 20% 概率额外掉落一次）。
// ========================================================================
public class FeatherWingLootHandler {

    // 末地城宝箱
    private static final ResourceLocation END_CITY_TREASURE =
            ResourceLocation.parse("minecraft:chests/end_city_treasure");

    // 末地船宝箱
    private static final ResourceLocation END_SHIP =
            ResourceLocation.parse("minecraft:chests/end_ship_treasure");

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!END_CITY_TREASURE.equals(name) && !END_SHIP.equals(name)) {
            return;
        }

        // 获取羽翼附魔的注册 ID
        var enchantment = ModEnchantments.FEATHER_WING.get();
        ResourceLocation enchId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchId == null) return;

        // 构建附魔书 NBT（StoredEnchantments 格式）
        CompoundTag entry = new CompoundTag();
        entry.putString("id", enchId.toString());
        entry.putInt("lvl", 1);
        ListTag stored = new ListTag();
        stored.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", stored);

        // 构建 20% 概率出现的羽翼附魔书池
        // bonusRolls=0.2 → 每次 loottable roll 时有 20% 概率额外 roll 一次
        LootPool pool = LootPool.lootPool()
                .name("feather_wing_enchanted_book")
                .setRolls(ConstantValue.exactly(0.0F))
                .setBonusRolls(ConstantValue.exactly(0.2F))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(tag))
                        .setWeight(1))
                .build();

        event.getTable().addPool(pool);
    }
}
