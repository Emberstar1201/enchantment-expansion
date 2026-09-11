package com.github.emberstar1201.enchantmentex.recipe;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// ========================================================================
// 配方注册类
//
// 注册本模组的自定义 RecipeSerializer 到 ForgeRegistries.RECIPE_SERIALIZERS。
// 对应的配方定义文件位于 data/enchantment_expansion/recipes/ 下。
// ========================================================================
public class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, EnchantmentExpansion.MODID);

    // 生命之星：9 种不同的花（注册 ID：enchantment_expansion:life_star）
    public static final RegistryObject<RecipeSerializer<LifeStarRecipe>> LIFE_STAR =
            SERIALIZERS.register("life_star", LifeStarRecipe.Serializer::new);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
