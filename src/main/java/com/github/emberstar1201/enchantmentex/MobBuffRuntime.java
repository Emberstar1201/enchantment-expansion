package com.github.emberstar1201.enchantmentex;

/**
 * 原版怪物强化的运行时总开关。
 * 配置决定默认值，开局弹窗可以覆盖当前世界的选择。
 */
public final class MobBuffRuntime {
    private static boolean enabled = true;

    private MobBuffRuntime() {
    }

    public static void initializeFromConfig() {
        enabled = MobBuffConfig.enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
