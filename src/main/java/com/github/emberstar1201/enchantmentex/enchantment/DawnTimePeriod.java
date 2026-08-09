package com.github.emberstar1201.enchantmentex.enchantment;

// ========================================================================
// 【拂晓】时段枚举 - 定义昼夜各时段的数值加成
//
// 用户要求的时段划分（Minecraft 原版时间，0~24000 一天循环）：
//   白天     (6000~12000)                    ：无加成
//   傍晚/凌晨 (12000~13000 / 23000~24000)    ：伤害 +50% ，冷却 -25%
//   夜晚     (13000~17000)                   ：伤害 +100%（2倍），冷却 -50%
//   午夜     (17000~18000)                   ：伤害 +300%（4倍），冷却 -100%（无冷却）
//
// 补充时段（用户未明确，但需覆盖完整一天）：
//   0~6000（清晨）：归为白天（无加成）
//   18000~23000（深夜）：归为夜晚（+100%伤害，-50%冷却）
//
// 时段编码（用于 PersistentData 存储）：
//   0 = DAY（白天）     damageMultiplier=1.0,  cooldownReduction=0.0,  attackSpeedBonus=0.0
//   1 = DUSK_DAWN       damageMultiplier=1.5,  cooldownReduction=0.25, attackSpeedBonus=0.333
//   2 = NIGHT           damageMultiplier=2.0,  cooldownReduction=0.50, attackSpeedBonus=1.0
//   3 = MIDNIGHT        damageMultiplier=4.0,  cooldownReduction=1.0,  attackSpeedBonus=100.0（极大值实现无冷却）
//
// attackSpeedBonus 推导：
//   冷却时间 = 基础冷却 × (1 - cooldownReduction)
//   新攻速 = 基础攻速 / (1 - cooldownReduction)
//   MULTIPLY_BASE 模式下 amount = 1/(1-r) - 1
//     r=0.25 → amount = 0.333
//     r=0.50 → amount = 1.0
//     r=1.0  → amount = 无穷 → 取大值 100.0（实际冷却 < 1 tick，等价无冷却）
// ========================================================================
public enum DawnTimePeriod {

    DAY(0, 1.0f, 0.0f, 0.0f, "白天"),
    DUSK_DAWN(1, 1.5f, 0.25f, 0.333f, "傍晚/凌晨"),
    NIGHT(2, 2.0f, 0.50f, 1.0f, "夜晚"),
    MIDNIGHT(3, 4.0f, 1.0f, 100.0f, "午夜");

    // 时段编码（存储到 PersistentData 的 int 值）
    private final int code;
    // 伤害倍率（1.0=不变，1.5=+50%，2.0=2倍，4.0=4倍）
    private final float damageMultiplier;
    // 冷却缩减比例（0.0=无缩减，0.5=减半，1.0=无冷却）
    private final float cooldownReduction;
    // 攻速加成（MULTIPLY_BASE 模式的 amount 值）
    private final float attackSpeedBonus;
    // 时段名称（用于调试/日志）
    private final String displayName;

    DawnTimePeriod(int code, float damageMultiplier, float cooldownReduction,
                   float attackSpeedBonus, String displayName) {
        this.code = code;
        this.damageMultiplier = damageMultiplier;
        this.cooldownReduction = cooldownReduction;
        this.attackSpeedBonus = attackSpeedBonus;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public float getCooldownReduction() {
        return cooldownReduction;
    }

    public float getAttackSpeedBonus() {
        return attackSpeedBonus;
    }

    public String getDisplayName() {
        return displayName;
    }

    // ========================================================================
    // 根据 Minecraft 世界时间（0~24000）判定当前时段
    // 用户指定的时段边界，补充 0~6000 和 18000~23000 的归属
    // ========================================================================
    public static DawnTimePeriod fromGameTime(long time) {
        // 规范化到 0~24000 范围（防止负数或溢出）
        long t = ((time % 24000L) + 24000L) % 24000L;

        // 0~6000：清晨（归为白天）
        if (t < 6000) {
            return DAY;
        }
        // 6000~12000：白天
        if (t < 12000) {
            return DAY;
        }
        // 12000~13000：傍晚
        if (t < 13000) {
            return DUSK_DAWN;
        }
        // 13000~17000：夜晚
        if (t < 17000) {
            return NIGHT;
        }
        // 17000~18000：午夜
        if (t < 18000) {
            return MIDNIGHT;
        }
        // 18000~23000：深夜（归为夜晚）
        if (t < 23000) {
            return NIGHT;
        }
        // 23000~24000：凌晨（黎明前）
        return DUSK_DAWN;
    }

    // ========================================================================
    // 根据 PersistentData 中存储的编码值反查时段枚举
    // 无效编码默认返回 DAY（安全降级）
    // ========================================================================
    public static DawnTimePeriod fromCode(int code) {
        for (DawnTimePeriod period : values()) {
            if (period.code == code) {
                return period;
            }
        }
        return DAY;  // 默认白天（无加成）
    }
}
