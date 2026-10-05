package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.item.handler.ResidualScytheHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端按下丢弃键后发送的 C2S 大招请求，所有条件由服务端重新校验。 */
public class AnnihilationOrbPacket {
    public AnnihilationOrbPacket() {
    }

    public AnnihilationOrbPacket(FriendlyByteBuf buffer) {
    }

    public void encode(FriendlyByteBuf buffer) {
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ResidualScytheHandler.tryFireAnnihilationOrb(player);
            }
        });
        context.setPacketHandled(true);
    }
}
