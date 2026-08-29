package com.github.emberstar1201.enchantmentex.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

// ========================================================================
// 【友方过滤】统一 AOE / 范围类效果的友军保护工具
//
// 背景：
//   多个附魔具有 AOE（范围溅射/扩散/弹射/穿透）效果，此前各自的
//   目标过滤条件不统一，部分效果会误伤村民、驯服动物或其他玩家。
//
// 【设计原则】
//   1. 软兼容：不直接引用任何外部模组类，通过命名空间识别第三方
//      友方实体（如车万女仆 touhou_little_maid），未安装该模组时
//      无任何副作用，也不会崩溃。
//   2. 单一出口：所有 AOE 类伤害统一调用 isFriendly 判定，
//      后续需要新增第三方友方实体时只需修改这里。
// ========================================================================
public final class AllyFilter {

    // 车万女仆（Touhou Little Maid）模组命名空间
    // 女仆属于玩家的"伙伴"实体，AOE 不应波及；
    // 采用命名空间判断可覆盖女仆及其衍生实体，且无需硬依赖该模组。
    private static final String NAMESPACE_TOUHOU_MAID = "touhou_little_maid";

    private AllyFilter() {
        // 工具类，禁止实例化
    }

    /**
     * 判断实体是否属于"不应被 AOE 波及的友方/中立阵营"：
     *
     * 1. 所有玩家 —— AOE 类效果一律不产生 PvP 伤害；
     * 2. 有主人的驯服生物（狼、猫、鹦鹉、马、羊驼等，实现 OwnableEntity）——
     *    无论主人是谁，"有主"即视为玩家伙伴；
     * 3. 村民与流浪商人（AbstractVillager 及其子类）；
     * 4. 原版友好傀儡（铁傀儡、雪傀儡）；
     * 5. 车万女仆模组（touhou_little_maid 命名空间）的全部实体。
     */
    public static boolean isFriendly(LivingEntity entity) {
        // 1. 玩家（含攻击者本人，由调用方按需另行排除）
        if (entity instanceof Player) {
            return true;
        }

        // 2. 有主人的驯服生物
        if (entity instanceof OwnableEntity ownable
                && ownable.getOwnerUUID() != null) {
            return true;
        }

        // 3. 村民 / 流浪商人
        if (entity instanceof AbstractVillager) {
            return true;
        }

        // 4. 原版友好傀儡
        if (entity instanceof IronGolem || entity instanceof SnowGolem) {
            return true;
        }

        // 5. 第三方友方实体（车万女仆等）—— 命名空间软兼容
        var typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return typeId != null
                && NAMESPACE_TOUHOU_MAID.equals(typeId.getNamespace());
    }
}
