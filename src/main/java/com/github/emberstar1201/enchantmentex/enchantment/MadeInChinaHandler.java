package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ========================================================================
// "中国制造"附魔事件处理器（Made in China）
//
// 【监听事件】
//   TickEvent.ServerTickEvent (END 阶段)
//     → 遍历服务器所有玩家，对装备槽中带"中国制造"附魔的物品
//       每隔配置间隔（默认 60 tick = 3秒）恢复 1 点耐久
//
// 【设计说明】
//   - 自动修复效果融合"原版耐久 Unbreaking"（耐久续航）+
//     "耐久强化 Durability Boost"（等效耐久×2/×3/×4）+ 自动修复概念
//   - 默认间隔 60 tick（3秒/1点）等效于"耐久持续不消耗"——
//     对比 AutoRepairHandler I 级（100 tick/1点）更频繁，体现"中国制造"的耐久强度
//   - "50% 概率不消耗耐久"概念融合在自动修复中：玩家正常使用消耗耐久，
//     本附魔每 3 秒恢复 1 点耐久，等效长期消耗速率减半
//   - 匠心传承效果（记忆方块 + 加速 + 额外掉落）由 ArtisanLegacyHandler 共用触发
//     （仅当物品是镐/斧/锹 DIGGER 时生效，避免影响武器/盔甲）
// ========================================================================
public class MadeInChinaHandler {

    // 累计 tick 计数器：key = "玩家UUID_装备槽索引"，value = 累计 tick
    private static final Map<String, Integer> ACCUMULATED_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 仅在 END 阶段处理，避免与其他阶段的处理器冲突
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<String> currentKeys = new ArrayList<>();

        for (ServerPlayer player : players) {
            // 收集玩家 6 个装备槽的物品（主手 / 副手 / 四件盔甲）
            List<ItemStack> stacks = collectEquipmentStacks(player);
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                // 检测是否带"中国制造"附魔
                int level = EnchantmentHelper.getItemEnchantmentLevel(
                        ModEnchantments.MADE_IN_CHINA.get(), stack);
                if (level <= 0) {
                    continue;
                }
                // 耐久已满则跳过
                if (!stack.isDamaged()) {
                    continue;
                }

                String key = player.getUUID() + "_" + i;
                currentKeys.add(key);

                int ticks = ACCUMULATED_TICKS.getOrDefault(key, 0) + 1;
                int interval = Config.madeInChinaAutoRepairIntervalTicks;

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
     * 返回顺序固定，用于构建累计 tick 的 key（与 AutoRepairHandler 一致）。
     */
    private static List<ItemStack> collectEquipmentStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>(6);
        stacks.add(player.getMainHandItem());       // 0: 主手
        stacks.add(player.getOffhandItem());         // 1: 副手
        for (ItemStack armor : player.getArmorSlots()) {  // 2-5: 头/胸/腿/脚
            stacks.add(armor);
        }
        return stacks;
    }
}
