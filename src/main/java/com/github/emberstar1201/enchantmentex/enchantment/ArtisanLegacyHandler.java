package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "匠心传承"附魔事件处理器
//
// 【监听事件】
//   1. BlockEvent.BreakEvent  → 记录方块 + 额外掉落判定
//   2. PlayerEvent.BreakSpeed → 对已记忆方块施加速度加成
//
// 在 Forge 1.20.1 中没有 BlockDropItemEvent，
// 因此额外掉落使用 BlockEvent.BreakEvent 配合 Block.popResource 实现。
// 此模式已在 CreationFromNothingHandler 中得到验证。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class ArtisanLegacyHandler {

    // ========================================================================
    // 事件1：方块破坏 → 记录到工具 NBT + 额外掉落判定
    // ========================================================================
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        // 检查是否有匠心传承附魔
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ARTISAN_LEGACY.get(), tool);

        // ★ 兼容：如果"中国制造"附魔在工具（镐/斧/锹）上，也触发匠心传承效果
        // 实现"中国制造"融合匠心传承的能力（仅 DIGGER 类工具才触发）
        if (enchantLevel <= 0
                && tool.getItem() instanceof DiggerItem
                && EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.MADE_IN_CHINA.get(), tool) > 0) {
            enchantLevel = 1;
        }
        if (enchantLevel <= 0) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        // ========================================================================
        // 步骤1：记录方块到工具 NBT
        // ========================================================================
        ArtisanLegacyData.recordBreak(tool, state.getBlock());

        // ========================================================================
        // 步骤2：额外掉落判定
        // ========================================================================
        if (ArtisanLegacyData.rollExtraDrop(tool, state.getBlock(), level.random)) {
            // 获取方块的默认掉落物，从中随机选一个作为额外掉落
            if (level instanceof ServerLevel serverLevel) {
                LootParams.Builder lootParams = new LootParams.Builder(serverLevel)
                        .withParameter(LootContextParams.ORIGIN, pos.getCenter())
                        .withParameter(LootContextParams.TOOL, tool)
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, player);

                List<ItemStack> possibleDrops = state.getDrops(lootParams);
                if (!possibleDrops.isEmpty()) {
                    ItemStack extraDrop = possibleDrops.get(
                            level.random.nextInt(possibleDrops.size())).copy();
                    extraDrop.setCount(1);

                    // 在方块位置生成额外掉落物
                    ItemEntity extraEntity = new ItemEntity(level,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            extraDrop);
                    extraEntity.setPickUpDelay(10);
                    level.addFreshEntity(extraEntity);
                }
            }
        }
    }

    // ========================================================================
    // 事件2：挖掘速度计算 → 对已记忆方块加速
    // ========================================================================
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) return;

        // 检查附魔
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ARTISAN_LEGACY.get(), tool);

        // ★ 兼容：如果"中国制造"附魔在工具（镐/斧/锹）上，也触发匠心传承效果
        if (level <= 0
                && tool.getItem() instanceof DiggerItem
                && EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.MADE_IN_CHINA.get(), tool) > 0) {
            level = 1;
        }
        if (level <= 0) return;

        // 获取正在挖掘的方块
        BlockState state = event.getState();
        if (state == null || state.isAir()) return;

        // 计算速度倍率并应用
        float multiplier = ArtisanLegacyData.getSpeedMultiplier(tool, state.getBlock());
        if (multiplier > 1.0f) {
            event.setNewSpeed(event.getNewSpeed() * multiplier);
        }
    }
}