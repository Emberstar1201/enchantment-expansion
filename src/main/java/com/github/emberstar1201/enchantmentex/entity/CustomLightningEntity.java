package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;

// ========================================================================
// 【自定义闪电实体】CustomLightningEntity
//
// 【用途】修复「人权剑 · 人的意志」技能引雷时误伤/误毁环境的副作用。
//
// 【问题】原版闪电（LightningBolt）命中时会：
//   1. 销毁周围 1 格内的掉落物（ItemEntity）→ 玩家损失战利品
//   2. 对半径 6 格内所有实体施加 thunderHit 闪电伤害 → 误伤其他玩家/动物
//   3. 点燃周围可燃方块（allowFires）→ 烧毁建筑/农田
//
// 【解决】本实体在构造时调用原版 setVisualOnly(true)：
//   这是原版内置的"纯视觉闪电"模式（命令 /summon lightning_bolt
//   {visualOnly:1b} 就是它），效果如下：
//   ✔ 保留完整闪电视觉（纹理、雷电分支、雷声音效、屏幕闪烁）
//   ✔ 不销毁掉落物
//   ✔ 不伤害任何实体（玩家/动物/怪物均不受 thunderHit）— 伤害由
//      调用方（人权剑 handler）手动对指定怪物施加，精确控制目标
//   ✔ 不点燃方块、不破坏地形
//
// 【伤害来源】视觉闪电本身无伤害；「人的意志」的实际闪电伤害由
//   SwordOfTheFreeWillHandler 在生成闪电后手动对怪物造成魔法伤害，
//   因此本实体只负责"看起来像闪电"，不承载伤害逻辑。
// ========================================================================
public class CustomLightningEntity extends LightningBolt {

    public CustomLightningEntity(EntityType<? extends CustomLightningEntity> type, Level level) {
        super(type, level);
        // 关键一行：切换到纯视觉模式，取消掉落物销毁/误伤/点火
        this.setVisualOnly(true);
    }

    // ========================================================================
    // 静态工厂：在指定位置生成一道自定义闪电（纯视觉）
    // ========================================================================
    public static void spawn(ServerLevel level, double x, double y, double z) {
        CustomLightningEntity lightning =
                ModEntities.CUSTOM_LIGHTNING.get().create(level);
        if (lightning != null) {
            lightning.moveTo(x, y, z);
            level.addFreshEntity(lightning);
        }
    }
}