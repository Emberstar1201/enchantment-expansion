package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.SmokelessDashHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【无烟冲击滚轮调整数据包】
//
// 客户端鼠标滚轮事件触发时发送，通知服务端玩家调整飞行速度。
// 包含一个 delta 字段（正值 = 加速，负值 = 减速）。
// 服务端收到后更新玩家 PersistentData 中的速度倍率值。
// ========================================================================
public class SmokelessDashPacket {

    private final double delta; // 本次滚轮调整量（受灵敏度影响）

    public SmokelessDashPacket(double delta) {
        this.delta = delta;
    }

    // 解码：从 buf 读取 delta
    public SmokelessDashPacket(FriendlyByteBuf buf) {
        this.delta = buf.readDouble();
    }

    // 编码：将 delta 写入 buf
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.delta);
    }

    // 处理：服务端收到后更新玩家的速度倍率
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SmokelessDashHandler.handleScrollDelta(player, delta);
            }
        });
        ctx.setPacketHandled(true);
    }
}