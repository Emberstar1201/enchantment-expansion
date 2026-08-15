package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.FlywheelEffectHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【飞轮冲刺请求数据包】
//
// 空数据包（无额外字段），仅作为信号：
// 客户端按下 R 键时发送，服务端收到后触发冲刺逻辑
// ========================================================================
public class FlywheelDashPacket {

    // 客户端发送时使用的空构造函数
    public FlywheelDashPacket() {}

    // 服务端解码时使用的构造函数（从 buf 读取字段）
    // 当前数据包无字段，直接构造空实例即可
    public FlywheelDashPacket(FriendlyByteBuf buf) {}

    // ========================================================================
    // 编码：将数据包写入网络缓冲区
    // 当前无字段，无需写入任何数据
    // ========================================================================
    public void encode(FriendlyByteBuf buf) {}

    // ========================================================================
    // 处理：服务端收到数据包后的回调
    // enqueueWork 确保逻辑在主线程执行（线程安全）
    // ========================================================================
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            // 获取发送此数据包的服务器玩家
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // 触发飞轮冲刺核心逻辑
                FlywheelEffectHandler.handleDashRequest(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}