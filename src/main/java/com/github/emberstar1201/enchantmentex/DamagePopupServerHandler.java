package com.github.emberstar1201.enchantmentex;

import com.github.emberstar1201.enchantmentex.network.DamagePopupPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【伤害/治疗浮动数字】服务端事件处理
//
// LivingHurtEvent / LivingHealEvent 仅在服务端触发（伤害与回血计算
// 都是服务端权威逻辑），因此必须由服务端捕获事件并通过自定义包
// 广播给附近玩家的客户端，由客户端负责视觉渲染。
//
// 【广播范围】64 格内的所有玩家（含受伤实体自己）。
//
// 【显示规则】
//   - 伤害：红色数字（前缀 "-"）
//   - 治疗：绿色数字（前缀 "+"）
//   - 数量取伤害/治疗事件的最终数值（受其他模组/附魔修改后的值）
//
// 可通过 Config.damagePopup.enabled 总开关关闭。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class DamagePopupServerHandler {

    // 广播半径（平方）：超过 64 格的玩家不发送（节省带宽）
    private static final double SEND_RANGE_SQR = 64.0 * 64.0;

    // ========================================================================
    // 受伤事件 → 红色数字
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!Config.enableDamagePopup) return;
        sendPopup(event.getEntity(), event.getAmount(), false);
    }

    // ========================================================================
    // 治疗事件 → 绿色数字
    // 覆盖：药水回血、自然恢复、金苹果、附魔治疗等所有 heal() 路径
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!Config.enableDamagePopup) return;
        sendPopup(event.getEntity(), event.getAmount(), true);
    }

    // ========================================================================
    // 工具：向附近玩家广播浮字包
    // 浮字起点固定在事件发生时刻的实体头顶位置（不跟随实体移动），
    // 后续上飘动画由客户端负责。
    // ========================================================================
    private static void sendPopup(LivingEntity target, float amount, boolean heal) {
        if (amount <= 0) return;
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        // 浮字起点：实体头顶上方 0.3 格
        DamagePopupPacket packet = new DamagePopupPacket(
                target.getX(),
                target.getY() + target.getBbHeight() + 0.3,
                target.getZ(),
                amount,
                heal
        );

        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(target) <= SEND_RANGE_SQR) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        packet
                );
            }
        }
    }
}
