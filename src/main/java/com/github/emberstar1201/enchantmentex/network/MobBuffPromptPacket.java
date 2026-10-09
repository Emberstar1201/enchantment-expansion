package com.github.emberstar1201.enchantmentex.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端要求客户端显示开局的原版怪物强化确认界面。 */
public final class MobBuffPromptPacket {
    public MobBuffPromptPacket() {
    }

    public MobBuffPromptPacket(FriendlyByteBuf ignored) {
    }

    public void encode(FriendlyByteBuf ignored) {
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.github.emberstar1201.enchantmentex.client.MobBuffPromptScreen.open());
            }
        });
        context.setPacketHandled(true);
    }
}
