package com.github.emberstar1201.enchantmentex.entity;

/** 友好娘统一工作模式。 */
public enum GirlWorkMode {
    /** 主动攻击目标。 */
    ATTACK("attack"),
    /** 跟随主人。 */
    FOLLOW("follow"),
    /** 防御主人并允许战斗。 */
    DEFEND("defend"),
    /** 砍伐树木。 */
    TREE_CUTTING("tree_cutting"),
    /** 挖掘矿物。 */
    MINING("mining"),
    /** 耕作农田。 */
    FARMING("farming"),
    /** 寻找床铺睡觉。 */
    SLEEP("sleep"),
    /** 放置照明方块。 */
    LIGHTING("lighting"),
    /** 待机。 */
    IDLE("idle");

    private final String key;

    GirlWorkMode(String key) {
        this.key = key;
    }

    /** 获取稳定的翻译键字符串。 */
    public String getKey() {
        return this.key;
    }

    /** 从序号安全读取模式，异常值回退为待机。 */
    public static GirlWorkMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IDLE;
    }

    /** 从稳定字符串键读取模式，未知值回退为跟随。 */
    public static GirlWorkMode fromKey(String key) {
        for (GirlWorkMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return FOLLOW;
    }
}
