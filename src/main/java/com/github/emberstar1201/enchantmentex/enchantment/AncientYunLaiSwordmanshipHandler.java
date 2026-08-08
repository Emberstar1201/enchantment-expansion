package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "古·云来剑法"附魔事件处理器
//
// 【继承效果】（与云来剑法一致，1级数值）
//   1. 攻击距离提升：+0.5 格（PlayerTickEvent，独立 UUID）
//   2. 攻击冷却缩减：约 10%（LivingHurtEvent 伤害补正）
//
// 【额外效果】AOE 之力（LivingHurtEvent）
//   触发条件：玩家用带此附魔的剑攻击实体
//   效果：有 50% 概率对半径 3 格内所有敌人造成"等额伤害"
//   冷却：10 秒（200 tick），冷却期间不重复触发
//   视觉：触发时在玩家周围生成红色/金色粒子特效
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class AncientYunLaiSwordmanshipHandler {

    // 攻击距离 modifier 的固定 UUID（古·云来专用，与云来剑法独立）
    private static final UUID REACH_MODIFIER_UUID =
            UUID.fromString("c4d5e6f7-8901-4b2c-0d3e-4f5a6b7c8d9e");

    // PersistentData key
    private static final String LAST_LEVEL_KEY = "AncientYunLaiSword_LastReachLevel";
    // AOE 上次触发时间（gameTime）的存储 key
    private static final String AOE_COOLDOWN_KEY = "AncientYunLaiSword_AoeLastTrigger";

    // AOE 配置
    private static final double AOE_RADIUS = 3.0;          // AOE 半径（格）
    private static final double AOE_TRIGGER_CHANCE = 0.5;  // 触发概率 50%
    private static final long AOE_COOLDOWN_TICKS = 200L;    // 冷却 10 秒（200 tick）

    // ========================================================================
    // 【继承效果一】攻击距离提升（PlayerTickEvent.END）
    // 逻辑与 YunLaiSwordmanshipHandler 一致，但查询古·云来剑法附魔 + 独立 UUID
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        ItemStack mainHand = player.getMainHandItem();

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ANCIENT_YUNLAI_SWORDMANSHIP.get(), mainHand);
        int lastLevel = player.getPersistentData().getInt(LAST_LEVEL_KEY);

        if (enchantLevel == lastLevel) {
            return;
        }

        AttributeInstance reachAttr = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reachAttr == null) {
            player.getPersistentData().putInt(LAST_LEVEL_KEY, enchantLevel);
            return;
        }

        // 移除旧 modifier
        reachAttr.removeModifier(REACH_MODIFIER_UUID);

        if (enchantLevel > 0) {
            // 古·云来只有1级，加成 = getAttackReachBonus(1) = 0.5
            double bonus = AncientYunLaiSwordmanshipEnchantment.getAttackReachBonus(enchantLevel);
            AttributeModifier modifier = new AttributeModifier(
                    REACH_MODIFIER_UUID,
                    "Ancient YunLai Swordmanship Reach Bonus",
                    bonus,
                    AttributeModifier.Operation.ADDITION
            );
            reachAttr.addTransientModifier(modifier);
        }

        player.getPersistentData().putInt(LAST_LEVEL_KEY, enchantLevel);
    }

    // ========================================================================
    // 【继承效果二 + 额外效果】冷却缩减 + AOE 之力（LivingHurtEvent.Pre）
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.ANCIENT_YUNLAI_SWORDMANSHIP.get(), mainHand);
        if (enchantLevel <= 0) {
            return;
        }

        // ---------- 【继承效果二】冷却缩减（伤害补正）----------
        applyCooldownReduction(event, player, enchantLevel);

        // ---------- 【额外效果】AOE 之力 ----------
        tryTriggerAoe(event, player);
    }

    // ========================================================================
    // 冷却缩减：与 YunLaiSwordmanshipHandler 相同的补正逻辑
    // 数值取古·云来1级：reduction = getCooldownReduction(1) ≈ 0.10
    // ========================================================================
    private static void applyCooldownReduction(LivingHurtEvent event, Player player, int level) {
        float strengthScale = player.getAttackStrengthScale(0.5f);
        if (strengthScale >= 1.0f) {
            return; // 满力攻击无需补正
        }

        double reduction = AncientYunLaiSwordmanshipEnchantment.getCooldownReduction(level);
        if (reduction <= 0.0) {
            return;
        }

        float safeScale = Math.max(strengthScale, 0.01f);
        float originalAmount = event.getAmount();
        float recovered = originalAmount * (1.0f - safeScale) / safeScale * (float) reduction;
        event.setAmount(originalAmount + recovered);
    }

    // ========================================================================
    // AOE 触发逻辑
    //   1. 检查冷却（lastTrigger + 200 tick <= now）
    //   2. 概率判定（50%）
    //   3. AABB 获取半径3格内 LivingEntity，排除自身和主目标
    //   4. 对每个敌人造成"等额伤害"（= event.getAmount()）
    //   5. 生成红色/金色粒子特效
    // ========================================================================
    private static void tryTriggerAoe(LivingHurtEvent event, Player player) {
        Level level = player.level();
        long now = level.getGameTime();

        // 【冷却检查】10秒内不重复触发
        long lastTrigger = player.getPersistentData().getLong(AOE_COOLDOWN_KEY);
        if (now - lastTrigger < AOE_COOLDOWN_TICKS) {
            return;
        }

        // 【概率判定】50% 触发
        if (level.random.nextDouble() >= AOE_TRIGGER_CHANCE) {
            return;
        }

        // 主目标（本次攻击的目标）
        LivingEntity primaryTarget = event.getEntity();

        // 【AABB 范围查询】以玩家为中心，半径3格的盒子
        AABB aabb = new AABB(
                player.getX() - AOE_RADIUS, player.getY() - AOE_RADIUS, player.getZ() - AOE_RADIUS,
                player.getX() + AOE_RADIUS, player.getY() + AOE_RADIUS, player.getZ() + AOE_RADIUS
        );
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, aabb);

        // 等额伤害 = 本次攻击造成的伤害
        float aoeDamage = event.getAmount();
        DamageSource source = event.getSource();

        int hitCount = 0;
        for (LivingEntity entity : nearbyEntities) {
            // 排除：玩家自身、本次的主目标（避免双倍伤害）
            if (entity == player || entity == primaryTarget) {
                continue;
            }
            // 排除：濒死/无效目标
            if (entity.isDeadOrDying()) {
                continue;
            }
            // 【友军过滤】避免误伤友好生物（村民、宠物、队友等）
            //   1. isAlliedTo(player)：同一 Team 的队友（原版 Team 系统判定）
            //   2. TamableAnimal 且 owner 是玩家：玩家驯服的宠物（狼、猫、鹦鹉、骆驼等）
            //   3. 其他 Player：避免误伤同服玩家（即便不在同一 Team）
            //   4. Villager：村民是中立友好生物，不应被剑气误伤
            // 注意：羊、牛、猪等家畜不属于上述任一类别，仍会被剑气扫到
            //      （符合"剑气无差别横扫"主题，且与原版横扫之刃一致）
            if (entity.isAlliedTo(player)) {
                continue;
            }
            if (entity instanceof TamableAnimal tamable && tamable.getOwner() == player) {
                continue;
            }
            if (entity instanceof Player) {
                continue;
            }
            if (entity instanceof Villager) {
                continue;
            }
            // 对周围敌人造成等额伤害
            entity.hurt(source, aoeDamage);
            hitCount++;
        }

        // 仅在确实命中了额外敌人时才记录冷却 + 生成粒子（避免空挥也进入冷却）
        if (hitCount > 0) {
            // 记录触发时间，进入10秒冷却
            player.getPersistentData().putLong(AOE_COOLDOWN_KEY, now);
            // 生成红色/金色粒子特效
            spawnAoeParticles(level, player);
        }
    }

    // ========================================================================
    // AOE 触发时的粒子特效
    //   - 红石粒子（REDSTONE）：红色，象征剑气
    //   - 末地烛粒子（END_ROD）：金白色，象征云来宗门气劲
    // 仅在 ServerLevel 生成（服务端 sendParticles 会同步到客户端，所有人可见）
    // ========================================================================
    private static void spawnAoeParticles(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return; // 客户端不处理（服务端负责广播粒子）
        }

        double cx = player.getX();
        double cy = player.getY() + 1.0; // 腰部高度，视觉更居中
        double cz = player.getZ();

        // 红色粒子（红石粉尘粒子）：在玩家周围散布（象征剑气爆发）
        // 【API 说明】sendParticles 要求传入 ParticleOptions 实例（粒子数据），
        //   而非 ParticleType（粒子类型工厂）。
        //   DustParticleOptions.REDSTONE 是原版预制的红色粒子数据实例（红色 + 默认大小），
        //   而 ParticleTypes.DUST 只是 ParticleType<DustParticleOptions> 类型工厂，
        //   无法直接作为 sendParticles 的参数。
        serverLevel.sendParticles(
                DustParticleOptions.REDSTONE,
                cx, cy, cz,
                20,
                AOE_RADIUS * 0.5, 0.3, AOE_RADIUS * 0.5,
                0.1
        );

        // 金白色末地烛粒子：向上飘散（象征云来气劲升腾）
        serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                cx, cy, cz,
                15,
                AOE_RADIUS * 0.3, 0.5, AOE_RADIUS * 0.3,
                0.05
        );
    }
}
