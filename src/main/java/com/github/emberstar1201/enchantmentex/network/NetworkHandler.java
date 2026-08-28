package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

// ========================================================================
// 【网络通道】飞轮效应数据包注册
//
// 用于客户端 → 服务端的 R 键冲刺信号传输
// 协议版本校验确保客户端与服务端模组版本匹配
// ========================================================================
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    // 简单通道：用于 C2S 单向通信（飞轮冲刺请求）
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.of(EnchantmentExpansion.MODID + ":main", ':'),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,   // 客户端接受的服务端版本
            PROTOCOL_VERSION::equals    // 服务端接受的客户端版本
    );

    // 数据包 ID 自增计数器
    private static int packetId = 0;

    // ========================================================================
    // 注册所有数据包
    // 在主类构造函数中调用
    // ========================================================================
    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                FlywheelDashPacket.class,
                FlywheelDashPacket::encode,
                FlywheelDashPacket::new,    // 通过 FriendlyByteBuf 构造
                FlywheelDashPacket::handle
        );

        // ================================================================
        // 无烟冲击数据包（C2S 滚轮调速信号）
        // ================================================================
        CHANNEL.registerMessage(
                packetId++,
                SmokelessDashPacket.class,
                SmokelessDashPacket::encode,
                SmokelessDashPacket::new,
                SmokelessDashPacket::handle
        );

        // ================================================================
        // 无烟冲击同步包（S2C 速度倍率同步）
        // 服务端每 10 tick 发送，供客户端 HUD 显示当前速度倍率
        // ================================================================
        CHANNEL.registerMessage(
                packetId++,
                SmokelessDashSyncPacket.class,
                SmokelessDashSyncPacket::encode,
                SmokelessDashSyncPacket::new,
                SmokelessDashSyncPacket::handle
        );

        // ================================================================
        // 连锁挖掘数据包（C2S 按键按住状态同步）
        // 客户端按住/松开 ~ 键时发送，服务端据此决定是否触发连锁
        // ================================================================
        CHANNEL.registerMessage(
                packetId++,
                ChainBreakerPacket.class,
                ChainBreakerPacket::encode,
                ChainBreakerPacket::new,
                ChainBreakerPacket::handle
        );

        // ================================================================
        // 蓄积数据包（C2S 右键按住状态同步）
        // 客户端按住/松开右键时发送，服务端据此管理蓄力计时
        // ================================================================
        CHANNEL.registerMessage(
                packetId++,
                AccumulateChargePacket.class,
                AccumulateChargePacket::encode,
                AccumulateChargePacket::new,
                AccumulateChargePacket::handle
        );
    }
}