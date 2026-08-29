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
// 【引雷 II / III】战利品表注入处理器
//
// 功能：
//   通过 LootTableLoadEvent 向指定战利品表中注入附魔书池，
//   实现附魔的精确获取途径控制。
//
// 注入规则：
//   - 沙漠神殿（chests/desert_pyramid）→ 引雷 II
//   - 废弃矿洞（chests/abandoned_mineshaft）→ 引雷 II
//   - 钓鱼（gameplay/fishing）→ 引雷 II + 引雷 III
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID)
public class ChannelingLootHandler {

    // 目标战利品表 ID（使用双参构造器，避免单参过时警告）
    // 注：1.20.1 中 ResourceLocation 双参构造器已过时，但 of(String, String) 尚不存在，
    // 因此使用 of(location, ':') 配合冒号格式的字符串
    private static final ResourceLocation FISHING = ResourceLocation.of("minecraft:gameplay/fishing", ':');
    private static final ResourceLocation DESERT_PYRAMID = ResourceLocation.of("minecraft:chests/desert_pyramid", ':');
    private static final ResourceLocation ABANDONED_MINESHAFT = ResourceLocation.of("minecraft:chests/abandoned_mineshaft", ':');

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();

        // ================================================================
        // 钓鱼战利品表：引雷 II + 引雷 III
        // ================================================================
        if (FISHING.equals(name)) {
            addPoolIfNotNull(event,
                    "channeling_ii_fishing",           // 池名，用于唯一标识
                    ModEnchantments.CHANNELING_II.get(), // 附魔
                    1,                                  // 附魔等级
                    0.08f                               // 生成概率（8%，与稀有附魔匹配）
            );
            addPoolIfNotNull(event,
                    "channeling_iii_fishing",
                    ModEnchantments.CHANNELING_III.get(),
                    1,
                    0.04f                               // 生成概率（4%，终极版更稀有）
            );
        }

        // ================================================================
        // 沙漠神殿宝箱：引雷 II
        // ================================================================
        else if (DESERT_PYRAMID.equals(name)) {
            addPoolIfNotNull(event,
                    "channeling_ii_desert",
                    ModEnchantments.CHANNELING_II.get(),
                    1,
                    0.12f                               // 生成概率（12%，沙漠神殿）
            );
        }

        // ================================================================
        // 废弃矿洞宝箱：引雷 II
        // ================================================================
        else if (ABANDONED_MINESHAFT.equals(name)) {
            addPoolIfNotNull(event,
                    "channeling_ii_mineshaft",
                    ModEnchantments.CHANNELING_II.get(),
                    1,
                    0.10f                               // 生成概率（10%，废弃矿洞）
            );
        }
    }

    // ========================================================================
    // 构建附魔书 LootPool
    //
    // 参数：
    //   poolName     — 池名称（用于调试和唯一性）
    //   enchantment  — 附魔
    //   level       — 附魔等级
    //   chance      — 该池生成的概率（0.0 ~ 1.0）
    //
    // 构建逻辑：
    //   1. 通过 ForgeRegistries 获取附魔的注册 ID
    //   2. 手动构建附魔书的 NBT（StoredEnchantments 列表）
    //   3. 使用 SetNbtFunction 将 NBT 应用到附魔书上
    //   4. 通过 setRolls 的小数部分控制生成概率
    //
    // 【概率实现原理】
    //   LootPool 的 rolls 决定尝试 roll 的次数：
    //     - rolls = 1.0 → 每次 roll 1 次（必出）
    //     - rolls = 0.08 → 8% 概率 roll 1 次（整数部分为 0，小数部分为概率）
    //   pool 内只有 1 个 entry 时，setWeight 不影响概率（必然选中），
    //   因此概率控制必须通过 setRolls 的小数部分实现。
    //
    // 注：不使用 SetEnchantmentsFunction 是因为 1.20.1 中
    //   其 setEnchantments(Map) 静态方法无法被正确解析
    // ========================================================================
    private static LootPool buildEnchantedBookPool(String poolName,
                                                    Enchantment enchantment,
                                                    int level,
                                                    float chance) {
        // 获取附魔的注册 ID（如 "enchantment_expansion:channeling_ii"）
        ResourceLocation enchId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchId == null) {
            // 正常情况下不会为 null，防御性检查
            return null;
        }

        // 构建附魔书 NBT 结构：
        // {StoredEnchantments:[{id:"<modid>:<enchantment_name>", lvl:<level>}]}
        CompoundTag enchEntry = new CompoundTag();
        enchEntry.putString("id", enchId.toString());
        enchEntry.putInt("lvl", level);

        ListTag storedEnchantments = new ListTag();
        storedEnchantments.add(enchEntry);

        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", storedEnchantments);

        return LootPool.lootPool()
                .name(poolName)
                .setRolls(ConstantValue.exactly(chance))  // 小数 rolls 实现概率控制
                .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(SetNbtFunction.setTag(tag))  // 通过 NBT 设置附魔
                )
                .build();
    }

    // ========================================================================
    // 安全地添加战利品池（处理 buildEnchantedBookPool 返回 null 的情况）
    // ========================================================================
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