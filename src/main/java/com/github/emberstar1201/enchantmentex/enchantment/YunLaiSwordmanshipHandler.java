package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.entity.CrescentEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// "云来剑法·重制"附魔事件处理器
//
// 【效果一】攻击速度提升（PlayerTickEvent）
//   使用 AttributeModifier 直接修改 Attributes.ATTACK_SPEED，
//   在手持附魔剑时永久添加 +3.5 攻击速度（ADDITION 模式）。
//   切换武器或放下武器时自动移除修饰符。
//   原版剑基础攻击速度约 4.0，+3.5 后约 7.5，攻击间隔大幅缩短。
//
//   为什么不用 attackStrengthTicker？
//     attackStrengthTicker 每 tick 最多 +1（由原版硬编码控制），
//     我们强行 +3/+49 的方式不生效——因为原版在 tick 末尾会
//     将 ticker 重新 clamp 到合法范围（0 ~ delay）。
//
// 【效果二】月牙形剑气（LivingHurtEvent）
//   90%概率在玩家眼前生成1道淡蓝色月牙形剑气
//
// 【注意】必须使用 Bus.FORGE，因为 PlayerTickEvent 和
//   LivingHurtEvent 都是 Forge 事件
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YunLaiSwordmanshipHandler {

    // 攻击速度修饰符 UUID（固定，用于检测修饰符是否存在）
    private static final UUID ATK_SPEED_MOD_UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    // 攻击速度修饰符：+3.5（ADDITION 模式）
    private static final AttributeModifier ATK_SPEED_BOOST =
            new AttributeModifier(
                    ATK_SPEED_MOD_UUID,
                    "YunLai Swordmanship attack speed bonus",
                    3.5,
                    AttributeModifier.Operation.ADDITION
            );

    // ========================================================================
    // 【效果一】攻击速度提升（PlayerTickEvent）
    //
    // 原理：
    //   每 tick 检查玩家主手是否持有带"云来剑法·重制"附魔的剑。
    //   - 是且修饰符不存在 → 添加修饰符
    //   - 否且修饰符存在 → 移除修饰符
    //
    // 这样做天然处理了武器切换：切到其他武器时附魔等级为 0，
    //   修饰符在下一个 tick 被移除。
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.isCreative() || player.isSpectator()) return;

        // 获取攻击速度属性实例
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) return;

        ItemStack mainHand = player.getMainHandItem();
        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.YUNLAI_SWORDMANSHIP.get(), mainHand);

        boolean hasModifier = attr.hasModifier(ATK_SPEED_BOOST);

        if (enchantLevel > 0 && !hasModifier) {
            // 手持附魔剑且修饰符不存在 → 添加
            attr.addTransientModifier(ATK_SPEED_BOOST);
        } else if (enchantLevel <= 0 && hasModifier) {
            // 未持有附魔剑但修饰符存在 → 移除
            attr.removeModifier(ATK_SPEED_MOD_UUID);
        }
        // 其他情况（已持有且已添加、未持有且未添加）不做操作
    }

    // ========================================================================
    // 【效果二】月牙形剑气（LivingHurtEvent）
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

        // 服务端才生成剑气
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }

        // 概率判定
        double chance = Config.yunlaiSwordCrescentChance;
        if (level.random.nextDouble() >= chance) {
            return;
        }

        // 剑气伤害基准 = 本次攻击伤害 × 配置倍率
        float crescentDamage = event.getAmount() * (float) Config.yunlaiSwordCrescentDamageMultiplier;

        // 在玩家视线方向生成1道淡蓝色剑气（type=1）
        spawnCrescent(level, player, crescentDamage, 1, 0, 1);
    }

    // ========================================================================
    // 生成月牙形剑气
    // ========================================================================
    private static void spawnCrescent(Level level, Player player,
                                      float damage, int count,
                                      double spreadDegrees, int type) {
        Vec3 direction = player.getLookAngle();
        Vec3 spawnPos = player.getEyePosition().add(direction.scale(0.8));

        double speed = Config.yunlaiSwordCrescentSpeed;
        double maxDist = Config.yunlaiSwordCrescentMaxDistance;

        if (count <= 1) {
            // 单道剑气：直接沿视线方向飞行
            CrescentEntity crescent = new CrescentEntity(
                    level, player, spawnPos, direction,
                    (float) speed, damage, maxDist, type);
            level.addFreshEntity(crescent);
        } else {
            // 多道剑气：扇形分布
            Vec3 up = new Vec3(0, 1, 0);
            if (Math.abs(direction.y) > 0.99) {
                up = new Vec3(1, 0, 0);
            }
            Vec3 right = direction.cross(up).normalize();

            double spreadRad = Math.toRadians(spreadDegrees);
            double step = count > 1 ? spreadRad / (count - 1) : 0;
            double startAngle = -spreadRad / 2.0;

            for (int i = 0; i < count; i++) {
                double angle = startAngle + step * i;
                Vec3 dir = direction.scale(Math.cos(angle))
                        .add(right.scale(Math.sin(angle)))
                        .normalize();

                CrescentEntity crescent = new CrescentEntity(
                        level, player, spawnPos, dir,
                        (float) speed, damage, maxDist, type);
                level.addFreshEntity(crescent);
            }
        }
    }
}