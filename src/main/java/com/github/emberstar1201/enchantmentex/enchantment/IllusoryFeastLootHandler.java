package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

// 将画饼充饥附魔书注入常见遗迹宝箱，附魔本体仍可正常出现在附魔台。
public class IllusoryFeastLootHandler {

    private static final ResourceLocation DESERT_PYRAMID =
            ResourceLocation.parse("minecraft:chests/desert_pyramid");
    private static final ResourceLocation ABANDONED_MINESHAFT =
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft");
    private static final ResourceLocation JUNGLE_TEMPLE =
            ResourceLocation.parse("minecraft:chests/jungle_temple");
    private static final ResourceLocation STRONGHOLD_LIBRARY =
            ResourceLocation.parse("minecraft:chests/stronghold_library");

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!DESERT_PYRAMID.equals(name)
                && !ABANDONED_MINESHAFT.equals(name)
                && !JUNGLE_TEMPLE.equals(name)
                && !STRONGHOLD_LIBRARY.equals(name)) {
            return;
        }

        LootPool pool = buildEnchantedBookPool();
        if (pool != null) {
            event.getTable().addPool(pool);
        }
    }

    // ========================================================================
    // 构建画饼充饥附魔书 LootPool
    //
    // 【概率实现原理】
    //   LootPool 的 rolls 决定尝试 roll 的次数：
    //     - rolls = 1.0 → 每次 roll 1 次（必出）
    //     - rolls = 0.05 → 5% 概率 roll 1 次（整数部分为 0，小数部分为概率）
    //   pool 内只有 1 个 entry 时，setWeight 不影响概率（必然选中），
    //   因此概率控制必须通过 setRolls 的小数部分实现。
    // ========================================================================
    private static LootPool buildEnchantedBookPool() {
        Enchantment enchantment = ModEnchantments.ILLUSORY_FEAST.get();
        ResourceLocation enchantmentId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchantmentId == null) {
            return null;
        }

        CompoundTag entry = new CompoundTag();
        entry.putString("id", enchantmentId.toString());
        entry.putInt("lvl", 1);

        ListTag storedEnchantments = new ListTag();
        storedEnchantments.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", storedEnchantments);

        return LootPool.lootPool()
                .name("illusory_feast_enchanted_book")
                .setRolls(ConstantValue.exactly(0.05F))  // 5% 概率生成
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(tag))
                )
                .build();
    }
}
