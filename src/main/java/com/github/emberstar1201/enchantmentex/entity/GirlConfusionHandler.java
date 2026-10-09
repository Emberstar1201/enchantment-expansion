package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「意识暂时丧失」彩蛋的铁傀儡保护。
 *
 * <p>丧尸娘 / 溺尸娘是 {@code Monster} 子类，铁傀儡有三条路径会把她们
 * 设为攻击目标：</p>
 * <ol>
 *     <li>主动搜索附近怪物（NearestAttackableTargetGoal）；</li>
 *     <li>保卫村庄：村民因恐慌把接近的怪物记为「伤害来源」；</li>
 *     <li>被攻击后的报复目标（HurtByTargetGoal）。</li>
 * </ol>
 * <p>彩蛋期间少女只追村民、不造成伤害，铁傀儡不应介入。这里统一拦截
 * 铁傀儡一切新目标变更：只要新目标是处于意识丧失状态的少女，就把目标
 * 改回 null。彩蛋开始前已经存在的旧目标由 {@link GirlConfusionGoal}
 * 主动清除。</p>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID)
public class GirlConfusionHandler {

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof IronGolem
                && GirlConfusionGoal.isConfused(event.getNewTarget())) {
            event.setNewTarget(null);
        }
    }
}
