package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.DeepSeaRippleHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【深海的涟漪】C2S 滚轮调整数据包
//
// 客户端在“Shift+滚轮”且玩家穿带附魔靴子且在水中时发送。
// 字段 delta：正值 = 加速（向上滚），负值 = 减速（向下滚）。
// 单位为“游泳速度倍率变化量”，例如 0.25 表示倍率 + 0.25。
// ========================================================================
public class DeepSeaRippleScrollPacket {

    private final double delta;

    public DeepSeaRippleScrollPacket(double delta) {
        this.delta = delta;
    }

    // 解码：从网络流恢复 double
    public DeepSeaRippleScrollPacket(FriendlyByteBuf buf) {
        this.delta = buf.readDouble();
    }

    // 编码：写入网络流
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.delta);
    }

    // 处理：在主线程调用 DeepSeaRippleHandler 调整服务端倍率
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                DeepSeaRippleHandler.handleScrollDelta(player, delta);
            }
        });
        ctx.setPacketHandled(true);
    }
}
