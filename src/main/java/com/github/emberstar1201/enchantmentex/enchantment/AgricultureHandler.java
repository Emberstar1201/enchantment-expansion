package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.AgricultureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【农业生产系】附魔事件处理器
//
// 处理三个农业附魔：
//   A. 春华秋实（spring_harvest）：右键一键开垦 + 一键收获补种
//   B. 万物回春（all_nature_revive）：右键未成熟作物 → 范围催熟
//   C. 丰饶之息（fertile_bounty）：手持光环催熟 + 收获翻倍
//
// 右键分流逻辑（同一把锄头可同附春华秋实+万物回春+丰饶之息）：
//   中心方块是成熟作物  → 春华秋实：范围收获+补种（有丰饶之息则翻倍）
//   中心方块是未成熟作物 → 万物回春：范围催熟
//   其他（可耕地）      → 春华秋实：范围开垦
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AgricultureHandler {

    // ========================================================================
    // 事件1：右键方块 → 开垦 / 收获 / 催熟 分流
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (player.level().isClientSide()) return; // 仅服务端处理

        // 仅主手锄头生效
        ItemStack hoe = player.getMainHandItem();
        if (hoe.isEmpty()) return;

        int springLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.SPRING_HARVEST.get(), hoe);
        int reviveLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ALL_NATURE_REVIVE.get(), hoe);
        int bountyLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FERTILE_BOUNTY.get(), hoe);
        if (springLevel <= 0 && reviveLevel <= 0) return;

        BlockPos center = event.getPos();
        BlockState centerState = player.level().getBlockState(center);
        Level level = player.level();

        boolean handled = false;

        // ================================================================
        // 分支1：中心是成熟作物 → 春华秋实范围收获+补种
        // ================================================================
        if (springLevel > 0 && centerState.getBlock() instanceof CropBlock crop
                && crop.isMaxAge(centerState)) {
            handled = harvestArea(level, player, center, springLevel, bountyLevel, hoe);
        }
        // ================================================================
        // 分支2：中心是未成熟作物 → 万物回春范围催熟
        // ================================================================
        else if (reviveLevel > 0 && centerState.getBlock() instanceof CropBlock
                && !((CropBlock) centerState.getBlock()).isMaxAge(centerState)) {
            handled = reviveArea(level, player, center, reviveLevel);
        }
        // ================================================================
        // 分支3：中心是可耕地 → 春华秋实范围开垦
        // ================================================================
        else if (springLevel > 0 && isTillable(level, centerState)) {
            handled = tillArea(level, player, center, springLevel, hoe);
        }

        if (handled) {
            // 取消原版右键交互，防止锄头原版逻辑重复执行
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    // ========================================================================
    // 事件2：TickEvent → 丰饶之息生长光环
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (!player.level().isClientSide()) return;

        // 主手锄头附魔检查
        ItemStack hoe = player.getMainHandItem();
        int bountyLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.FERTILE_BOUNTY.get(), hoe);
        if (bountyLevel <= 0) return;

        // 间隔定时光环
        if (player.tickCount % AgricultureConfig.fertileBountyGrowthIntervalTicks != 0) return;

        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        int radius = AgricultureConfig.fertileBountyAuraRadius;
        double chance = AgricultureConfig.getFertileBountyGrowthChance(bountyLevel);
        if (chance <= 0) return;

        // 扫描含玩家中心 ±半径 的立方体（Y 方向取玩家所在水平 ±4 格，减少开销）
        int yMin = (int) Math.floor(player.getY()) - 4;
        int yMax = (int) Math.ceil(player.getY()) + 4;
        int grown = 0;
        BlockPos minPos = new BlockPos(player.getBlockX() - radius, yMin, player.getBlockZ() - radius);
        BlockPos maxPos = new BlockPos(player.getBlockX() + radius, yMax, player.getBlockZ() + radius);
        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)
                    && serverLevel.random.nextDouble() < chance) {
                crop.growCrops(serverLevel, pos, state);
                grown++;
            }
        }
        // 有作物被催熟时，在玩家脚下播一点绿色粒子反馈
        if (grown > 0) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    Math.min(grown, 10), 1.5, 1.0, 1.5, 0);
        }
    }

    // ========================================================================
    // 工具：范围开垦
    // ========================================================================
    private static boolean tillArea(Level level, Player player, BlockPos center,
                                    int level_2, ItemStack hoe) {
        int radius = AgricultureConfig.getSpringHarvestRadius(level_2);
        if (radius <= 0) return false;
        int durabilityCost = AgricultureConfig.springHarvestDurabilityPerTillBlock;

        ServerLevel serverLevel = (ServerLevel) level;
        boolean any = false;
        int count = 0;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!isTillable(level, state)) continue;

            // 执行原版开垦：直接替换为对应的开垦产物
            BlockState tilled = getTilledState(level, pos, state);
            if (tilled == null) continue;

            level.setBlock(pos, tilled, 3);
            level.levelEvent(2001, pos, Block.getId(tilled));
            level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            any = true;
            count++;
        }

        if (any) {
            // 消耗锄头耐久（每块 1 点）
            if (durabilityCost > 0 && !player.isCreative()) {
                hoe.hurtAndBreak(durabilityCost * count, player,
                        p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
            }
            // 播放一次清脆开垦音效
            serverLevel.playSound(null, center, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.2F);
            return true;
        }
        return false;
    }

    /** 判断方块是否可被锄头开垦（参照原版 HoeItem.TILLABLES） */
    private static boolean isTillable(Level level, BlockState state) {
        return state.getBlock() == Blocks.DIRT
                || state.getBlock() == Blocks.GRASS_BLOCK
                || state.getBlock() == Blocks.DIRT_PATH
                || state.getBlock() == Blocks.COARSE_DIRT
                || state.getBlock() == Blocks.ROOTED_DIRT;
    }

    /** 返回开垦后的方块状态 */
    private static BlockState getTilledState(Level level, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.GRASS_BLOCK) {
            return Blocks.FARMLAND.defaultBlockState();
        }
        if (b == Blocks.DIRT_PATH) {
            return Blocks.FARMLAND.defaultBlockState();
        }
        if (b == Blocks.DIRT
                || b == Blocks.COARSE_DIRT
                || b == Blocks.ROOTED_DIRT) {
            // 上方有方块时不能变成耕地
            if (!level.getBlockState(pos.above()).isAir()) {
                return null;
            }
            return Blocks.FARMLAND.defaultBlockState();
        }
        return null;
    }

    // ========================================================================
    // 工具：范围收获 + 补种（春华秋实）
    // ========================================================================
    private static boolean harvestArea(Level level, Player player, BlockPos center,
                                       int springLevel, int bountyLevel, ItemStack hoe) {
        int radius = AgricultureConfig.getSpringHarvestRadius(springLevel);
        if (radius <= 0) return false;

        ServerLevel serverLevel = (ServerLevel) level;
        boolean any = false;
        boolean doubleDrop = bountyLevel > 0 && AgricultureConfig.fertileBountyDoubleHarvest;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;

            // 生成掉落物（不破坏方块，避免二次掉落）
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null, player, hoe);
            if (doubleDrop) {
                drops = doubleDrops(drops);
            }

            boolean replanted = false;
            if (AgricultureConfig.springHarvestReplant) {
                // 1.20.1 CropBlock 的种子方法为 protected getBaseSeedId()，
                // 无法跨包访问，这里按作物类型映射出对应的种子物品
                Item seed = getSeedItemFor(crop);
                for (ItemStack drop : drops) {
                    if (!replanted && seed != null && drop.getItem() == seed && drop.getCount() > 0) {
                        drop.shrink(1);
                        replanted = true;
                        break;
                    }
                }
            }

            // 掉落物给玩家，背包满则落到作物位置上方（手工生成 ItemEntity）
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                if (!player.addItem(drop)) {
                    ItemEntity itemEntity = new ItemEntity(serverLevel,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                    itemEntity.setDefaultPickUpDelay();
                    serverLevel.addFreshEntity(itemEntity);
                }
            }

            // 补种或清除
            if (replanted) {
                level.setBlock(pos, crop.getStateForAge(0), 3);
            } else {
                level.destroyBlock(pos, false); // 无种子可补，移除且不额外掉落
            }

            // 收获音效（在玩家位置播放一次作物折断音）
            serverLevel.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
            any = true;
        }

        if (any && player instanceof ServerPlayer serverPlayer) {
            // 统计成就在原版没有对应，保持简单：仅播放收获完成音效
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                    8, radius, 0.5, radius, 0);
        }
        return any;
    }

    /** 掉落翻倍 */
    private static List<ItemStack> doubleDrops(List<ItemStack> drops) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemStack copy = drop.copy();
            copy.setCount(drop.getCount() * 2);
            result.add(copy);
        }
        return result;
    }

    // ========================================================================
    // 工具：范围催熟（万物回春）
    // ========================================================================
    private static boolean reviveArea(Level level, Player player, BlockPos center, int reviveLevel) {
        int radius = AgricultureConfig.getAllNatureReviveRadius(reviveLevel);
        double chance = AgricultureConfig.getAllNatureReviveChance(reviveLevel);
        if (radius <= 0 || chance <= 0) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        boolean any = false;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state)) continue;
            if (level.random.nextDouble() >= chance) continue;

            crop.growCrops(serverLevel, pos, state); // 原版骨粉跳阶段逻辑
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0);
            any = true;
        }

        if (any) {
            serverLevel.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return any;
    }

    // ========================================================================
    // 工具：按作物类型获取补种种子（1.20.1 的 getSeedItem 不可跨包访问）
    // ========================================================================
    private static Item getSeedItemFor(CropBlock crop) {
        // 1.20.1 的作物块：胡罗卜/马铃薯/甜菜有独立子类，小麦没有独立类（直接用 CropBlock）
        if (crop instanceof CarrotBlock) return Items.CARROT;
        if (crop instanceof PotatoBlock) return Items.POTATO;
        if (crop instanceof BeetrootBlock) return Items.BEETROOT_SEEDS;
        // 原版小麦无独立子类；模组自定义作物也归到这里。补种时按掉落物查找匹配，
        // 若掉落中不含小麦种子（模组作物），则无法补种，会按原版逻辑移除该作物
        return Items.WHEAT_SEEDS;
    }
}