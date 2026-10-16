package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** 送礼请求。服务端始终读取玩家当前主手，客户端不能伪造礼物内容。 */
public final class GiftPacket {
    private final int entityId;
    private final boolean confirmed;

    public GiftPacket(int entityId, boolean confirmed) {
        this.entityId = entityId;
        this.confirmed = confirmed;
    }

    public GiftPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readVarInt();
        this.confirmed = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeBoolean(this.confirmed);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.serverLevel().getEntity(this.entityId);
            if (!(entity instanceof FriendlyGirlInventory girl)
                    || !girl.isAlive()
                    || girl.isRemoved()
                    || player.distanceToSqr(entity) > 256.0D
                    || !girl.isTamed()
                    || !girl.isOwnedBy(player)) {
                return;
            }

            ItemStack gift = player.getMainHandItem();
            if (gift.isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "chat.enchantment_expansion.girl.gift_empty"), true);
                return;
            }
            if (!this.confirmed && isPrecious(gift)) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new GiftPromptPacket(this.entityId, gift.getHoverName().getString()));
                return;
            }
            girl.receiveGift(player);
        });
        context.setPacketHandled(true);
    }

    public static boolean isPrecious(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.isDamageableItem()
                || stack.getItem() instanceof ArmorItem
                || stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof DiggerItem
                || stack.getItem() instanceof TieredItem
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof ShieldItem
                || stack.getItem() instanceof TridentItem
                || stack.getRarity() == Rarity.RARE
                || stack.getRarity() == Rarity.EPIC;
    }
}
