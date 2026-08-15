package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

// ========================================================================
// 【飞轮效应】客户端处理器
//
// 功能：
//   1. 注册 R 键映射（可在 Controls → 按键绑定中自定义）
//   2. 检测 R 键按下 → 发送 C2S 数据包触发冲刺
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FlywheelDashClientHandler {

    // 飞轮冲刺按键：默认 R 键，玩家可在 Controls 菜单中自定义
    public static final KeyMapping FLYWHEEL_DASH_KEY = new KeyMapping(
            "key." + EnchantmentExpansion.MODID + ".flywheel_dash",   // 国际化 key
            GLFW.GLFW_KEY_R,                                           // 默认 R 键
            "key.categories." + EnchantmentExpansion.MODID             // 按键分类
    );

    // ========================================================================
    // 注册按键映射（Mod 事件总线）
    // 在客户端初始化阶段调用
    // ========================================================================
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FLYWHEEL_DASH_KEY);
    }

    // ========================================================================
    // 客户端设置完成后，注册 ClientTick 到 Forge 事件总线
    // 因为 ClientTickEvent 在 Forge 总线而不是 Mod 总线上
    // ========================================================================
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MinecraftForge.EVENT_BUS.addListener(FlywheelDashClientHandler::onClientTick)
        );
    }

    // ========================================================================
    // 每帧检测 R 键是否被按下（Forge 事件总线）
    // consumeClick() 确保一次按下只触发一次
    // ========================================================================
    public static void onClientTick(ClientTickEvent event) {
        // 仅在 START 阶段执行，避免一帧触发两次
        if (event.phase != TickEvent.Phase.START) return;

        // 仅在游戏内且按下时触发
        if (FLYWHEEL_DASH_KEY.consumeClick()) {
            // 发送空数据包到服务端，服务端收到后执行冲刺逻辑
            NetworkHandler.CHANNEL.sendToServer(
                    new com.github.emberstar1201.enchantmentex.network.FlywheelDashPacket()
            );
        }
    }
}