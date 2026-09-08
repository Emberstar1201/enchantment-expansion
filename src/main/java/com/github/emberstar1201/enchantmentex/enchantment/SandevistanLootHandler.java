package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

// ========================================================================
// 斯安维斯坦附魔：战利品注入
//
// 附魔台只能抽到 I 级（II/III 级 minCost > 30），
// 这里向各类遗迹宝箱注入 II / III 级附魔书：
//   II 级（军用型）：约 18% 概率额外掉落
//   III 级（传说型）：约 8% 概率额外掉落，仅高价值宝箱
//
// bonusRolls 机制：rolls=0 + bonusRolls=0.18 表示每次开箱有 18% 概率
// 额外 roll 一次该池，池内仅有一本附魔书（权重 1）。
// ========================================================================
public class SandevistanLootHandler {

    // 所有可出现 II/III 级的遗迹宝箱
    private static final ResourceLocation[] STRUCTURE_CHESTS = {
            ResourceLocation.parse("minecraft:chests/simple_dungeon"),        // 地牢
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft"),   // 废弃矿井
            ResourceLocation.parse("minecraft:chests/stronghold_corridor"),   // 要塞走廊
            ResourceLocation.parse("minecraft:chests/desert_pyramid"),        // 沙漠神殿
            ResourceLocation.parse("minecraft:chests/jungle_temple"),         // 丛林神庙
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),      // 林地府邸
            ResourceLocation.parse("minecraft:chests/fortress"),              // 下界要塞
            ResourceLocation.parse("minecraft:chests/bastion_treasure"),      // 堡垒遗迹宝藏
            ResourceLocation.parse("minecraft:chests/end_city_treasure"),     // 末地城
            ResourceLocation.parse("minecraft:chests/ancient_city"),          // 远古城市
    };

    // III 级（传说型）额外限定的高价值宝箱
    private static final ResourceLocation[] HIGH_TIER_CHESTS = {
            ResourceLocation.parse("minecraft:chests/bastion_treasure"),
            ResourceLocation.parse("minecraft:chests/end_city_treasure"),
            ResourceLocation.parse("minecraft:chests/ancient_city"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),
    };

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();

        boolean isStructureChest = false;
        for (ResourceLocation rl : STRUCTURE_CHESTS) {
            if (rl.equals(name)) {
                isStructureChest = true;
                break;
            }
        }
        if (!isStructureChest) return;

        var enchantment = ModEnchantments.SANDEVISTAN.get();
        ResourceLocation enchId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchId == null) return;

        // II 级附魔书池（约 18% 概率）
        event.getTable().addPool(buildBookPool(
                "sandevistan_book_ii", enchId, 2, 0.18F));

        // III 级仅高价值宝箱（约 8% 概率）
        for (ResourceLocation rl : HIGH_TIER_CHESTS) {
            if (rl.equals(name)) {
                event.getTable().addPool(buildBookPool(
                        "sandevistan_book_iii", enchId, 3, 0.08F));
                break;
            }
        }
    }

    /** 构建一个"概率掉落指定等级附魔书"的战利品池 */
    private static LootPool buildBookPool(String poolName, ResourceLocation enchId,
                                          int level, float bonusRolls) {
        // 附魔书 NBT（StoredEnchantments 格式）
        CompoundTag entry = new CompoundTag();
        entry.putString("id", enchId.toString());
        entry.putInt("lvl", level);
        ListTag stored = new ListTag();
        stored.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", stored);

        return LootPool.lootPool()
                .name(poolName)
                .setRolls(ConstantValue.exactly(0.0F))
                .setBonusRolls(ConstantValue.exactly(bonusRolls))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(tag))
                        .setWeight(1))
                .build();
    }
}
