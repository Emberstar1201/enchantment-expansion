package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.MobBuffWorldState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端提交开局强化选择，并由服务端写入玩家持久数据。 */
public final class MobBuffChoicePacket {
    private final boolean enabled;

    public MobBuffChoicePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public MobBuffChoicePacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enabled);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            MobBuffWorldState.get(player.serverLevel()).choose(this.enabled);
        });
        context.setPacketHandled(true);
    }
}
