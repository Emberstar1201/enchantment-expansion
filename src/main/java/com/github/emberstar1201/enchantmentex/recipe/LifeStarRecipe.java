package com.github.emberstar1201.enchantmentex.recipe;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

// ========================================================================
// 【生命之星】合成配方
//
// 规则：3×3 工作台，9 格全部放花，且 9 朵花必须互不相同（按物品 ID 区分）。
//   例：红郁金香与白郁金香算两种不同的花，可以同时放入。
//       两朵红郁金香算同一种花，不可同时放入。
//
// 为什么不用原版 JSON 配方？
//   crafting_shapeless 允许 9 朵完全相同的花，crafting_shaped 的 9 格也可以
//   指向同一个 key，都无法表达"9 种不同的花"，所以这里自定义 Recipe。
//
// 实现 CraftingRecipe（而不是裸 Recipe）的原因：
//   CraftingRecipe#getType() 默认返回 RecipeType.CRAFTING，
//   这样帕秋莉手册的 patchouli:crafting 页面（PageDoubleRecipeRegistry）
//   才能通过 recipe.getType() == RecipeType.CRAFTING 的过滤并正常显示配方图。
// ========================================================================
public class LifeStarRecipe implements CraftingRecipe {

    /** 工作台合成格数量（3×3） */
    public static final int GRID_SIZE = 9;

    /** 参与合成的"花"：由 data/enchantment_expansion/tags/items/life_star_flowers.json 定义 */
    public static final TagKey<Item> FLOWERS = TagKey.create(
            Registries.ITEM,
            new ResourceLocation(EnchantmentExpansion.MODID, "life_star_flowers"));

    private final ResourceLocation id;
    /** 仅用于展示（手册配方图 / 配方书），匹配逻辑在 matches() 中单独实现 */
    private final NonNullList<Ingredient> ingredients;

    public LifeStarRecipe(ResourceLocation id) {
        this.id = id;
        this.ingredients = NonNullList.withSize(GRID_SIZE, Ingredient.of(FLOWERS));
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        // 必须是 3×3 的工作台（玩家背包内的 2×2 合成格大小为 4）
        if (container.getContainerSize() != GRID_SIZE) {
            return false;
        }

        Set<Item> distinct = new HashSet<>();
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = container.getItem(i);
            // 空格、非花、重复的花 → 不成立
            if (stack.isEmpty() || !stack.is(FLOWERS) || !distinct.add(stack.getItem())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        return new ItemStack(ModItems.LIFE_STAR.get());
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return new ItemStack(ModItems.LIFE_STAR.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    // 特殊配方：不参与配方书（配方书无法表达"9 种不同"的约束）
    @Override
    public boolean isSpecial() {
        return true;
    }

    // 展示用料是标签，不能因此被判定为"配方残缺"而在数据包重载时被丢弃
    @Override
    public boolean isIncomplete() {
        return false;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.LIFE_STAR.get();
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    // ========================================================================
    // 序列化器：配方本身没有任何可变字段，JSON 里只需写 type
    // ========================================================================
    public static class Serializer implements RecipeSerializer<LifeStarRecipe> {

        @Override
        public LifeStarRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new LifeStarRecipe(id);
        }

        @Override
        public @Nullable LifeStarRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new LifeStarRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, LifeStarRecipe recipe) {
            // 无字段需要同步
        }
    }
}
