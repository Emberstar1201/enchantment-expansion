package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.ChainBreakerConfig;
import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// ========================================================================
// 【连锁挖掘】服务端事件处理器
//
// 事件：BlockEvent.BreakEvent
// 原理：
//   1. 客户端按住 ~ 键时发送 C2S 数据包，把玩家 UUID 加入到连锁模式集合
//   2. 玩家挖掘方块触发 BreakEvent 时，检查：
//      - 主手工具是否带「连锁挖掘」附魔
//      - 玩家是否处于连锁模式（按住 ~ 键）
//   3. 围绕被挖掘方块，在附魔等级对应的立方体范围内，
//      仅连锁破坏与目标方块相同类型的方块
//   4. 掉落物走原版 playerDestroy 逻辑（正常掉落，含精准采集/时运）
//   5. 每个额外方块消耗 0.5 耐久，单次最大消耗 ≤ 10（低耐久保护）
//
// 玩家登出时清理连锁模式记录，防止状态残留
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChainBreakerHandler {

    // 处于"连锁模式"（按住 ~ 键）的玩家 UUID 集合
    private static final Set<UUID> CHAIN_MODE_PLAYERS = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------
    // 按键状态：数据包回调入口
    // ------------------------------------------------------------------
    public static void setChainMode(UUID uuid, boolean active) {
        if (active) {
            CHAIN_MODE_PLAYERS.add(uuid);
        } else {
            CHAIN_MODE_PLAYERS.remove(uuid);
        }
    }

    /** 该玩家当前是否按住触发键 */
    public static boolean isChainMode(UUID uuid) {
        return CHAIN_MODE_PLAYERS.contains(uuid);
    }

    // ------------------------------------------------------------------
    // 玩家登出 → 清理连锁模式标记
    // ------------------------------------------------------------------
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Entity entity = event.getEntity();
        if (entity != null) {
            CHAIN_MODE_PLAYERS.remove(entity.getUUID());
        }
    }

    // ========================================================================
    // 核心：连锁挖掘
    // ========================================================================
    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;

        // 附魔未启用或玩家未按住触发键 → 走原版
        if (!ChainBreakerConfig.isEnabled()) return;
        if (!isChainMode(player.getUUID())) return;

        ItemStack tool = player.getMainHandItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.CHAIN_BREAKER.get(), tool);
        if (level <= 0) return;

        Level level_ = player.level();
        if (!(level_ instanceof ServerLevel serverLevel)) return;

        BlockPos origin = event.getPos();
        BlockState targetState = event.getState();
        Block targetBlock = targetState.getBlock();

        int radius = ChainBreakerConfig.getRadius(level);
        if (radius <= 0) return;

        int broken = 0;              // 额外连锁破坏的方块数
        BlockPos minPos = origin.offset(-radius, -radius, -radius);
        BlockPos maxPos = origin.offset(radius, radius, radius);

        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            // 中心方块已由原版破坏流程处理，跳过
            if (pos.equals(origin)) continue;

            BlockState state = level_.getBlockState(pos);
            // 仅连锁破坏与目标方块同类型的方块
            if (state.isAir() || state.getBlock() != targetBlock) continue;

            // 可挖掘性检查：destroySpeed < 0 表示不可破坏（如基岩、黑曜石）
            if (state.getDestroySpeed(level_, pos) < 0.0F) continue;

            // 破坏方块：playerDestroy 走原版掉落逻辑（含精准采集/时运），
            // removeBlock(false) 防止二次掉落
            BlockEntity blockEntity = level_.getBlockEntity(pos);
            targetBlock.playerDestroy(level_, player, pos, state, blockEntity, tool);
            level_.removeBlock(pos, false);

            // 播放原版方块破坏粒子与音效
            serverLevel.levelEvent(2001, pos, Block.getId(state));
            broken++;
        }

        // 耐久消耗：每个额外方块 0.5 耐久，向上取整、最低 1，单次封顶 maxDurabilityCost
        if (broken > 0 && !player.isCreative()) {
            double rawCost = broken * ChainBreakerConfig.chainBreakerDurabilityPerBlock;
            int cost = Math.max(1, (int) Math.ceil(rawCost));
            cost = Math.min(cost, ChainBreakerConfig.chainBreakerMaxDurabilityCost);
            tool.hurtAndBreak(cost, player,
                    p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }
}