package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.SwiftCrossbowConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【迅捷之弩】附魔事件处理器
//
// 【核心效果：弩箭附加魔法伤害（无视护甲，受保护附魔和抗性提升影响）】
//
//   I:+2  II:+4  III:+6  IV:+8（数值见 SwiftCrossbowConfig）
//
//   DamageSource.indirectMagic() 魔法伤害类型：
//     1. 无视盔甲值（Armor）→ 对高护甲目标依然有效
//     2. 受保护附魔（Protection）减免
//     3. 受抗性提升（Resistance）效果减免
//     4. 无法被盾牌格挡
//
// 【实现方式（两段式，保证附魔判定与射击武器解耦）】
//   ① EntityJoinLevelEvent：箭生成进入世界时，若射击者（玩家）手持
//      装有「迅捷之弩」的弩（主手或副手），把附魔等级写入箭的 NBT。
//      → 之后即使玩家切了武器，箭上标记依然存在，不丢失判定。
//   ② LivingHurtEvent：箭命中触发伤害事件时，读取箭头 NBT 标记，
//      用 indirectMagic() 追加一笔独立魔法伤害（不 setAmount，
//      与原版箭矢伤害叠加，互不影响）。
//      → 追加伤害会再次触发 LivingHurtEvent，用目标 NBT 标记
//        提前 return，防止递归（与千破·青溟剑同款方案）。
//
// 【音效】不添加额外音效，沿用原版弩音效。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SwiftCrossbowHandler {

    // 箭实体 NBT 标记：记录「迅捷之弩」附魔等级
    private static final String TAG_SWIFT_LEVEL = "SwiftCrossbowLevel";

    // 目标 NBT 标记：在调用 hurt() 追加魔法伤害前标记，
    // 防止追加伤害触发的 LivingHurtEvent 再次进入此方法（递归保护）
    private static final String TAG_APPLYING_SWIFT = "SwiftCrossbowApplying";

    // ========================================================================
    // ① 箭生成进入世界时打标记
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide()) return;
        if (!(entity instanceof AbstractArrow arrow)) return;

        // 防重复标记（例如实体在维度间传送会再次进入世界）
        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_SWIFT_LEVEL)) return;

        // 仅玩家射出的箭才标记（发射器/骷髅弩射出的忽略）
        if (!(arrow.getOwner() instanceof Player player)) return;

        // 检查主手/副手弩上的「迅捷之弩」附魔
        int level = getSwiftLevel(player.getMainHandItem(), player.getOffhandItem());
        if (level <= 0) return;

        // 写入箭 NBT，供命中时的伤害事件读取
        tag.putInt(TAG_SWIFT_LEVEL, level);
    }

    /** 计算两手弩上的「迅捷之弩」等级（取较大值） */
    private static int getSwiftLevel(ItemStack main, ItemStack off) {
        int level = 0;
        if (main.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.SWIFT_CROSSBOW.get(), main));
        }
        if (off.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.SWIFT_CROSSBOW.get(), off));
        }
        return level;
    }

    // ========================================================================
    // ② 箭命中时追加魔法伤害
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;

        // ★ 递归保护：追加魔法伤害会再次触发 LivingHurtEvent，
        //   通过目标 NBT 标记提前返回，避免无限递归。
        if (target.getPersistentData().getBoolean(TAG_APPLYING_SWIFT)) return;

        // 伤害源必须是箭（AbstractArrow）
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) return;

        // 读取箭上 NBT 标记（无标记 → 非迅捷之弩射出）
        int level = arrow.getPersistentData().getInt(TAG_SWIFT_LEVEL);
        if (level <= 0) return;

        // 根据等级读取配置的附加魔法伤害
        float bonus = (float) SwiftCrossbowConfig.getBonusDamage(level);
        if (bonus <= 0) return;

        // 追加独立魔法伤害（无视护甲）；间接来源=射手（可能为 null，泛型允许）
        // 先在目标 NBT 上标记，防止 hurt() 再次触发本方法
        target.getPersistentData().putBoolean(TAG_APPLYING_SWIFT, true);
        try {
            target.hurt(target.damageSources().indirectMagic(arrow, arrow.getOwner()), bonus);
        } finally {
            // 无论是否抛异常，必须清除标记防止卡死
            target.getPersistentData().remove(TAG_APPLYING_SWIFT);
        }
    }
}