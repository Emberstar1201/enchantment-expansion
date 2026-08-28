package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.ChainBreakerHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【连锁挖掘】C2S 数据包：按键按住状态同步
//
// 客户端检测到 ~ 键按下/松开时发送此包，
// 服务端据此更新玩家的连锁模式状态（存在 ChainBreakerHandler 中）。
// 因为服务端无法直接读取客户端按键，必须通过数据包通信。
// ========================================================================
public class ChainBreakerPacket {

    // 按键是否被按住（true = 按下，false = 松开）
    private final boolean heldDown;

    public ChainBreakerPacket(boolean heldDown) {
        this.heldDown = heldDown;
    }

    // 空构造：解码用
    public ChainBreakerPacket(FriendlyByteBuf buf) {
        this.heldDown = buf.readBoolean();
    }

    // 编码：把布尔写入缓冲区
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.heldDown);
    }

    // ========================================================================
    // 服务端处理：更新玩家的连锁模式状态
    // ========================================================================
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ChainBreakerHandler.setChainMode(player.getUUID(), this.heldDown);
        });
        context.setPacketHandled(true);
    }
}