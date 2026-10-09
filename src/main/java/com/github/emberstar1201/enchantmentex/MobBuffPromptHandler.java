package com.github.emberstar1201.enchantmentex;

import com.github.emberstar1201.enchantmentex.network.MobBuffPromptPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/** 首次进入世界时发送原版怪物强化确认界面。 */
public final class MobBuffPromptHandler {
    private MobBuffPromptHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobBuffWorldState state = MobBuffWorldState.get(player.serverLevel());
        if (state.isPrompted()) {
            MobBuffRuntime.setEnabled(state.isEnabled());
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MobBuffPromptPacket());
    }
}
