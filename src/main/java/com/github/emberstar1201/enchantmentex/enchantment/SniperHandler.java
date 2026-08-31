package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 狙击 Handler
//
// 两段式实现（与爆破箭矢同款思路）：
//   ① EntityJoinLevelEvent：箭进入世界时，若射手主/副手弓弩带狙击附魔，
//      将附魔标记 + 射手眼睛位置写入箭的 PersistentData。
//   ② LivingHurtEvent：箭命中生物时，读取箭上记录的发射位置，
//      计算飞行距离 → 按距离缩放伤害。
//
// 伤害公式：
//   每 10 格 +20%，上限 +100%（50 格满额）
//   multiplier = 1.0 + min(distance / 10 * 0.2, 1.0)
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SniperHandler {

    // NBT 标记：是否带狙击附魔
    private static final String TAG_SNIPER = "SniperMarked";
    // NBT 标记：发射者眼睛位置（x, y, z）
    private static final String TAG_SNIPER_X = "SniperOriginX";
    private static final String TAG_SNIPER_Y = "SniperOriginY";
    private static final String TAG_SNIPER_Z = "SniperOriginZ";

    // 每 10 格 +20%，上限 +100%（50 格满额）
    private static final double BLOCKS_PER_TIER = 10.0;
    private static final double BONUS_PER_TIER = 0.20;
    private static final double MAX_BONUS = 1.0; // +100%

    // ========================================================================
    // ① 箭生成时打标记 + 记录发射位置
    // ========================================================================
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_SNIPER)) return; // 防重复

        if (!(arrow.getOwner() instanceof Player player)) return;

        // 检查主/副手弓弩上的狙击附魔
        int level = getSniperLevel(player.getMainHandItem(), player.getOffhandItem());
        if (level <= 0) return;

        // 写入标记 + 发射者眼睛位置
        tag.putBoolean(TAG_SNIPER, true);
        Vec3 eye = player.getEyePosition();
        tag.putDouble(TAG_SNIPER_X, eye.x);
        tag.putDouble(TAG_SNIPER_Y, eye.y);
        tag.putDouble(TAG_SNIPER_Z, eye.z);
    }

    /** 计算两手弓/弩上的狙击等级 */
    private static int getSniperLevel(ItemStack main, ItemStack off) {
        int level = 0;
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.SNIPER.get(), main));
        }
        if (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.SNIPER.get(), off));
        }
        return level;
    }

    // ========================================================================
    // ② 箭命中生物时按飞行距离缩放伤害
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (!tag.contains(TAG_SNIPER)) return;

        // 读取发射位置
        Vec3 origin = new Vec3(
                tag.getDouble(TAG_SNIPER_X),
                tag.getDouble(TAG_SNIPER_Y),
                tag.getDouble(TAG_SNIPER_Z)
        );

        // 计算飞行距离（命中点到发射点的直线距离）
        Vec3 hitPos = arrow.position();
        double distance = origin.distanceTo(hitPos);

        // 计算伤害倍率
        double bonus = Math.min(distance / BLOCKS_PER_TIER * BONUS_PER_TIER, MAX_BONUS);
        if (bonus <= 0.001) return;

        double multiplier = 1.0 + bonus;
        event.setAmount((float) (event.getAmount() * multiplier));

        // ActionBar 提示
        if (source.getEntity() instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§a§l狙击 §r§7距离 " + String.format("%.1f", distance) + " 格 §b×"
                                    + String.format("%.2f", multiplier)),
                    true);
        }
    }
}
