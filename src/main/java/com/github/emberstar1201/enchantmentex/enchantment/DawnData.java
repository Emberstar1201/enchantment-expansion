package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// ========================================================================
// 拂晓重制 - 数据管理工具类
//
// 【原理】
//   拂晓成长数据只存储在武器物品 NBT，避免同一玩家的多把武器共享等级：
//     1. 武器物品 NBT（服务器持久化并自动同步到客户端，用于 Tooltip 显示）
//     2. 实体 PersistentData 仅保存连击、伪暴击等实体状态，不保存武器成长
//
//   NBT 结构：DawnData: { effectiveKills: <double> }
//
// 【数据流】
//   击杀时 → 读取并更新当前武器 NBT
//   换武器时 → 保留新武器自己的 NBT，不再从玩家数据覆盖
//   Tooltip → 从武器 NBT 读取（客户端能看到最新数据）
//
//   兼容旧版本：旧版玩家 PersistentData 中遗留的 effectiveKills 只会在首次
//   访问一把尚无成长数据的拂晓武器时迁移一次，随后删除共享键。
//
// 【为什么参数类型是 LivingEntity 而不是 Player】
//   车万女仆（EntityMaid）同样是 LivingEntity，Forge 也为它提供了
//   getPersistentData()。把参数放宽到 LivingEntity 后，女仆可以使用
//   与玩家完全相同的暴击伪概率 / 刺破长夜状态存取逻辑，
//   且数据存在女仆自己的 PersistentData 里，与玩家互不干扰。
//   （玩家调用点无需改动：Player 是 LivingEntity 的子类。）
// ========================================================================
public class DawnData {

    private static final String TAG_ROOT = "DawnData";
    private static final String KEY_KILLS = "effectiveKills";
    private static final String KEY_ACCUMULATED_CRIT = "accumulatedCrit";

    // ========================================================================
    // 刺破长夜 状态键（仅服务器端 PersistData 维护，不同步到客户端）
    //   combo:            当前连击数（相邻击杀间隔 ≤ 连击窗口才累计）
    //   lastKillTime:     上次击杀的游戏 tick 时间戳
    //   activeTicks:      刺破长夜激活剩余 tick（>0 表示激活中）
    //   activeType:       激活类型：1=连击爆发（移速+攻距），2=低血狂暴（吸血）
    //   cooldownTicks:    共享冷却剩余 tick
    // ========================================================================
    private static final String KEY_COMBO = "combo";
    private static final String KEY_LAST_KILL_TIME = "lastKillTime";
    private static final String KEY_ACTIVE_TICKS = "activeTicks";
    private static final String KEY_ACTIVE_TYPE = "activeType";
    private static final String KEY_COOLDOWN_TICKS = "cooldownTicks";

    // ========================================================================
    // 实体 PersistentData 存取（服务器端持久化）
    // ========================================================================

    /**
     * 获取旧版实体共享击杀数，仅供存档迁移使用。
     * 新逻辑禁止把它作为武器的实际成长数据来源。
     */
    public static double getEffectiveKills(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getDouble(KEY_KILLS);
    }

    /** 旧版兼容：读取当前实体上遗留的共享击杀数并迁移到当前武器一次。 */
    public static void migrateLegacyKills(LivingEntity entity, ItemStack stack) {
        if (hasItemKills(stack)) {
            return;
        }

        CompoundTag entityData = entity.getPersistentData().getCompound(TAG_ROOT);
        if (!entityData.contains(KEY_KILLS)) {
            return;
        }

        setItemKills(stack, Math.max(0.0D, entityData.getDouble(KEY_KILLS)));
        entityData.remove(KEY_KILLS);
        entity.getPersistentData().put(TAG_ROOT, entityData);
    }

