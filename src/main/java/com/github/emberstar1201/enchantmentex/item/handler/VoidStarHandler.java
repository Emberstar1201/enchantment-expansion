package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【虚空之星】事件处理器
//
// 核心效果（手持 + 盔甲嵌入均生效）：
//   1. 免疫摔落伤害 —— LivingFallEvent 把伤害倍率设为 0 并取消事件
//   2. 免疫虚空伤害 —— LivingHurtEvent 取消 FELL_OUT_OF_WORLD 伤害
//   3. 坠入虚空时传送回出生点 —— PlayerTickEvent 检测 Y 低于世界底部
//
// 【为什么效果 2 与 3 要并存？】
//   只免疫伤害 → 玩家会永远卡在虚空里下坠（无法脱身，且区块卸载后可能窒息）；
//   只传送不免疫 → 传送前那几 tick 仍会挨虚空伤害，低血量玩家可能先被秒。
//   两者配合：伤害免疫保证不会被秒，传送保证能被拉回安全位置。
//
// 【仅服务端处理】
//   传送与伤害判定都必须在服务端执行，客户端执行会造成位置回弹（反作弊/同步冲突）。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VoidStarHandler {

    /** 触发传送的深度：高于世界底部多少格开始救援 */
    private static final int VOID_TRIGGER_OFFSET = 5;

    // ========================================================================
    // 【LivingFallEvent】免疫摔落伤害
    //   事件在实体落地、结算摔落伤害之前触发，是取消摔落的最佳时机。
    //   双重保险：setDamageMultiplier(0) + setCanceled(true)。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!isHoldingOrWearingVoidStar(player)) return;

        event.setDamageMultiplier(0.0F);
        event.setCanceled(true);
        player.resetFallDistance();  // 清空累计高度，避免影响下一次落地判定
    }

    // ========================================================================
    // 【LivingHurtEvent】免疫虚空伤害
    //   FELL_OUT_OF_WORLD = 掉出世界底部（虚空）造成的伤害类型。
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!isHoldingOrWearingVoidStar(player)) return;

        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
        }
    }

    // ========================================================================
    // 【PlayerTickEvent】坠入虚空 → 传送回出生点
    //
    // 触发条件：Y 低于所在维度底部 5 格（主世界 -64 → -69，下界/末地 0 → -5）。
    // 目标位置优先级：床/重生锚 > 世界默认出生点。
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!isHoldingOrWearingVoidStar(serverPlayer)) return;

        double voidThreshold = serverPlayer.level().getMinBuildHeight() - VOID_TRIGGER_OFFSET;
        if (serverPlayer.getY() < voidThreshold) {
            teleportToSpawn(serverPlayer);
        }
    }

    // ========================================================================
    // 工具方法：把玩家传送回床/重生锚，没有则回到世界默认出生点
    // ========================================================================
    private static void teleportToSpawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel targetLevel;
        BlockPos targetPos = player.getRespawnPosition();  // 床/重生锚（未设置则为 null）
        if (targetPos != null) {
            targetLevel = server.getLevel(player.getRespawnDimension());
        } else {
            targetLevel = server.overworld();
        }
        if (targetLevel == null) {
            targetLevel = server.overworld();
        }
        if (targetPos == null) {
            targetPos = targetLevel.getSharedSpawnPos();
        }

        // 传送前清理状态，避免落地瞬间再次结算摔落/惯性
        player.stopRiding();
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);

        player.teleportTo(targetLevel,
                targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D,
                player.getYRot(), player.getXRot());

        player.displayClientMessage(
                Component.translatable("message.enchantment_expansion.void_star.teleported"), true);
    }

    // ========================================================================
    // 工具方法：检查玩家是否手持或穿戴虚空之星
    // ========================================================================
    public static boolean isHoldingOrWearingVoidStar(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.is(ModItems.VOID_STAR.get()) || offHand.is(ModItems.VOID_STAR.get())) {
            return true;
        }

        for (ItemStack armorPiece : player.getArmorSlots()) {
            if (!armorPiece.isEmpty() && armorPiece.hasTag()
                    && "void_star".equals(armorPiece.getTag().getString("EmbeddedStar"))) {
                return true;
            }
        }

        return false;
    }
}
