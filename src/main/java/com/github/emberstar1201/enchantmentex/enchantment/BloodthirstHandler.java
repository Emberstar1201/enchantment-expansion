package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "嗜血"附魔事件处理器
//
// 【原理】
//   监听 LivingHurtEvent，当玩家使用有"嗜血"附魔的武器攻击实体时，
//   检查冷却状态，若未冷却则回复玩家一定比例的最大生命值。
//
//   冷却存储在玩家的 PersistentData 中，格式：
//     BloodthirstData: { lastTriggerTime: <game_time> }
//   使用游戏世界时间（gameTime）判断冷却，跨维度一致。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class BloodthirstHandler {

    private static final String TAG_ROOT = "BloodthirstData";
    private static final String KEY_LAST_TRIGGER = "lastTriggerTime";

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 获取攻击者（必须是玩家）
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        // 服务端才处理
        if (player.level().isClientSide()) return;

        // 检查主手武器是否有嗜血附魔
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.BLOODTHIRST.get(), weapon);
        if (enchantLevel <= 0) return;

        // 获取目标（LivingHurtEvent.getEntity() 返回的就是 LivingEntity）
        LivingEntity target = event.getEntity();
        if (target == player) return; // 不自我回复

        // ========================================================================
        // 冷却检查
        // ========================================================================
        CompoundTag data = player.getPersistentData().getCompound(TAG_ROOT);
        long gameTime = player.level().getGameTime();
        long lastTrigger = data.getLong(KEY_LAST_TRIGGER);
        long cooldownTicks = Config.bloodthirstCooldownTicks;

        if (gameTime - lastTrigger < cooldownTicks) {
            return; // 冷却中，不回复
        }

        // ========================================================================
        // 执行生命回复：回复最大生命值 × 百分比
        // ========================================================================
        float healPercent = (float) Config.bloodthirstHealPercent;
        float healAmount = player.getMaxHealth() * (healPercent / 100.0f);
        player.heal(healAmount);

        // ========================================================================
        // 记录冷却时间
        // ========================================================================
        data.putLong(KEY_LAST_TRIGGER, gameTime);
        player.getPersistentData().put(TAG_ROOT, data);
    }
}