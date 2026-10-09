package com.github.emberstar1201.enchantmentex.item;

import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// ========================================================================
// 【无名诗笺】（Unsigned Verses）
//   宝箱战利品（见 VersesLootHandler）。进入玩家背包的瞬间自动消失，
//   并发放进度 story/found_verses——它是终界之书「剧情叙事」分栏的
//   解锁钥匙；首次解锁时聊天框会弹出提示信息。
// ========================================================================
public class UnsignedVersesItem extends Item {
    /** 解锁「剧情叙事」分栏的进度 ID。 */
    private static final ResourceLocation FOUND_VERSES_ADV =
            new ResourceLocation("enchantment_expansion", "story/found_verses");

    public UnsignedVersesItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // 只处理「进入玩家背包」：主手/副手/背包/盔甲槽的 inventoryTick 都算；
        // 放进箱子等外部容器不会触发，可以安全转移存放
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        stack.shrink(1);
        Advancement advancement = level.getServer().getAdvancements().getAdvancement(FOUND_VERSES_ADV);
        if (advancement == null) {
            return;
        }
        // 已解锁过就静静消失，不再重复弹消息
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        player.getAdvancements().award(advancement, "granted");
        player.displayClientMessage(Component.translatable(
                "chat.enchantment_expansion.verses_unlocked"), false);
    }
}
