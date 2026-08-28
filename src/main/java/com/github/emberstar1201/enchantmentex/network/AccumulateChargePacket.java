package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.AccumulateHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【蓄积】C2S 数据包：右键按住状态同步
//
// 客户端检测到「右键」按下/松开时发送此包，
// 服务端据此更新玩家的蓄力状态（存在 AccumulateHandler 中）。
// 服务端无法直接读取客户端鼠标，必须通过数据包通信。
// ========================================================================
public class AccumulateChargePacket {

    // 右键是否被按住（true = 按住蓄力，false = 松开）
    private final boolean heldDown;

    public AccumulateChargePacket(boolean heldDown) {
        this.heldDown = heldDown;
    }

    // 空构造：解码用
    public AccumulateChargePacket(FriendlyByteBuf buf) {
        this.heldDown = buf.readBoolean();
    }

    // 编码：把布尔写入缓冲区
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.heldDown);
    }

    // ========================================================================
    // 服务端处理：更新玩家的蓄力模式状态
    // ========================================================================
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            AccumulateHandler.setHoldRight(player.getUUID(), this.heldDown);
        });
        context.setPacketHandled(true);
    }
}