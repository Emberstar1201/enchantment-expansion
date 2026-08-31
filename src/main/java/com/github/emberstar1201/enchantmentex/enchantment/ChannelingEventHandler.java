package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// ========================================================================
// 【雷电之戟 Lightning Spear】事件处理器（统一引雷 II/III）
//
// 核心逻辑：
//   当掷出的三叉戟（ThrownTrident）击中实体时，检查「雷电之戟」等级：
//   - II 级：任何天气、任何维度（包括下界和末地）都生成闪电
//   - I  级：仅在下雨天气（无需雷暴）时生成闪电
//   - 原版引雷 I：由原版 ThrownTrident.onHitEntity() 处理，此处不干预
//
// 兼容性：
//   雷电之戟附魔 checkCompatibility 已与原版引雷 I 互斥。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID)
public class ChannelingEventHandler {

    @SubscribeEvent
    public static void onTridentImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(event.getProjectile() instanceof ThrownTrident trident)) return;
        if (trident.level().isClientSide()) return;

        ItemStack tridentStack = trident.getPickupItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.LIGHTNING_SPEAR.get(), tridentStack);
        if (level <= 0) return;

        boolean shouldSpawnLightning;
        if (level >= 2) {
            // II 级：任意天气 + 任意维度
            shouldSpawnLightning = true;
        } else {
            // I  级：下雨即可（雷暴/普通雨天都满足；下界/末地 isRaining=false）
            shouldSpawnLightning = trident.level().isRaining();
        }
        if (!shouldSpawnLightning) return;

        BlockPos hitPos = entityHit.getEntity().blockPosition();
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(trident.level());
        if (lightning == null) return;

        lightning.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(hitPos));
        if (trident.getOwner() instanceof ServerPlayer owner) {
            lightning.setCause(owner);
        }
        trident.level().addFreshEntity(lightning);

        trident.level().playSound(null,
                hitPos.getX(), hitPos.getY(), hitPos.getZ(),
                SoundEvents.TRIDENT_THUNDER,
                SoundSource.PLAYERS,
                5.0F, 1.0F);
    }
}