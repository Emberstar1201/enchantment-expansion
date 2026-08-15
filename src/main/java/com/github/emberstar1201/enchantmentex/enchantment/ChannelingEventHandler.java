package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
// 【引雷 II / III】事件处理器
//
// 核心逻辑：
//   当掷出的三叉戟（ThrownTrident）击中实体时，检查附魔等级：
//   - 引雷 III（Channeling III）：任何天气/任何维度都生成闪电
//   - 引雷 II（Channeling II）：仅在下雨天气且非下界/末地时生成闪电
//   - 原版引雷 I：由原版 ThrownTrident.onHitEntity() 自己处理，此处不做干预
//
// 兼容性：
//   引雷 I/II/III 为互斥附魔，同一把三叉戟只能附一个
//   我们不禁用原版的闪电生成，因此在下雨+雷暴同时满足时，
//   引雷 II 的检测会覆盖，但原版引雷 I 如果没有附魔则不会触发
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID)
public class ChannelingEventHandler {

    @SubscribeEvent
    public static void onTridentImpact(ProjectileImpactEvent event) {
        // ---------- 前置过滤 ----------

        // 只处理实体击中事件（排除方块击中）
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;

        // 只处理三叉戟投射物
        if (!(event.getProjectile() instanceof ThrownTrident trident)) return;

        // 仅在服务端处理（避免客户端重复生成闪电）
        if (trident.level().isClientSide()) return;

        // ---------- 获取三叉戟的附魔信息 ----------

        // getPickupItem() 返回三叉戟的 ItemStack 副本（包含附魔）
        ItemStack tridentStack = trident.getPickupItem();

        int channelingIIILevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.CHANNELING_III.get(), tridentStack);
        int channelingIILevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.CHANNELING_II.get(), tridentStack);

        // ---------- 判断是否生成闪电 ----------

        boolean shouldSpawnLightning = false;

        if (channelingIIILevel > 0) {
            // 引雷 III：终极版本，任何天气、任何维度均可触发
            shouldSpawnLightning = true;
        } else if (channelingIILevel > 0) {
            // 引雷 II：普通雨天即可触发（无需雷暴）
            // level.isRaining() 在雨天/雷暴时返回 true，下界/末地返回 false
            shouldSpawnLightning = trident.level().isRaining();
        }

        if (!shouldSpawnLightning) return;

        // ---------- 生成闪电 ----------

        // 获取被击中的实体位置
        BlockPos hitPos = entityHit.getEntity().blockPosition();

        // 创建闪电实体（与原版引雷的创建方式一致）
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(trident.level());
        if (lightning == null) return;

        lightning.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(hitPos));

        // 设置闪电的「发起者」为三叉戟的投掷者（用于统计击杀归属等）
        if (trident.getOwner() instanceof ServerPlayer owner) {
            lightning.setCause(owner);
        }

        // 将闪电添加到世界中
        trident.level().addFreshEntity(lightning);

        // 播放三叉戟引雷音效（原版引雷相同的音效）
        trident.level().playSound(null,
                hitPos.getX(), hitPos.getY(), hitPos.getZ(),
                SoundEvents.TRIDENT_THUNDER,
                SoundSource.PLAYERS,
                5.0F, 1.0F);
    }
}