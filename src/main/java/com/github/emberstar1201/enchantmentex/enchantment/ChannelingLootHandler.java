package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

// ========================================================================
// 【雷电之戟 Lightning Spear】战利品表注入处理器
//
// 注入规则（对应引雷 II=雷电之戟 I、引雷 III=雷电之戟 II 的新旧映射）：
//   - 钓鱼：雷电之戟 I（8%）+ 雷电之戟 II（4%）
//   - 沙漠神殿：雷电之戟 I（12%）
//   - 废弃矿洞：雷电之戟 I（10%）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID)
public class ChannelingLootHandler {

    private static final ResourceLocation FISHING = ResourceLocation.of("minecraft:gameplay/fishing", ':');
    private static final ResourceLocation DESERT_PYRAMID = ResourceLocation.of("minecraft:chests/desert_pyramid", ':');
    private static final ResourceLocation ABANDONED_MINESHAFT = ResourceLocation.of("minecraft:chests/abandoned_mineshaft", ':');

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();

        if (FISHING.equals(name)) {
            addPoolIfNotNull(event,
                    "lightning_spear_I_fishing",
                    ModEnchantments.LIGHTNING_SPEAR.get(),
                    1,
                    0.08f
            );
            addPoolIfNotNull(event,
                    "lightning_spear_II_fishing",
                    ModEnchantments.LIGHTNING_SPEAR.get(),
                    2,
                    0.04f
            );
        } else if (DESERT_PYRAMID.equals(name)) {
            addPoolIfNotNull(event,
                    "lightning_spear_I_desert",
                    ModEnchantments.LIGHTNING_SPEAR.get(),
                    1,
                    0.12f
            );
        } else if (ABANDONED_MINESHAFT.equals(name)) {
            addPoolIfNotNull(event,
                    "lightning_spear_I_mineshaft",
                    ModEnchantments.LIGHTNING_SPEAR.get(),
                    1,
                    0.10f
            );
        }
    }

    private static LootPool buildEnchantedBookPool(String poolName,
                                                    Enchantment enchantment,
                                                    int level,
                                                    float chance) {
        ResourceLocation enchId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchId == null) {
            return null;
        }

        CompoundTag enchEntry = new CompoundTag();
        enchEntry.putString("id", enchId.toString());
        enchEntry.putInt("lvl", level);

        ListTag storedEnchantments = new ListTag();
        storedEnchantments.add(enchEntry);

        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", storedEnchantments);

        return LootPool.lootPool()
                .name(poolName)
                .setRolls(ConstantValue.exactly(chance))
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(tag))
                )
                .build();
    }

    private static void addPoolIfNotNull(LootTableLoadEvent event,
                                          String poolName,
                                          Enchantment enchantment,
                                          int level,
                                          float chance) {
        LootPool pool = buildEnchantedBookPool(poolName, enchantment, level, chance);
        if (pool != null) {
            event.getTable().addPool(pool);
        }
    }
}