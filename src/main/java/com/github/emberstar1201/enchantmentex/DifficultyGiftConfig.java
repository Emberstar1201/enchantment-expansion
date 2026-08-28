package com.github.emberstar1201.enchantmentex;

import net.minecraft.world.Difficulty;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「难度馈赠」（difficulty_gift）附魔独立配置文件
//
// 根据当前游戏难度提供固定数值加成：
//   - weaponDamageBonus：武器攻击伤害加成（LivingHurtEvent 中生效）
//   - attackSpeedBonus：攻击速度加成（属性修饰符）
//   - armorBonus：盔甲护甲值加成（属性修饰符）
//   - toughnessBonus：盔甲韧性加成（属性修饰符）
//
// 各难度加成值（默认参考用户设定）：
//   和平：全部为 0（无加成）
//   简单：伤害+1，攻速+0.2，护甲+1，韧性+0.5
//   普通：伤害+2，攻速+0.4，护甲+2，韧性+1.0
//   困难：伤害+4，攻速+0.6，护甲+3，韧性+1.5
//
// 配置路径：config/enchantment_expansion-difficulty_gift.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DifficultyGiftConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("难度馈赠：是否启用（默认 true）")
            .define("difficultyGift.enabled", true);

    // ================================================================
    // 一、和平难度（无加成，保留占位配置便于统一读取）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue PEACEFUL_DAMAGE = BUILDER
            .comment("难度馈赠：和平难度 - 武器伤害加成（默认 0）")
            .defineInRange("difficultyGift.peaceful.damageBonus", 0.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue PEACEFUL_SPEED = BUILDER
            .comment("难度馈赠：和平难度 - 攻击速度加成（默认 0）")
            .defineInRange("difficultyGift.peaceful.attackSpeedBonus", 0.0, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue PEACEFUL_ARMOR = BUILDER
            .comment("难度馈赠：和平难度 - 护甲加成（默认 0）")
            .defineInRange("difficultyGift.peaceful.armorBonus", 0.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue PEACEFUL_TOUGH = BUILDER
            .comment("难度馈赠：和平难度 - 韧性加成（默认 0）")
            .defineInRange("difficultyGift.peaceful.toughnessBonus", 0.0, 0.0, 20.0);

    // ================================================================
    // 二、简单难度
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue EASY_DAMAGE = BUILDER
            .comment("难度馈赠：简单难度 - 武器伤害加成（默认 1）")
            .defineInRange("difficultyGift.easy.damageBonus", 1.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue EASY_SPEED = BUILDER
            .comment("难度馈赠：简单难度 - 攻击速度加成（默认 0.2）")
            .defineInRange("difficultyGift.easy.attackSpeedBonus", 0.2, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue EASY_ARMOR = BUILDER
            .comment("难度馈赠：简单难度 - 护甲加成（默认 1）")
            .defineInRange("difficultyGift.easy.armorBonus", 1.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue EASY_TOUGH = BUILDER
            .comment("难度馈赠：简单难度 - 韧性加成（默认 0.5）")
            .defineInRange("difficultyGift.easy.toughnessBonus", 0.5, 0.0, 20.0);

    // ================================================================
    // 三、普通难度
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue NORMAL_DAMAGE = BUILDER
            .comment("难度馈赠：普通难度 - 武器伤害加成（默认 2）")
            .defineInRange("difficultyGift.normal.damageBonus", 2.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue NORMAL_SPEED = BUILDER
            .comment("难度馈赠：普通难度 - 攻击速度加成（默认 0.4）")
            .defineInRange("difficultyGift.normal.attackSpeedBonus", 0.4, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue NORMAL_ARMOR = BUILDER
            .comment("难度馈赠：普通难度 - 护甲加成（默认 2）")
            .defineInRange("difficultyGift.normal.armorBonus", 2.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue NORMAL_TOUGH = BUILDER
            .comment("难度馈赠：普通难度 - 韧性加成（默认 1.0）")
            .defineInRange("difficultyGift.normal.toughnessBonus", 1.0, 0.0, 20.0);

    // ================================================================
    // 四、困难难度
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue HARD_DAMAGE = BUILDER
            .comment("难度馈赠：困难难度 - 武器伤害加成（默认 4）")
            .defineInRange("difficultyGift.hard.damageBonus", 4.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue HARD_SPEED = BUILDER
            .comment("难度馈赠：困难难度 - 攻击速度加成（默认 0.6）")
            .defineInRange("difficultyGift.hard.attackSpeedBonus", 0.6, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue HARD_ARMOR = BUILDER
            .comment("难度馈赠：困难难度 - 护甲加成（默认 3）")
            .defineInRange("difficultyGift.hard.armorBonus", 3.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue HARD_TOUGH = BUILDER
            .comment("难度馈赠：困难难度 - 韧性加成（默认 1.5）")
            .defineInRange("difficultyGift.hard.toughnessBonus", 1.5, 0.0, 20.0);

    // 配置 SPEC 实例
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 缓存到静态字段的配置值
    // ================================================================
    public static boolean difficultyGiftEnabled;
    public static double peacefulDamage, peacefulSpeed, peacefulArmor, peacefulToughness;
    public static double easyDamage, easySpeed, easyArmor, easyToughness;
    public static double normalDamage, normalSpeed, normalArmor, normalToughness;
    public static double hardDamage, hardSpeed, hardArmor, hardToughness;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：只响应本配置的加载事件
        if (event.getConfig() == null || event.getConfig().getSpec() != DifficultyGiftConfig.SPEC) {
            return;
        }

        difficultyGiftEnabled = ENABLED.get();
        peacefulDamage = PEACEFUL_DAMAGE.get();
        peacefulSpeed = PEACEFUL_SPEED.get();
        peacefulArmor = PEACEFUL_ARMOR.get();
        peacefulToughness = PEACEFUL_TOUGH.get();
        easyDamage = EASY_DAMAGE.get();
        easySpeed = EASY_SPEED.get();
        easyArmor = EASY_ARMOR.get();
        easyToughness = EASY_TOUGH.get();
        normalDamage = NORMAL_DAMAGE.get();
        normalSpeed = NORMAL_SPEED.get();
        normalArmor = NORMAL_ARMOR.get();
        normalToughness = NORMAL_TOUGH.get();
        hardDamage = HARD_DAMAGE.get();
        hardSpeed = HARD_SPEED.get();
        hardArmor = HARD_ARMOR.get();
        hardToughness = HARD_TOUGH.get();
    }

    // ================================================================
    // 运行工具方法
    // ================================================================

    /** 是否启用难度馈赠 */
    public static boolean isEnabled() {
        return difficultyGiftEnabled;
    }

    /** 根据难度返回武器伤害加成 */
    public static double getDamageBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> peacefulDamage;
            case EASY -> easyDamage;
            case NORMAL -> normalDamage;
            case HARD -> hardDamage;
        };
    }

    /** 根据难度返回攻击速度加成 */
    public static double getAttackSpeedBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> peacefulSpeed;
            case EASY -> easySpeed;
            case NORMAL -> normalSpeed;
            case HARD -> hardSpeed;
        };
    }

    /** 根据难度返回护甲加成 */
    public static double getArmorBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> peacefulArmor;
            case EASY -> easyArmor;
            case NORMAL -> normalArmor;
            case HARD -> hardArmor;
        };
    }

    /** 根据难度返回韧性加成 */
    public static double getToughnessBonus(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> peacefulToughness;
            case EASY -> easyToughness;
            case NORMAL -> normalToughness;
            case HARD -> hardToughness;
        };
    }
}