    /** 判断物品是否已经拥有独立的拂晓成长数据（包括明确的 0 级数据）。 */
    public static boolean hasItemKills(ItemStack stack) {
        return stack.hasTag()
                && stack.getTag().contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)
                && stack.getTag().getCompound(TAG_ROOT).contains(KEY_KILLS);
    }

    /** 清空所有拂晓成长数据，但保留其他状态 */
    public static void clearEffectiveKills(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.remove(KEY_KILLS);
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 清除物品上的拂晓成长数据 */
    public static void clearItemKills(ItemStack stack) {
        if (!stack.hasTag()) {
            return;
        }
        CompoundTag root = stack.getTag();
        root.remove(TAG_ROOT);
        if (root.isEmpty()) {
            stack.setTag(null);
        }
    }

    /** 清空所有数据 */
    public static void clear(LivingEntity entity) {
        entity.getPersistentData().remove(TAG_ROOT);
    }

    // ========================================================================
    // 伪暴击率（累积概率）存取 — 仅服务器端，不同步到客户端
    //
    // 机制：普通攻击未触发拂晓暴击时，每次 +5% 累积到下次，
    //       直到触发暴击后重置为 0。跳劈（原版暴击）无视此机制。
    // ========================================================================

    /** 获取当前累积的伪暴击率（百分比，如 15.0 = +15%） */
    public static double getAccumulatedCrit(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getDouble(KEY_ACCUMULATED_CRIT);
    }

    /** 设置累积伪暴击率 */
    public static void setAccumulatedCrit(LivingEntity entity, double value) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putDouble(KEY_ACCUMULATED_CRIT, Math.max(0, value));
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 增加累积伪暴击率 */
    public static void addAccumulatedCrit(LivingEntity entity, double amount) {
        double current = getAccumulatedCrit(entity);
        setAccumulatedCrit(entity, current + amount);
    }

    // ========================================================================
    // 刺破长夜 状态存取（全部存实体 PersistentData，仅服务器端）
    // ========================================================================

    /** 获取当前连击数 */
    public static int getCombo(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getInt(KEY_COMBO);
    }

    /** 设置连击数 */
    public static void setCombo(LivingEntity entity, int combo) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putInt(KEY_COMBO, Math.max(0, combo));
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 记录上次击杀的游戏 tick 时间戳 */
    public static void setLastKillTime(LivingEntity entity, long time) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putLong(KEY_LAST_KILL_TIME, time);
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 获取上次击杀的游戏 tick 时间戳（无记录时返回 0） */
    public static long getLastKillTime(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getLong(KEY_LAST_KILL_TIME);
    }

    /** 获取刺破长夜激活剩余 tick */
    public static int getActiveTicks(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getInt(KEY_ACTIVE_TICKS);
    }

    /** 设置刺破长夜激活剩余 tick */
    public static void setActiveTicks(LivingEntity entity, int ticks) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putInt(KEY_ACTIVE_TICKS, Math.max(0, ticks));
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 获取激活类型（1=连击爆发 2=低血狂暴） */
    public static int getActiveType(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getInt(KEY_ACTIVE_TYPE);
    }

    /** 设置激活类型 */
    public static void setActiveType(LivingEntity entity, int type) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putInt(KEY_ACTIVE_TYPE, type);
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 获取共享冷却剩余 tick */
    public static int getCooldownTicks(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getInt(KEY_COOLDOWN_TICKS);
    }

    /** 设置共享冷却剩余 tick */
    public static void setCooldownTicks(LivingEntity entity, int ticks) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putInt(KEY_COOLDOWN_TICKS, Math.max(0, ticks));
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 刺破长夜是否激活中 */
    public static boolean isPierceActive(LivingEntity entity) {
        return getActiveTicks(entity) > 0;
    }

    // ========================================================================
    // 武器 ItemStack NBT 存取（自动同步到客户端，用于 Tooltip 显示）
    // ========================================================================

    /** 从武器 NBT 获取击杀数（客户端 Tooltip 使用）；没有数据时按 0 处理。 */
    public static double getItemKills(ItemStack stack) {
        if (!hasItemKills(stack)) {
            return 0.0D;
        }
        return stack.getTag().getCompound(TAG_ROOT).getDouble(KEY_KILLS);
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

    private static double levelGrowth(double kills, double base, double perLevel) {
        return base + Math.max(0.0D, Math.floor(kills) - 1.0D) * perLevel;
    }

    /** 伤害加成百分比 */
    public static double getDamageBonusPercent(double kills) {
        if (kills < 1.0D) return 0.0D;
        return Math.min(levelGrowth(kills, 50.0D, Config.dawnDamagePerKill), Config.dawnDamageMax);
    }

    /** 暴击率百分比 */
    public static double getCritRatePercent(double kills) {
        if (kills < 1.0D) return 0.0D;
        return Math.min(levelGrowth(kills, 5.0D, 0.25D), Config.dawnCritRateMax);
    }

    /** 暴击伤害百分比 */
    public static double getCritDamagePercent(double kills) {
        if (kills < 1.0D) return 0.0D;
        return Math.min(levelGrowth(kills, 75.0D, Config.dawnCritDamagePerKill), Config.dawnCritDamageMax);
    }
}
