package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【无中生有】附魔事件处理器
//
// 核心机制：
//   玩家用带有"无中生有"附魔的工具破坏方块时，若使用的是该方块的
//   正确工具（isCorrectToolForDrops），则按附魔等级概率额外掉落稀有物品。
//   额外掉落独立于方块本身的掉落物，不影响原版掉落逻辑。
//
// 掉落概率（按等级）：
//   每级 2%，即 Lv1: 2%   Lv2: 4%   Lv3: 6%
//
// 掉落池（根据方块标签区分）：
//   ┌─ base_stone_overworld 标签（石头/深板岩/花岗岩等）
//   │   需要正确工具：镐
//   │   钻石          权重 60（60%）
//   │   下界合金碎片   权重 40（40%）
//   │
//   └─ leaves 标签（各种树叶）
//       需要正确工具：锄（或任何能获取掉落的工具）
//       金苹果        权重 70（70%）
//       附魔金苹果     权重 30（30%）
//
// 【正确工具判定】使用 ItemStack.isCorrectToolForDrops(BlockState)
//   例如：用斧破坏石头不满足条件（石头需要镐），不会触发额外掉落
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class CreationFromNothingHandler {

    // 【每级掉落概率】概率 = 0.02 × 附魔等级
    private static final double DROP_CHANCE_PER_LEVEL = 0.02;

    // 【石头类方块掉落池】base_stone_overworld 标签方块触发
    // 钻石较常见，下界合金碎片较稀有
    private static final ItemStack[] STONE_DROPS = {
            new ItemStack(Items.DIAMOND),            // 钻石
            new ItemStack(Items.NETHERITE_SCRAP)      // 下界合金碎片
    };
    private static final int[] STONE_WEIGHTS = {60, 40};
    private static final int STONE_TOTAL_WEIGHT = 100;

    // 【树叶类方块掉落池】leaves 标签方块触发
    // 金苹果较常见，附魔金苹果较稀有
    private static final ItemStack[] LEAVES_DROPS = {
            new ItemStack(Items.GOLDEN_APPLE),            // 金苹果
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)   // 附魔金苹果
    };
    private static final int[] LEAVES_WEIGHTS = {70, 30};
    private static final int LEAVES_TOTAL_WEIGHT = 100;

    // 监听方块破坏事件
    // BlockEvent.BreakEvent 在玩家破坏方块时触发（服务端）
    // 在方块被移除之前触发，可获取方块状态和玩家手持工具
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 获取世界对象，必须是 Level（非纯 LevelAccessor）才处理
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }

        // 仅服务端处理（掉落物只在服务端生成）
        if (level.isClientSide()) {
            return;
        }

        Player player = event.getPlayer();
        // 创造模式不触发额外掉落（避免无意义刷物品）
        if (player.isCreative()) {
            return;
        }

        // 检查主手工具是否带有"无中生有"附魔
        ItemStack tool = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.CREATION_FROM_NOTHING.get(), tool);
        if (enchantLevel <= 0) {
            return;
        }

        BlockState state = event.getState();

        // 【正确工具判定】必须使用该方块的正确工具才能触发
        // 例如：用斧破坏石头不满足条件（石头需要镐），不会触发额外掉落
        if (!tool.isCorrectToolForDrops(state)) {
            return;
        }

        // 根据方块标签选择对应的掉落池
        ItemStack[] drops;
        int[] weights;
        int totalWeight;

        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            // 石头类方块：掉落钻石或下界合金碎片
            drops = STONE_DROPS;
            weights = STONE_WEIGHTS;
            totalWeight = STONE_TOTAL_WEIGHT;
        } else if (state.is(BlockTags.LEAVES)) {
            // 树叶类方块：掉落金苹果或附魔金苹果
            drops = LEAVES_DROPS;
            weights = LEAVES_WEIGHTS;
            totalWeight = LEAVES_TOTAL_WEIGHT;
        } else {
            // 不在任何目标标签内，不触发
            return;
        }

        // 按等级计算掉落概率：每级 2%
        double chance = DROP_CHANCE_PER_LEVEL * enchantLevel;

        // 概率判定：未通过则不掉落
        if (level.random.nextDouble() >= chance) {
            return;
        }

        // 按权重随机选择一件稀有物品
        int roll = level.random.nextInt(totalWeight);
        ItemStack drop = pickWeightedDrop(drops, weights, roll);

        // 在方块位置生成掉落物
        BlockPos pos = event.getPos();
        Block.popResource(level, pos, drop);
    }

    // 按权重随机选择一件稀有物品
    // roll 为 0 ~ totalWeight-1 的随机数
    // 依次累加权重，roll 落在哪个区间就返回对应物品
    private static ItemStack pickWeightedDrop(ItemStack[] drops, int[] weights, int roll) {
        int cumulative = 0;
        for (int i = 0; i < drops.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return drops[i].copy();
            }
        }
        // 兜底：返回第一个物品（理论上不会走到这里）
        return drops[0].copy();
    }
}
