package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.client.GiftPromptScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端要求客户端确认是否赠送珍贵物品。 */
public final class GiftPromptPacket {
    private final int entityId;
    private final String itemName;

    public GiftPromptPacket(int entityId, String itemName) {
        this.entityId = entityId;
        this.itemName = itemName;
    }

    public GiftPromptPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readVarInt();
        this.itemName = buffer.readUtf(128);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeUtf(this.itemName, 128);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> GiftPromptScreen.open(this.entityId, this.itemName));
            }
        });
        context.setPacketHandled(true);
    }
}
