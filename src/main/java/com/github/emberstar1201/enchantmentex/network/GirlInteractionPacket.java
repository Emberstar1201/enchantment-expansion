package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 少女交互面板的客户端请求包。所有状态判定都在服务端执行。 */
public final class GirlInteractionPacket {
    public static final int GIFT = 0;
    public static final int FOLLOW = 1;
    public static final int STAY = 2;
    public static final int RENAME = 3;

    private final int entityId;
    private final int action;
    /** RENAME 动作携带的新名字；其它动作恒为空串。 */
    private final String name;

    public GirlInteractionPacket(int entityId, int action) {
        this(entityId, action, "");
    }

    public GirlInteractionPacket(int entityId, int action, String name) {
        this.entityId = entityId;
        this.action = action;
        this.name = name == null ? "" : name;
    }

    public GirlInteractionPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readVarInt();
        this.action = buffer.readVarInt();
        this.name = buffer.readUtf(64);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeVarInt(this.action);
        buffer.writeUtf(this.name, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            Entity entity = sender.level().getEntity(this.entityId);
            if (!(entity instanceof FriendlyGirlInventory girl)
                    || !girl.isAlive()
                    || girl.isRemoved()
                    || sender.distanceToSqr(entity) > 256.0D) {
                return;
            }

            if (this.action == GIFT) {
                if (!girl.isTamed() || !girl.isOwnedBy(sender)) {
                    return;
                }
                GiftPacket giftPacket = new GiftPacket(this.entityId, false);
                giftPacket.handle(() -> context);
                return;
            }
            if (!girl.isTamed() || !girl.isOwnedBy(sender)) {
                return;
            }
            switch (this.action) {
                case FOLLOW -> girl.setOrderedToSit(false);
                case STAY -> girl.setOrderedToSit(true);
                case RENAME -> applyRename(entity);
                default -> {
                }
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * RENAME：把名字写入实体 CustomName。
     * CustomName 随实体 NBT 按存档保存，因此每只少女名字独立、互不跨存档共享。
     */
    private void applyRename(Entity entity) {
        String value = this.name == null ? "" : this.name.trim();
        if (value.isEmpty()) {
            return;
        }
        if (value.length() > 32) {
            value = value.substring(0, 32);
        }
        entity.setCustomName(Component.literal(value));
    }
}
