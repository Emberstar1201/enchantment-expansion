package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

// ========================================================================
// 匠心传承 NBT 数据存储工具类
//
// 将"已记忆方块"的数据持久化存储在工具的 NBT 中。
// NBT 结构：
//   ArtisanLegacyData: {
//     learnedBlocks: [
//       {blockId: "minecraft:stone", breakCount: 50},
//       {blockId: "minecraft:iron_ore", breakCount: 30},
//       ...
//     ]
//   }
//
// 数据随工具存储，丢出、存放、交易均不丢失。
// ========================================================================
public class ArtisanLegacyData {

    // NBT 顶层键名
    private static final String TAG_ROOT = "ArtisanLegacyData";
    // 记忆方块列表的键名
    private static final String KEY_BLOCKS = "learnedBlocks";
    // 每个方块条目的字段
    private static final String KEY_BLOCK_ID = "blockId";
    private static final String KEY_BREAK_COUNT = "breakCount";

    // ========================================================================
    // 记录一次方块破坏
    //
    // 如果该方块已记忆，增加其 breakCount；
    // 如果是新方块，添加新条目（超出最大种类数时移除最旧条目）。
    // ========================================================================
    public static void recordBreak(ItemStack tool, Block block) {
        CompoundTag rootTag = tool.getOrCreateTagElement(TAG_ROOT);
        ListTag blocksList = rootTag.getList(KEY_BLOCKS, Tag.TAG_COMPOUND);

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        String idStr = blockId.toString();

        // 查找是否已记忆该方块
        for (int i = 0; i < blocksList.size(); i++) {
            CompoundTag entry = blocksList.getCompound(i);
            if (entry.getString(KEY_BLOCK_ID).equals(idStr)) {
                // 已记忆 → 增加计数
                entry.putInt(KEY_BREAK_COUNT, entry.getInt(KEY_BREAK_COUNT) + 1);
                rootTag.put(KEY_BLOCKS, blocksList);
                return;
            }
        }

        // 新方块：检查是否超出记忆上限
        int maxMemory = Config.artisanLegacyMaxMemory;
        if (blocksList.size() >= maxMemory) {
            // 移除最旧（第一个）条目，腾出空间
            blocksList.remove(0);
        }

        // 添加新条目
        CompoundTag newEntry = new CompoundTag();
        newEntry.putString(KEY_BLOCK_ID, idStr);
        newEntry.putInt(KEY_BREAK_COUNT, 1);
        blocksList.add(newEntry);
        rootTag.put(KEY_BLOCKS, blocksList);
    }

    // ========================================================================
    // 获取对某方块的挖掘次数（未记忆返回 0）
    // ========================================================================
    public static int getBreakCount(ItemStack tool, Block block) {
        CompoundTag rootTag = tool.getTagElement(TAG_ROOT);
        if (rootTag == null) return 0;

        ListTag blocksList = rootTag.getList(KEY_BLOCKS, Tag.TAG_COMPOUND);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        String idStr = blockId.toString();

        for (int i = 0; i < blocksList.size(); i++) {
            CompoundTag entry = blocksList.getCompound(i);
            if (entry.getString(KEY_BLOCK_ID).equals(idStr)) {
                return entry.getInt(KEY_BREAK_COUNT);
            }
        }
        return 0;
    }

    // ========================================================================
    // 根据挖掘次数计算速度倍率
    //
    // 使用配置中的阶梯阈值进行线性插值：
    //   例如阈值 [10, 25, 50, 100]：
    //     0次  → 1.0x（无加成）
    //     10次 → 1.25x（25%提升）
    //     25次 → 1.5x（50%提升）
    //     50次 → 1.75x（75%提升）
    //     100+次 → 2.0x（最大，100%提升）
    // ========================================================================
    public static float getSpeedMultiplier(ItemStack tool, Block block) {
        int breakCount = getBreakCount(tool, block);
        if (breakCount <= 0) return 1.0f;

        int[] thresholds = Config.artisanLegacyThresholds;
        double maxMultiplier = Config.artisanLegacyMaxSpeedMultiplier;

        // 若 breakCount 达到或超过最后一个阈值，返回最大倍率
        if (breakCount >= thresholds[thresholds.length - 1]) {
            return (float) maxMultiplier;
        }

        // 找到所在区间，线性插值
        for (int i = 1; i < thresholds.length; i++) {
            if (breakCount < thresholds[i]) {
                // 区间 [thresholds[i-1], thresholds[i])
                // 对应倍率区间
                double prevMultiplier = 1.0 + (maxMultiplier - 1.0) * (i - 1) / (thresholds.length - 1);
                double currMultiplier = 1.0 + (maxMultiplier - 1.0) * i / (thresholds.length - 1);

                float t = (float) (breakCount - thresholds[i - 1])
                        / (thresholds[i] - thresholds[i - 1]);
                return (float) (prevMultiplier + (currMultiplier - prevMultiplier) * t);
            }
        }

        return (float) maxMultiplier;
    }

    // ========================================================================
    // 判断是否触发额外掉落
    //
    // 概率 = maxChance * min(1.0, breakCount / maxThreshold)
    // 即挖掘次数越多，越接近最大概率
    // ========================================================================
    public static boolean rollExtraDrop(ItemStack tool, Block block, RandomSource random) {
        int breakCount = getBreakCount(tool, block);
        if (breakCount <= 0) return false;

        double maxChance = Config.artisanLegacyExtraDropChance;
        int maxThreshold = Config.artisanLegacyMaxExtraDropThreshold;

        // 概率随 breakCount 线性增长，达到 maxThreshold 后封顶
        double chance = maxChance * Math.min(1.0, (double) breakCount / maxThreshold);
        return random.nextDouble() < chance;
    }

    // ========================================================================
    // 获取已记忆的方块数量
    // ========================================================================
    public static int getMemoryCount(ItemStack tool) {
        CompoundTag rootTag = tool.getTagElement(TAG_ROOT);
        if (rootTag == null) return 0;
        return rootTag.getList(KEY_BLOCKS, Tag.TAG_COMPOUND).size();
    }
}