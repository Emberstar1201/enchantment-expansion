package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "自动冶炼"附魔事件处理器
//
// 【原理】
//   监听 BlockEvent.BreakEvent，当玩家使用有"自动冶炼"附魔的镐挖掘矿石时，
//   拦截默认掉落流程，手动计算掉落物并将粗矿替换为冶炼成品。
//
//   在 Forge 1.20.1 中没有 BlockDropItemEvent，因此采用
//   "取消 BreakEvent → 手动获取掉落 → 替换为成品 → 生成掉落"的模式。
//   此模式已在 CreationFromNothingHandler 中得到验证。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AutoSmeltHandler {

    // ========================================================================
    // 粗矿 → 成品 映射表（硬编码，原版稳定）
    // ========================================================================
    private static final Map<Item, Item> SMELT_MAP = new HashMap<>();

    static {
        SMELT_MAP.put(Items.RAW_IRON, Items.IRON_INGOT);
        SMELT_MAP.put(Items.RAW_COPPER, Items.COPPER_INGOT);
        SMELT_MAP.put(Items.RAW_GOLD, Items.GOLD_INGOT);
        // 远古残骸掉落自身（不经过粗矿），但同样会触发冶炼映射
        SMELT_MAP.put(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        // 检查附魔等级
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.AUTO_SMELT.get(), tool);
        if (enchantLevel <= 0) return;

        // 检查破坏的方块是否在配置的"矿石列表"中
        ResourceLocation brokenBlockId = BuiltInRegistries.BLOCK.getKey(
                event.getState().getBlock());
        if (!Config.autoSmeltOreBlocks.contains(brokenBlockId.toString())) {
            return;
        }

        // ========================================================================
        // 取消默认破坏流程（防止默认掉落物和经验生成）
        // ========================================================================
        event.setCanceled(true);

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        // 由于已经 clientSide 过滤，此处可安全转型
        ServerLevel serverLevel = (ServerLevel) level;

        // 获取方块默认掉落物（传入工具以支持时运等级等）
        ItemStack toolCopy = player.getMainHandItem();
        List<ItemStack> originalDrops = Block.getDrops(state, serverLevel, pos,
                null, player, toolCopy);

        // ========================================================================
        // 替换粗矿为冶炼成品，并计算额外掉落
        // ========================================================================
        List<ItemStack> finalDrops = new ArrayList<>();

        for (ItemStack drop : originalDrops) {
            Item smelted = SMELT_MAP.get(drop.getItem());
            if (smelted != null) {
                int count = drop.getCount();

                // II级：33%概率额外+1
                if (enchantLevel >= 2
                        && level.random.nextDouble() < Config.autoSmeltLevel2BonusChance) {
                    count += 1;
                }

                // III级：50%概率额外+1~2
                if (enchantLevel >= 3
                        && level.random.nextDouble() < Config.autoSmeltLevel3BonusChance) {
                    count += 1 + level.random.nextInt(2);
                }

                finalDrops.add(new ItemStack(smelted, count));
            } else {
                // 非粗矿物品（如经验瓶、特殊掉落），保持原样
                finalDrops.add(drop.copy());
            }
        }

        // ========================================================================
        // 设置方块为空气，播放破坏效果
        // ========================================================================
        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        serverLevel.levelEvent(2001, pos, Block.getId(state));

        // ========================================================================
        // 生成最终掉落物
        // ========================================================================
        for (ItemStack stack : finalDrops) {
            Block.popResource(serverLevel, pos, stack);
        }

        // ========================================================================
        // 经验补偿
        // ========================================================================
        double xpPerBlock = Config.autoSmeltXpMin
                + level.random.nextDouble() * (Config.autoSmeltXpMax - Config.autoSmeltXpMin);
        int totalXp = Math.max(1, (int) Math.floor(xpPerBlock));
        if (totalXp > 0) {
            serverLevel.addFreshEntity(new ExperienceOrb(serverLevel,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    totalXp));
        }
    }
}