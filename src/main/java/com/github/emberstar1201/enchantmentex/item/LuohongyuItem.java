package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.function.Consumer;

public class LuohongyuItem extends SwordItem {
    private static final String NBT_ENCH_INIT = "LuohongyuEnchInit";

    private static final Tier TIER = new Tier() {
        @Override public int getUses() { return 2031; }
        @Override public float getSpeed() { return 8.0F; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 18; }
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.NETHERITE_INGOT);
        }
    };

    public LuohongyuItem() {
        super(TIER, 19, -2.35F, new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide()) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (tag.getBoolean(NBT_ENCH_INIT)) {
            return;
        }

        ListTag enchantments = new ListTag();
        CompoundTag qianpo = new CompoundTag();
        qianpo.putString("id", "enchantment_expansion:qianpo_qingming_sword");
        qianpo.putShort("lvl", (short) 5);
        enchantments.add(qianpo);

        tag.put("Enchantments", enchantments);
        tag.putBoolean(NBT_ENCH_INIT, true);
        stack.setTag(tag);
    }

    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> enchantments = super.getAllEnchantments(stack);
        enchantments.put(ModEnchantments.QIANPO_QINGMING_SWORD.get(), 5);
        return enchantments;
    }

    // 洛红雨不损耗耐久，避免战斗和附魔效果使用时减少耐久值。
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount,
                                                    T entity, Consumer<T> onBroken) {
        return 0;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
