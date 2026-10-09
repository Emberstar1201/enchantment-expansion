package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.entity.DrownedGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.ModEntities;
import com.github.emberstar1201.enchantmentex.entity.SurvivorGirlEntity;
import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 无主的星尘：用于收容和释放已驯服的丧尸娘/溺尸娘。
 * 空星尘没有 CapturedData，收入后把完整实体 NBT 写入物品；有数据时右键地面释放。
 */
public class UnownedStardustItem extends Item {
    private static final String CAPTURED_DATA = "CapturedData";
    private static final String CAPTURED_TYPE = "CapturedType";

    public UnownedStardustItem(Properties properties) {
        super(properties);
    }

    public static boolean isCaptured(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(CAPTURED_DATA);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ZombieGirlEntity)
                && !(target instanceof DrownedGirlEntity)) {
            return InteractionResult.PASS;
        }
        if (isCaptured(stack)) {
            return InteractionResult.PASS;
        }
        // 未驯服的野生个体：完全无效且不弹任何提示（魂符只认已驯服的伙伴）
        boolean tamed;
        boolean owned;
        if (target instanceof ZombieGirlEntity zombieGirl) {
            tamed = zombieGirl.isTamed();
            owned = zombieGirl.isOwnedBy(player);
        } else {
            DrownedGirlEntity drownedGirl = (DrownedGirlEntity) target;
            tamed = drownedGirl.isTamed();
            owned = drownedGirl.isOwnedBy(player);
        }
        if (!tamed) {
            return InteractionResult.PASS;
        }
        if (!owned) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "item.enchantment_expansion.unowned_stardust.not_owner"), true);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        // 冷却中：防止连点收入
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (!player.level().isClientSide) {
            CompoundTag entityData = new CompoundTag();
            target.saveWithoutId(entityData);
            // 保存收容时刻的 tickCount，并把聊天状态改存为相对时间。
            // 这样无论星尘收容多久，放出后都只会继续消耗收入时剩余的时间。
            entityData.putInt("CapturedTickCount", target.tickCount);
            entityData.putBoolean("CapturedChatState", true);
            entityData.putLong("ChatHurtCooldownUntil",
                    entityData.getLong("ChatHurtCooldownUntil") - target.tickCount);
            entityData.putLong("ChatNextIdleTick",
                    entityData.getLong("ChatNextIdleTick") - target.tickCount);
            entityData.putLong("ChatLastMealTick",
                    target.tickCount - entityData.getLong("ChatLastMealTick"));
            entityData.putLong("ChatVillageCooldownUntil",
                    entityData.getLong("ChatVillageCooldownUntil") - target.tickCount);
            entityData.putLong("ChatCombatCooldownUntil",
                    entityData.getLong("ChatCombatCooldownUntil") - target.tickCount);
            entityData.putLong("ChatLastTimeTick",
                    target.tickCount - entityData.getLong("ChatLastTimeTick"));
            entityData.putLong("ChatLastCorruptionTick",
                    target.tickCount - entityData.getLong("ChatLastCorruptionTick"));
            entityData.putLong("ChatFreezeUntilTick",
                    entityData.getLong("ChatFreezeUntilTick") - target.tickCount);
            entityData.remove("Pos");
            entityData.remove("Motion");
            entityData.remove("Rotation");
            entityData.remove("UUID");
            entityData.remove("Passengers");
            // 幸存者少女继承自丧尸娘，必须先于 ZombieGirlEntity 判断，
            // 否则收容后放出会退回亡灵形态。
            String type;
            if (target instanceof SurvivorGirlEntity) {
                type = "survivor_girl";
            } else if (target instanceof ZombieGirlEntity) {
                type = "zombie_girl";
            } else {
                type = "drowned_girl";
            }
            // 关键：整组替换手中物品，而不是原地改 NBT。
            // 原地修改的 NBT 在创造模式等客户端权威背包同步下会丢失，
            // 导致星尘看似仍为空（无光效、放不出、还能继续收入并覆盖前一个）。
            ItemStack filled = new ItemStack(this);
            CompoundTag itemData = filled.getOrCreateTag();
            itemData.put(CAPTURED_DATA, entityData);
            itemData.putString(CAPTURED_TYPE, type);
            target.discard();
            player.setItemInHand(hand, filled);
            player.getCooldowns().addCooldown(this, 20); // 1 秒冷却
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, target.getSoundSource(), 0.8F, 1.4F);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!isCaptured(stack)) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        // 冷却中：防止连点放出
        if (player != null && player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.CONSUME;
        }
        CompoundTag itemData = stack.getTag();
        String type = itemData.getString(CAPTURED_TYPE);
        Entity entity = switch (type) {
            case "zombie_girl" -> ModEntities.ZOMBIE_GIRL.get().create(level);
            case "drowned_girl" -> ModEntities.DROWNED_GIRL.get().create(level);
            case "survivor_girl" -> ModEntities.SURVIVOR_GIRL.get().create(level);
            default -> null;
        };
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        entity.load(itemData.getCompound(CAPTURED_DATA));
        float yRot = player == null ? 0.0F : player.getYRot();
        entity.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(),
                spawnPos.getZ() + 0.5D, yRot, 0.0F);
        level.addFreshEntity(entity);
        // 同样整组替换回空星尘，避免原地删 NBT 在客户端权威同步下数据残留
        if (player != null) {
            player.setItemInHand(context.getHand(), new ItemStack(this));
            player.getCooldowns().addCooldown(this, 20); // 1 秒冷却
        } else {
            stack.shrink(1);
        }
        level.playSound(null, spawnPos, SoundEvents.ENDERMAN_TELEPORT,
                entity.getSoundSource(), 0.8F, 0.8F);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        return isCaptured(stack)
                ? Component.translatable("item.enchantment_expansion.owned_stardust")
                : Component.translatable("item.enchantment_expansion.unowned_stardust");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isCaptured(stack);
    }
}
