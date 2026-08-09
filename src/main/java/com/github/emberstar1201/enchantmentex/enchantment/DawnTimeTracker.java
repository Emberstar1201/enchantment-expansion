package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【拂晓】时间检测类 - 服务端每 20 刻（1秒）更新一次时段缓存
//
// 设计原因：
//   时间检测如果放在 PlayerTickEvent 中每 tick 执行，对每个在线玩家都要计算一次
//   而世界时间是全局共享的（所有玩家共享同一时间），只需在服务端统一计算一次
//   再广播到所有在线玩家的 PersistentData 中，DawnHandler 读取即可
//
// 缓存机制：
//   1. ServerTickEvent.END 触发时，若 server.tickCounter % 20 == 0（每秒一次）
//   2. 遍历所有 Overworld 玩家（拂晓只影响主世界，不下界/末地）
//   3. 用 player.level().getDayTime() 获取世界时间
//   4. DawnTimePeriod.fromGameTime() 判定时段
//   5. 写入 player.getPersistentData().putInt(DawnEnchantment.PERIOD_KEY, period.code)
//   6. DawnHandler 在 PlayerTickEvent / LivingHurtEvent 中读取该缓存值
//
// 注意：PersistentData 在玩家切换维度/退出重进时仍保留（序列化到 NBT）
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class DawnTimeTracker {

    // 每 20 刻（1秒）更新一次，降低性能开销
    private static final int UPDATE_INTERVAL_TICKS = 20;

    // 静态缓存当前时段（供不需要玩家上下文的逻辑快速读取）
    private static volatile DawnTimePeriod currentPeriod = DawnTimePeriod.DAY;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 仅在 END 阶段执行（所有实体 tick 完成后，数据更稳定）
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // 每 20 刻执行一次
        if (event.getServer().getTickCount() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        // 获取主世界 ServerLevel（拂晓只影响主世界昼夜循环）
        // 遍历所有维度找 Overworld（dimensionKey == Level.OVERWORLD）
        ServerLevel overworld = null;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                overworld = level;
                break;
            }
        }
        if (overworld == null) {
            return;  // 理论上不会发生，主世界一定存在
        }

        // 判定当前时段
        long gameTime = overworld.getDayTime();
        DawnTimePeriod period = DawnTimePeriod.fromGameTime(gameTime);
        currentPeriod = period;

        // 将时段写入所有在线玩家的 PersistentData（供 DawnHandler 读取）
        // 注意：仅写主世界玩家——但 PersistentData 是玩家级别的，
        //       下界/末地玩家也写入（在那些维度中"夜晚"概念不存在，但写入不会有害，
        //       因为 DawnHandler 会判断玩家所在维度是否为 Overworld）
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            player.getPersistentData().putInt(DawnEnchantment.PERIOD_KEY, period.getCode());
        }
    }

    // ========================================================================
    // 工具方法：获取静态缓存的当前时段
    // 供 DawnHandler 在没有玩家上下文时快速读取（如 LivingHurtEvent 中攻击者不是玩家）
    // ========================================================================
    public static DawnTimePeriod getCurrentPeriod() {
        return currentPeriod;
    }
}
