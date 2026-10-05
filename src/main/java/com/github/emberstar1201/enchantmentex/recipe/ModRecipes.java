package com.github.emberstar1201.enchantmentex.recipe;

import net.minecraftforge.eventbus.api.IEventBus;

// ========================================================================
// 配方注册类
//
// 注册本模组的自定义 RecipeSerializer 到 ForgeRegistries.RECIPE_SERIALIZERS。
// 对应的配方定义文件位于 data/enchantment_expansion/recipes/ 下。
// ========================================================================
public class ModRecipes {

    public static void register(IEventBus eventBus) {
        // 生命之星使用唯一的原版 crafting_shaped JSON 配方，无需自定义序列化器。
    }
}
