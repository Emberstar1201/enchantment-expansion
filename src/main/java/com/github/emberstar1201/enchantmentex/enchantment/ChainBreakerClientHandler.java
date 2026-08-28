package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.network.ChainBreakerPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// ========================================================================
// 【连锁挖掘】客户端处理器
//
// 功能：每 tick 检测「连锁挖掘」触发键（默认 ~）的按住状态，
//       状态变化时发送 C2S 数据包到服务端，从而让服务端知道
//       玩家当前是否处于“按住触发键”的连锁模式。
//
// 设计说明：
//   - 按住的检测用状态变化比较（press 状态改变才发包），
//     避免每帧都发数据包浪费带宽。
//   - KeyMapping 在 ChainBreakerKeybindings 中注册，玩家可在
//     控制 → 按键绑定 中自定义。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChainBreakerClientHandler {

    // 上一帧的按键状态（用于检测状态变化）
    private static boolean lastPressed = false;

    // ========================================================================
    // 客户端设置完成后 → 将 ClientTick 注册到 Forge 事件总线
    // 因为 ClientTickEvent 走 Forge 总线而非 Mod 总线
    // ========================================================================
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MinecraftForge.EVENT_BUS.addListener(ChainBreakerClientHandler::onClientTick)
        );
    }

    // ========================================================================
    // 每 tick 检测触发键按住状态，状态变化时发 C2S 数据包
    // ========================================================================
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean pressed = ChainBreakerKeybindings.CHAIN_BREAKER_KEY.isDown();
        if (pressed != lastPressed) {
            lastPressed = pressed;
            // 按住 → true；松开 → false
            NetworkHandler.CHANNEL.sendToServer(new ChainBreakerPacket(pressed));
        }
    }
}