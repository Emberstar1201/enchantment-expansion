package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import com.github.emberstar1201.enchantmentex.entity.GirlWorkMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端请求切换友好娘工作模式的数据包。 */
public class SetGirlWorkModePacket {
    private final int entityId;
    private final int modeOrdinal;

    public SetGirlWorkModePacket(int entityId, GirlWorkMode mode) {
        this.entityId = entityId;
        this.modeOrdinal = mode.ordinal();
    }

    public SetGirlWorkModePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.modeOrdinal = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeVarInt(this.modeOrdinal);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender == null) {
                return;
            }
            Entity entity = sender.level().getEntity(this.entityId);
            GirlWorkMode mode = GirlWorkMode.fromOrdinal(this.modeOrdinal);
            if (entity instanceof FriendlyGirlInventory girl
                    && girl.isTamed() && girl.isOwnedBy(sender)
                    && sender.distanceToSqr(entity) <= 256.0D
                    && girl.supportsWorkMode(mode)) {
                girl.setWorkMode(mode);
            }
        });
        context.setPacketHandled(true);
    }
}
