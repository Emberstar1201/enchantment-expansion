package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

// ========================================================================
// 近战伤害 / AOE 系附魔互斥清单（共享工具类）
//
// 【设计目的】
//   为了平衡性，允许近战武器只同时携带一个“伤害成长 / 百分比真伤 / AOE / 固定附加伤害 / 吸血”系附魔，
//   避免多乘区 / 多段伤害叠加导致一把武器超模。
//
// 【清单】
//   - 拂晓            dawn                  （击杀成长：伤害/暴击/暴伤/攻距）
//   - 熵增            entropy               （百分比真伤 + AOE 扩散）
//   - 星火不灭        eternal_spark         （百分比真伤 + AOE 扩散）
//   - 兵长的回声      levis_echo            （百分比真伤）
//   - 蓄积            accumulate            （蓄力倍伤）
//   - 千破·青溟剑     qianpo_qingming_sword （无视护甲固定附加伤害）
//   - 嗜血            bloodthirst           （命中吸血）
//   - 云来剑法        yunlai_swordmanship   （攻击速度/距离/伤害倍率）
//   - 古·云来剑法     ancient_yunlai_swordmanship（更弱的攻击速度/距离/伤害倍率）
//
// 【用法】
//   在各附魔的 checkCompatibility 中调用 isExcluded(other)，配合各自现有互斥逻辑。
//   使用 ForgeRegistries.ENCHANTMENTS.getKey 比较注册名，避免 mappings 版本差异问题。
// ========================================================================
public final class DamageEnchantmentExclusion {

    // 所有近战伤害 / AOE / 成长系附魔的无命名空间路径集合（注册名末段）
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>();

    static {
        EXCLUDED_PATHS.add("dawn");
        EXCLUDED_PATHS.add("entropy");
        EXCLUDED_PATHS.add("eternal_spark");
        EXCLUDED_PATHS.add("levis_echo");
        EXCLUDED_PATHS.add("accumulate");
        EXCLUDED_PATHS.add("qianpo_qingming_sword");
        EXCLUDED_PATHS.add("bloodthirst");
        EXCLUDED_PATHS.add("yunlai_swordmanship");
        EXCLUDED_PATHS.add("ancient_yunlai_swordmanship");
    }

    private DamageEnchantmentExclusion() {
    }

    /**
     * 判断给定附魔是否属于“近战伤害 / AOE / 成长系”清单。
     * 仅检查本模组命名空间下的附魔，避免误伤其他模组的同名附魔。
     */
    public static boolean isExcluded(Enchantment other) {
        ResourceLocation rl = ForgeRegistries.ENCHANTMENTS.getKey(other);
        if (rl == null) {
            return false;
        }
        // 只对本模组命名空间下的附魔做互斥判定
        if (!"enchantment_expansion".equals(rl.getNamespace())) {
            return false;
        }
        return EXCLUDED_PATHS.contains(rl.getPath());
    }
}
