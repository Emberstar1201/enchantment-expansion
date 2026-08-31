package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
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
// 自动修复附魔：向常见遗迹宝箱战利品注入附魔书
// 由于附魔设置为 treasureOnly + notDiscoverable + notTradeable，
// 仅靠原版战利品表不足以保证出现，因此通过 LootTableLoadEvent 手动注入。
// ========================================================================
public class AutoRepairLootHandler {

    // 目标结构：废弃矿井、沙漠神殿、丛林神庙、要塞图书馆、下界要塞、末地城、古代城市
    private static final ResourceLocation ABANDONED_MINESHAFT =
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft");
    private static final ResourceLocation DESERT_PYRAMID =
            ResourceLocation.parse("minecraft:chests/desert_pyramid");
    private static final ResourceLocation JUNGLE_TEMPLE =
            ResourceLocation.parse("minecraft:chests/jungle_temple");
    private static final ResourceLocation STRONGHOLD_LIBRARY =
            ResourceLocation.parse("minecraft:chests/stronghold_library");
    private static final ResourceLocation NETHER_FORTRESS =
            ResourceLocation.parse("minecraft:chests/nether_bridge");
    private static final ResourceLocation END_CITY =
            ResourceLocation.parse("minecraft:chests/end_city_treasure");
    private static final ResourceLocation ANCIENT_CITY =
            ResourceLocation.parse("minecraft:chests/ancient_city");

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!ABANDONED_MINESHAFT.equals(name)
                && !DESERT_PYRAMID.equals(name)
                && !JUNGLE_TEMPLE.equals(name)
                && !STRONGHOLD_LIBRARY.equals(name)
                && !NETHER_FORTRESS.equals(name)
                && !END_CITY.equals(name)
                && !ANCIENT_CITY.equals(name)) {
            return;
        }

        LootPool pool = buildPool(name);
        if (pool != null) {
            event.getTable().addPool(pool);
        }
    }

    private static LootPool buildPool(ResourceLocation table) {
        Enchantment enchantment = ModEnchantments.AUTO_REPAIR.get();
        ResourceLocation enchId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchId == null) {
            return null;
        }

        // 末地城与古代城市权重略高，以反映高价值宝藏定位
        int weight = (END_CITY.equals(table) || ANCIENT_CITY.equals(table)) ? 4 : 2;

        // 附魔书的等级池：I 级 60%，II 级 30%，III 级 10%
        // 为简单起见，直接生成 I 级（用户可通过铁砧合并升级）
        // 这里仍通过权重随机 I~III，以支持开箱体验
        CompoundTag i = bookTag(enchId.toString(), 1);
        CompoundTag ii = bookTag(enchId.toString(), 2);
        CompoundTag iii = bookTag(enchId.toString(), 3);

        return LootPool.lootPool()
                .name("auto_repair_enchanted_book")
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(i))
                        .setWeight(6))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(ii))
                        .setWeight(3))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(iii))
                        .setWeight(1))
                .setBonusRolls(ConstantValue.exactly(0.0F))
                .build();
    }

    private static CompoundTag bookTag(String enchantmentId, int level) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", enchantmentId);
        entry.putInt("lvl", level);
        ListTag stored = new ListTag();
        stored.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", stored);
        return tag;
    }
}
