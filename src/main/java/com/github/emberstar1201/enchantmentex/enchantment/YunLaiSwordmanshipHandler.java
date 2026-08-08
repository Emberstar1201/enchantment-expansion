package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "云来剑法"附魔事件处理器
//
// 【效果一】攻击距离提升（PlayerTickEvent）
//   原理：在玩家手持附魔剑时，向 ForgeMod.ENTITY_REACH 属性添加固定 UUID 的
//         AttributeModifier(ADDITION)，等级越高加成越大。
//   管理：用 PersistentData 记录"上一 tick 的等级"，等级变化时才增删 modifier，
//         避免每 tick 重复添加导致属性溢出。使用 addTransientModifier（不持久化NBT）。
//
// 【效果二】攻击冷却缩减（LivingHurtEvent.Pre）
//   原理：玩家攻击时若攻击强度不满（getAttackStrengthScale < 1.0，即处于冷却中），
//         按附魔等级的"缩减比例"补正伤害——把本应丢失的伤害按比例补回，
//         等效于"冷却中攻击也不掉太多伤害"，玩家手感上等价于攻击冷却缩减。
//   公式：补回比例 = 冷却缩减比例（getCooldownReduction）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class YunLaiSwordmanshipHandler {

    // 攻击距离 modifier 的固定 UUID（用固定 UUID 保证每 tick 只有一个，不会叠加）
    // 云来剑法专用 UUID，与古·云来剑法互不冲突
    private static final UUID REACH_MODIFIER_UUID =
            UUID.fromString("b3c4d5e6-7890-4a1b-9c2d-3e4f5a6b7c8d");

    // PersistentData key：记录上一 tick 的云来剑法等级（用于检测等级变化）
    private static final String LAST_LEVEL_KEY = "YunLaiSword_LastReachLevel";

    // ========================================================================
    // 【效果一】攻击距离提升
    // 在 PlayerTickEvent.Phase.END 执行：确保原版 tick 完成后再调整属性
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅在 END 阶段处理，避免 PRE 阶段与原版属性计算冲突
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        ItemStack mainHand = player.getMainHandItem();

        // 获取主手剑的"云来剑法"附魔等级
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.YUNLAI_SWORDMANSHIP.get(), mainHand);

        // 读取上一 tick 记录的等级（检测是否切换武器/等级）
        int lastLevel = player.getPersistentData().getInt(LAST_LEVEL_KEY);

        // 等级未变化 → 无需调整 modifier，直接返回（避免每 tick 重复操作）
        if (enchantLevel == lastLevel) {
            return;
        }

        // ---------- 等级发生变化：先移除旧 modifier，再添加新 modifier ----------
        AttributeInstance reachAttr = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reachAttr == null) {
            // 属性实例不存在（理论上玩家都有），防御性返回
            player.getPersistentData().putInt(LAST_LEVEL_KEY, enchantLevel);
            return;
        }

        // 移除旧的 modifier（无论之前是几级，都用同一 UUID，移除一次即可）
        reachAttr.removeModifier(REACH_MODIFIER_UUID);

        // 若新等级 > 0，添加对应加成的 modifier
        if (enchantLevel > 0) {
            double bonus = YunLaiSwordmanshipEnchantment.getAttackReachBonus(enchantLevel);
            // Operation.ADDITION：在基础值上直接加 bonus（原版攻击距离 3.0 + bonus）
            AttributeModifier modifier = new AttributeModifier(
                    REACH_MODIFIER_UUID,
                    "YunLai Swordmanship Reach Bonus",
                    bonus,
                    AttributeModifier.Operation.ADDITION
            );
            // 使用 transient（瞬时）修饰符：不写入玩家NBT，由附魔每 tick 动态管理
            // 这样卸下剑时 modifier 自动随等级归零被移除，干净无残留
            reachAttr.addTransientModifier(modifier);
        }

        // 更新记录的等级
        player.getPersistentData().putInt(LAST_LEVEL_KEY, enchantLevel);
    }

    // ========================================================================
    // 【效果二】攻击冷却缩减（冷却中攻击的伤害补正）
    // 在 LivingHurtEvent.Pre 中：检测攻击者是否手持云来剑法附魔剑
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 伤害来源必须是玩家
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.YUNLAI_SWORDMANSHIP.get(), mainHand);
        if (enchantLevel <= 0) {
            return;
        }

        // 读取攻击瞬间的攻击强度比例（0.0 ~ 1.0）
        // < 1.0 表示处于攻击冷却中（挥剑过快，未完全蓄力）
        // 参数 0.5f 表示半tick后的预测值，与原版 Player.attack() 内部一致
        float strengthScale = player.getAttackStrengthScale(0.5f);

        // 已满力攻击，无需补正
        if (strengthScale >= 1.0f) {
            return;
        }

        // 获取冷却缩减比例（0.0 ~ 0.5）
        double reduction = YunLaiSwordmanshipEnchantment.getCooldownReduction(enchantLevel);
        if (reduction <= 0.0) {
            return;
        }

        // ---------- 伤害补正计算 ----------
        // 原版：amount = baseDamage × strengthScale（不满力则伤害打折）
        //   丢失的伤害 = baseDamage × (1 - strengthScale) = amount × (1-strengthScale)/strengthScale
        // 补回 = 丢失量 × reduction
        // 补正后 = amount + 补回 = amount × (1 + (1-strengthScale)×reduction/strengthScale)
        //
        // 防御性：strengthScale 极小时避免除零，钳制到最小 0.01
        float safeScale = Math.max(strengthScale, 0.01f);
        float originalAmount = event.getAmount();
        float recovered = originalAmount * (1.0f - safeScale) / safeScale * (float) reduction;
        float newAmount = originalAmount + recovered;

        event.setAmount(newAmount);
    }
}
