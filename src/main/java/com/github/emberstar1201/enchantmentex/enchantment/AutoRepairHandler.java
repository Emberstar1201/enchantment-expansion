package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.AutoRepairConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

// ========================================================================
// 自动修复服务端逻辑
// 监听 ServerTickEvent，每个 END 阶段：
//   1. 遍历当前服务器所有玩家
//   2. 检查其手持 / 穿戴 / 副手物品是否带自动修复附魔
//   3. 按等级累加 tick 计数，达到间隔后恢复 1 点耐久
//
// 本 Handler 不使用 PlayerTickEvent，以覆盖多人服务端所有玩家，
// 同时避免依赖客户端 tick 导致的非同步问题。
// ========================================================================
public class AutoRepairHandler {

    // 记录已累加的 tick 数，格式：<玩家UUID哈希值 转物品索引> -> 累计tick
    // 由于每件物品的附魔等级可能不同，使用 ItemStack.hashCode() 作为 key 是不稳定的，
    // 因此改为按"玩家 + 装备槽索引"记录。
    private static final java.util.Map<String, Integer> ACCUMULATED_TICKS =
            new java.util.HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!AutoRepairConfig.isEnabled()) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<String> currentKeys = new ArrayList<>();

        for (ServerPlayer player : players) {
            // 主手、副手、四件盔甲（共 6 个槽位）
            List<ItemStack> stacks = collectRepairStacks(player);
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                int level = EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.AUTO_REPAIR.get(), stack);
                if (level <= 0) {
                    continue;
                }
                if (!stack.isDamaged()) {
                    // 耐久已满，跳过
                    continue;
                }

                String key = player.getUUID() + "_" + i;
                currentKeys.add(key);

                int ticks = ACCUMULATED_TICKS.getOrDefault(key, 0) + 1;
                int interval = AutoRepairConfig.getIntervalTicks(level);

                if (ticks >= interval) {
                    // 达到间隔，恢复 1 点耐久
                    stack.setDamageValue(stack.getDamageValue() - 1);
                    ticks = 0;
                }

                ACCUMULATED_TICKS.put(key, ticks);
            }
        }

        // 清理离线玩家的累计状态，避免内存占用无限增长
        if (players.isEmpty()) {
            ACCUMULATED_TICKS.clear();
        } else {
            ACCUMULATED_TICKS.keySet().retainAll(currentKeys);
        }
    }

    /**
     * 收集玩家的主手、副手和四件盔甲槽物品。
     * 返回顺序固定，用于构建累计 tick 的 key。
     */
    private static List<ItemStack> collectRepairStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>(6);
        stacks.add(player.getMainHandItem());       // 0: 主手
        stacks.add(player.getOffhandItem());         // 1: 副手
        // 盔甲槽：head, chest, legs, feet
        for (ItemStack armor : player.getArmorSlots()) {
            stacks.add(armor);
        }
        return stacks;
    }
}
