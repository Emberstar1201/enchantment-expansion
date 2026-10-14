package com.github.emberstar1201.enchantmentex.network;

import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import com.github.emberstar1201.enchantmentex.entity.SurvivorGirlEntity;
import com.github.emberstar1201.enchantmentex.world.ZombieGirlCampSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 少女交互面板的客户端请求包。所有状态和坐标判定都在服务端执行。 */
public final class GirlInteractionPacket {
    public static final int GIFT = 0;
    public static final int FOLLOW = 1;
    public static final int STAY = 2;
    public static final int CAMP_LOCATION = 3;
    public static final int HELLO = 4;
    public static final int WEATHER = 5;
    public static final int RENAME = 6;

    private static final int AFFECTION_VAGUE_THRESHOLD = 30;
    private static final int AFFECTION_EXACT_THRESHOLD = 70;

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
            if (sender == null || !(sender.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                return;
            }
            Entity entity = serverLevel.getEntity(this.entityId);
            if (!(entity instanceof FriendlyGirlInventory girl)
                    || !girl.isAlive()
                    || girl.isRemoved()
                    || sender.distanceToSqr(entity) > 256.0D) {
                return;
            }

            if (this.action == GIFT) {
                sender.displayClientMessage(Component.translatable(
                        "chat.enchantment_expansion.girl.gift_unavailable"), true);
                return;
            }
            if (!girl.isTamed() || !girl.isOwnedBy(sender)) {
                return;
            }
            switch (this.action) {
                case CAMP_LOCATION -> sendCampLocation(sender, serverLevel, entity, girl);
                case HELLO -> sendChatReply(sender, "chat.enchantment_expansion.girl.hello");
                case WEATHER -> sendChatReply(sender, "chat.enchantment_expansion.girl.weather");
                case FOLLOW -> girl.setOrderedToSit(false);
                case STAY -> girl.setOrderedToSit(true);
                case RENAME -> applyRename(entity);
                default -> {
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void sendCampLocation(ServerPlayer sender,
                                         net.minecraft.server.level.ServerLevel girlLevel,
                                         Entity girlEntity,
                                         FriendlyGirlInventory girl) {
        // SavedData 按维度存储；交互实体不在主世界时不读取主世界营地坐标。
        if (!girlLevel.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            sender.displayClientMessage(Component.translatable(
                    "chat.enchantment_expansion.girl.camp_location_wrong_dimension"), true);
            return;
        }

        // 先判断信任度，避免低好感度时因没有 SavedData 记录而泄露错误的“没有营地”提示。
        int affection = girl.getAffection();
        if (affection < AFFECTION_VAGUE_THRESHOLD) {
            String refusalKey = girl instanceof SurvivorGirlEntity
                    ? "chat.enchantment_expansion.girl.camp_location_refused_survivor"
                    : "chat.enchantment_expansion.girl.camp_location_refused_undead";
            sender.displayClientMessage(Component.translatable(refusalKey), true);
            return;
        }

        BlockPos camp = ZombieGirlCampSavedData.get(girlLevel)
                .findNearest(girlEntity.blockPosition());
        if (camp == null) {
            sender.displayClientMessage(Component.translatable(
                    "chat.enchantment_expansion.girl.camp_location_no_camp"), true);
            return;
        }
        if (affection >= AFFECTION_EXACT_THRESHOLD) {
            sender.displayClientMessage(Component.translatable(
                    "chat.enchantment_expansion.girl.camp_location_exact",
                    camp.getX(), camp.getY(), camp.getZ()), true);
            return;
        }

        int dx = camp.getX() - girlEntity.blockPosition().getX();
        int dz = camp.getZ() - girlEntity.blockPosition().getZ();
        Component direction = directionKey(dx, dz);
        sender.displayClientMessage(Component.translatable(
                "chat.enchantment_expansion.girl.camp_location_vague", direction), true);
    }

    private static void sendChatReply(ServerPlayer sender, String translationKey) {
        sender.displayClientMessage(Component.translatable(translationKey), true);
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

    private static Component directionKey(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return Component.translatable(dx >= 0
                    ? "chat.enchantment_expansion.girl.direction.east"
                    : "chat.enchantment_expansion.girl.direction.west");
        }
        return Component.translatable(dz >= 0
                ? "chat.enchantment_expansion.girl.direction.south"
                : "chat.enchantment_expansion.girl.direction.north");
    }
}
