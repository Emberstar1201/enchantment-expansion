package com.github.emberstar1201.enchantmentex.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// ========================================================================
// 【苦力怕 · 蓄力时长】Creeper.maxSwell Accessor
//
// 为什么必须用 Mixin：
//   Creeper 的蓄力时长字段 `private int maxSwell`（默认 30）没有任何 setter，
//   1.20.1 也没有对应的 Forge 事件或属性可以修改它，
//   因此只能通过 @Accessor 直接把写入口暴露出来。
//
// 注意：该字段没有网络同步，所以调用方（MobBuffHandler）必须双端都设置，
//   否则客户端会按默认 30 tick 提前闪白并播放引爆音。
// ========================================================================
@Mixin(Creeper.class)
public interface CreeperSwellAccessor {

    /** 设置引爆所需蓄力 tick（1 秒 = 20 tick） */
    @Accessor("maxSwell")
    void setMaxSwell(int maxSwell);
}
