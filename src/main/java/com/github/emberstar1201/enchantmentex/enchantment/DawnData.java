package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 拂晓重制 - 数据管理工具类
//
// 【原理】
//   成长数据同时存储在两个地方：
//     1. 玩家 PersistentData（服务器端持久化，不自动同步到客户端）
//     2. 武器物品 NBT（自动同步到客户端，用于 Tooltip 显示）
//
//   NBT 结构（两者一致）：DawnData: { effectiveKills: <double> }
//
// 【数据流】
//   击杀时 → 写入 PersistentData + 同步到武器 NBT
//   换武器时 → 从 PersistentData 复制到武器 NBT
//   Tooltip → 从武器 NBT 读取（客户端能看到最新数据）
// ========================================================================
public class DawnData {

    private static final String TAG_ROOT = "DawnData";
    private static final String KEY_KILLS = "effectiveKills";
    private static final String KEY_ACCUMULATED_CRIT = "accumulatedCrit";

    // ========================================================================
    // Player PersistentData 存取（服务器端持久化）
    // ========================================================================

    /** 获取累计有效击杀数（从玩家 PersistentData） */
    public static double getEffectiveKills(Player player) {
        return player.getPersistentData().getCompound(TAG_ROOT).getDouble(KEY_KILLS);
    }

    /** 增加有效击杀数 */
    public static void addEffectiveKills(Player player, double amount) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        double current = data.getDouble(KEY_KILLS);
        data.putDouble(KEY_KILLS, Math.max(0, current + amount));
        player.getPersistentData().put(TAG_ROOT, data);
    }

    /** 清空所有数据 */
    public static void clear(Player player) {
        player.getPersistentData().remove(TAG_ROOT);
    }

    // ========================================================================
    // 伪暴击率（累积概率）存取 — 仅服务器端，不同步到客户端
    //
    // 机制：普通攻击未触发拂晓暴击时，每次 +5% 累积到下次，
    //       直到触发暴击后重置为 0。跳劈（原版暴击）无视此机制。
    // ========================================================================

    /** 获取当前累积的伪暴击率（百分比，如 15.0 = +15%） */
    public static double getAccumulatedCrit(Player player) {
        return player.getPersistentData().getCompound(TAG_ROOT).getDouble(KEY_ACCUMULATED_CRIT);
    }

    /** 设置累积伪暴击率 */
    public static void setAccumulatedCrit(Player player, double value) {
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        data.putDouble(KEY_ACCUMULATED_CRIT, Math.max(0, value));
        player.getPersistentData().put(TAG_ROOT, data);
    }

    /** 增加累积伪暴击率 */
    public static void addAccumulatedCrit(Player player, double amount) {
        double current = getAccumulatedCrit(player);
        setAccumulatedCrit(player, current + amount);
    }

    // ========================================================================
    // 武器 ItemStack NBT 存取（自动同步到客户端，用于 Tooltip 显示）
    // ========================================================================

    /** 从武器 NBT 获取击杀数（客户端 Tooltip 使用） */
    public static double getItemKills(ItemStack stack) {
        return stack.getOrCreateTag().getCompound(TAG_ROOT).getDouble(KEY_KILLS);
    }

    /** 将击杀数写入武器 NBT */
    public static void setItemKills(ItemStack stack, double kills) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag data = root.getCompound(TAG_ROOT);
        data.putDouble(KEY_KILLS, kills);
        root.put(TAG_ROOT, data);
    }

    // ========================================================================
    // 统计数值计算（从 effectiveKills 实时计算）
    // ========================================================================

    /** 显示等级（取整） */
    public static int getLevel(double kills) {
        return (int) Math.floor(kills);
    }

    /** 伤害加成百分比 */
    public static double getDamageBonusPercent(double kills) {
        return Math.min(kills * Config.dawnDamagePerKill, Config.dawnDamageMax);
    }

    /** 暴击率百分比 */
    public static double getCritRatePercent(double kills) {
        return Math.min(kills * Config.dawnCritRatePerKill, Config.dawnCritRateMax);
    }

    /** 暴击伤害百分比 */
    public static double getCritDamagePercent(double kills) {
        return Math.min(kills * Config.dawnCritDamagePerKill, Config.dawnCritDamageMax);
    }

    /** 攻速惩罚百分比（正值表示减少量，如 50 = 减少50%攻速） */
    public static double getAttackSpeedPenaltyPercent(double kills) {
        return Math.min(kills * Math.abs(Config.dawnAttackSpeedPerKill),
                Math.abs(Config.dawnAttackSpeedMax));
    }
}