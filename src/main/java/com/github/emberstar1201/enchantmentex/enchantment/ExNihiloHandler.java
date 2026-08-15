package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【无中生有·重制】附魔事件处理器
//
// 【设计原理】
//   我们 不取消 BlockEvent.BreakEvent，而是让 Minecraft 原版破坏流程
//   正常执行（包括原版掉落物、经验等）。我们仅在 BreakEvent 中额外
//   Block.popResource 一次，添加无中生有的稀有/常规掉落。
//
//   为什么 v3（取消事件 + 手动 setAir）不行？
//     当取消 BreakEvent 后，ServerPlayerGameMode.destroyBlock() 返回
//     false，服务端会强制向客户端发送方块状态同步包，覆盖我们手动设置的
//     AIR 状态。客户端看到方块还在，导致生成的掉落物卡在方块内部，
//     玩家什么都看不到。此外 Block.getDrops() 在已取消的事件上下文中
//     也可能因 LootTable 参数不完整返回空列表。
//
//   为什么不取消事件只加额外掉落就行？
//     经过 Forge 事件总线的 BreakEvent 是一个"检查点"：所有处理器执行
//     完毕后，如果事件未被取消，Minecraft 会正常破坏方块并生成掉落。
//     我们只需要在 LOW 优先级（晚于大多数处理器）检查事件未被取消，
//     然后 popResource 添加额外掉落，剩余工作交给原版流程。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ExNihiloHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 使用 LOW 优先级，确保在 AutoSmelt 等 NORMAL 优先级处理器之后执行
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 如果已被更高优先级的处理器取消（如自动冶炼），跳过
        if (event.isCanceled()) {
            LOGGER.info("[ExNihilo] event already canceled by higher priority, skipping");
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        // 仅服务端处理
        Level level = player.level();
        if (level.isClientSide()) return;

        // 创造模式不触发
        if (player.isCreative()) return;

        // ================================================================
        // 剪刀模式：采集树叶时概率额外掉落金苹果（不取消事件）
        // ================================================================
        ItemStack tool = player.getMainHandItem();
        if (tool.is(Items.SHEARS)) {
            int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.EX_NIHILO.get(), tool);
            if (enchantLevel <= 0) return;

            BlockState state = event.getState();
            if (state.is(BlockTags.LEAVES)) {
                handleShearDrop(level, event.getPos(), enchantLevel);
            }
            return;
        }

        // ================================================================
        // 镐模式：挖掘石质方块时额外爆出矿物
        // ================================================================

        // 检查"无中生有·重制"附魔等级
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.EX_NIHILO.get(), tool);
        if (enchantLevel <= 0) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        // 检查工具是否是该方块的正确工具
        if (!tool.isCorrectToolForDrops(state)) return;

        // 检查被破坏方块是否在配置的"石质方块列表"中
        String blockIdStr = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!Config.exNihiloStoneBlocks.contains(blockIdStr)) return;

        if (!(level instanceof ServerLevel serverLevel)) return;

        // ================================================================
        // ★ 不取消事件，仅添加额外掉落 ★
        // 原版掉落物（如石头→圆石）由 Minecraft 正常破坏流程处理
        // ================================================================

        // 获得无中生有的额外掉落（稀有/常规池）
        ItemStack exNihiloDrop = rollDrop(serverLevel, enchantLevel);
        LOGGER.info("[ExNihilo] block={} extraDrop={} count={}",
                blockIdStr, exNihiloDrop.getItem(), exNihiloDrop.getCount());

        if (!exNihiloDrop.isEmpty()) {
            // 在方块位置生成额外掉落物
            Block.popResource(level, pos, exNihiloDrop);
        }

        // 给玩家发送动作栏提示（仅限 ServerPlayer）
        if (player instanceof ServerPlayer sp && !exNihiloDrop.isEmpty()) {
            String dropName = exNihiloDrop.getHoverName().getString();
            sp.sendSystemMessage(Component.literal(
                    "§7[无中生有] §f+" + exNihiloDrop.getCount() + "x " + dropName
            ), true);
        }

        LOGGER.info("[ExNihilo] done: extra drop added without canceling event");
    }

    // ========================================================================
    // 掉落判定（"稀有/常规"双层结构）
    // ========================================================================
    private static ItemStack rollDrop(ServerLevel level, int enchantLevel) {
        double rareChance = Config.exNihiloRareChancePerLevel * enchantLevel;

        if (level.random.nextDouble() < rareChance) {
            LOGGER.info("[ExNihilo] RARE drop triggered! (chance={})", rareChance);
            return rollRareDrop(level);
        } else {
            LOGGER.info("[ExNihilo] common drop (rare chance={})", rareChance);
            return rollCommonDrop(level);
        }
    }

    // ========================================================================
    // 稀有池：钻石 / 下界合金碎片 / 下界合金锭（按权重）
    // ========================================================================
    private static ItemStack rollRareDrop(Level level) {
        int diamondW = Config.exNihiloRareDiamondWeight;
        int scrapW = Config.exNihiloRareScrapWeight;
        int ingotW = Config.exNihiloRareIngotWeight;
        int total = diamondW + scrapW + ingotW;

        int roll = level.random.nextInt(total);
        if (roll < diamondW) {
            return new ItemStack(Items.DIAMOND);
        } else if (roll < diamondW + scrapW) {
            return new ItemStack(Items.NETHERITE_SCRAP);
        } else {
            return new ItemStack(Items.NETHERITE_INGOT);
        }
    }

    // ========================================================================
    // 常规池：煤炭 / 铁锭 / 铜锭 / 紫水晶碎片 / 金粒 / 铁粒（等概率）
    // ========================================================================
    private static ItemStack rollCommonDrop(Level level) {
        ItemStack[] commonDrops = {
                new ItemStack(Items.COAL),
                new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.AMETHYST_SHARD),
                new ItemStack(Items.GOLD_NUGGET),
                new ItemStack(Items.IRON_NUGGET)
        };
        return commonDrops[level.random.nextInt(commonDrops.length)].copy();
    }

    // ========================================================================
    // 剪刀模式：采集树叶时概率额外掉落金苹果（不取消事件）
    // ========================================================================
    private static void handleShearDrop(Level level, BlockPos pos, int enchantLevel) {
        // 等级倍率：I→1.0, II→1.5, III→2.0
        double levelMultiplier = 1.0 + (enchantLevel - 1) * 0.5;

        // 金苹果判定
        if (level.random.nextDouble() < Config.exNihiloShearGoldenAppleChance * levelMultiplier) {
            Block.popResource(level, pos, new ItemStack(Items.GOLDEN_APPLE));
            LOGGER.info("[ExNihilo] shear mode: dropped golden apple");
        }

        // 附魔金苹果判定
        if (level.random.nextDouble() < Config.exNihiloShearEnchantedGoldenAppleChance * levelMultiplier) {
            Block.popResource(level, pos, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            LOGGER.info("[ExNihilo] shear mode: dropped enchanted golden apple");
        }
    }
}