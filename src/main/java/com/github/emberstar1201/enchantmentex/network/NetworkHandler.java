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
            new ResourceLocation(EnchantmentExpansion.MODID, "main"),
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
    }
}