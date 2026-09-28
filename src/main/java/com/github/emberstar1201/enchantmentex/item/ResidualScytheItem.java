package com.github.emberstar1201.enchantmentex.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

public class ResidualScytheItem extends SwordItem {
    private static final Tier TIER = new Tier() {
        @Override public int getUses() { return 4025; }
        @Override public float getSpeed() { return 8.0F; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 18; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.NETHERITE_INGOT); }
    };

    // 固定 UUID，用于实体攻击距离修饰符
    // 玩家默认实体攻击距离为 3.0，ADDITION +5.5 后总计 8.5
    private static final UUID REACH_MODIFIER_UUID =
            UUID.fromString("a3f5e2b1-7c84-4d61-9e0b-f2a1c8d73054");

    public ResidualScytheItem() {
        super(TIER, 10, -1.5F, new Item.Properties()
                .stacksTo(1)
                .durability(4025)
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        // 获取父类已有的属性修饰符（攻击伤害、攻击速度）
        Multimap<Attribute, AttributeModifier> modifiers = super.getDefaultAttributeModifiers(slot);
        if (slot == EquipmentSlot.MAINHAND) {
            // 拷贝原有属性，追加 forge:entity_reach 修饰符
            // 使用 ADDITION 操作，默认 3.0 + 5.5 = 8.5 格攻击距离
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder =
                    ImmutableMultimap.builder();
            builder.putAll(modifiers);
            builder.put(ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_MODIFIER_UUID, "Scythe reach",
                            5.5, AttributeModifier.Operation.ADDITION));
            return builder.build();
        }
        return modifiers;
    }
}
