package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.enchantment.SandevistanHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// ========================================================================
// 【斯安维斯坦激活请求包】（C2S：客户端 → 服务端）
//
// 玩家按下激活键（默认 X）时发送，无字段。
// 服务端收到后自行完成全部校验（胸甲附魔、冷却、饥饿值），
// 校验通过才会真正激活时缓效果。
// ========================================================================
public class SandevistanActivatePacket {

    public SandevistanActivatePacket() {
    }

    // 解码：无字段
    public SandevistanActivatePacket(FriendlyByteBuf buf) {
    }

    // 编码：无字段
    public void encode(FriendlyByteBuf buf) {
    }

    // 处理：服务端执行激活逻辑
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SandevistanHandler.tryActivate(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
