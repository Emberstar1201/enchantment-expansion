package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "兵长的回声"附魔事件处理器
//
// 【原理】
//   使用实体 PersistentData 存储标记层数和时间戳。
//   LivingHurtEvent：攻击时叠加标记，满3层引爆魔法伤害。
//   LevelTickEvent：定期清理超时标记。
//
// 【NBT 结构】
//   LevisEchoData: {
//     stacks: 1..3,
//     lastHitTime: <game_time>
//   }
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class LevisEchoHandler {

    // NBT 相关键名
    private static final String TAG_ROOT = "LevisEchoData";
    private static final String KEY_STACKS = "stacks";
    private static final String KEY_LAST_HIT = "lastHitTime";

    // ========================================================================
    // LivingHurtEvent：叠加标记 / 引爆回声伤害
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 目标实体
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;

        // 获取攻击者
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;

        // 检查攻击者主手武器是否有回声附魔
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.LEVIS_ECHO.get(), weapon);
        if (enchantLevel <= 0) return;

        // ========================================================================
        // 检查目标生命值是否达到阈值（默认 50 HP = 25❤️）
        // ========================================================================
        if (target.getHealth() < Config.levisEchoHealthThreshold) {
            return; // 血量不足，不触发回声
        }

        // ========================================================================
        // 读取 / 创建 NBT 标记数据
        // ========================================================================
        CompoundTag data = target.getPersistentData().getCompound(TAG_ROOT);
        int currentStacks = data.getInt(KEY_STACKS);
        long gameTime = target.level().getGameTime();

        // 检查是否超时（如果超时则重置计数）
        if (currentStacks > 0) {
            long lastHit = data.getLong(KEY_LAST_HIT);
            if (gameTime - lastHit > Config.levisEchoMarkTimeout) {
                currentStacks = 0; // 超时，标记重置
            }
        }

        // ========================================================================
        // 叠加标记
        // ========================================================================
        currentStacks++;
        data.putInt(KEY_STACKS, currentStacks);
        data.putLong(KEY_LAST_HIT, gameTime);
        target.getPersistentData().put(TAG_ROOT, data);

        // ========================================================================
        // 到达3层 → 引爆
        // ========================================================================
        int maxStacks = Config.levisEchoMaxStacks;
        if (currentStacks >= maxStacks) {
            // 清除标记（防止重复引爆）
            target.getPersistentData().remove(TAG_ROOT);

            // ========================================================================
            // 计算伤害：5% + (等级-1) × 2% 最大生命值
            // ========================================================================
            double basePercent = Config.levisEchoDamageBasePercent;
            double perLevelPercent = Config.levisEchoDamagePerLevelPercent;
            double damagePercent = basePercent + (enchantLevel - 1) * perLevelPercent;
            // 转为百分比小数（如 5% → 0.05）
            float damage = (float) (target.getMaxHealth() * (damagePercent / 100.0));

            if (damage > 0) {
                // 使用魔法伤害，不触发盔甲减伤
                DamageSource magicDamage = target.damageSources().magic();
                // 直接造成伤害（跳过 LivingHurtEvent 递归检测）
                target.hurt(magicDamage, damage);
            }
        }
    }

    // ========================================================================
    // LevelTickEvent：定期清理超时标记（每 100 tick 扫描一次）
    // ========================================================================
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        // 仅服务端、仅 end phase（避免过早计算）
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        // 每 100 tick 清理一次，减少性能开销
        if (serverLevel.getGameTime() % 100 != 0) return;

        List<Entity> toClean = new ArrayList<>();

        // 扫描世界中所有实体
        for (Entity entity : serverLevel.getAllEntities()) {
            if (!(entity instanceof LivingEntity)) continue;

            CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
            if (data.isEmpty()) continue;

            int stacks = data.getInt(KEY_STACKS);
            long lastHit = data.getLong(KEY_LAST_HIT);
            long elapsed = serverLevel.getGameTime() - lastHit;

            if (stacks <= 0 || elapsed > Config.levisEchoMarkTimeout) {
                toClean.add(entity);
            }
        }

        // 清除超时或无效的标记
        for (Entity entity : toClean) {
            entity.getPersistentData().remove(TAG_ROOT);
        }
    }
}