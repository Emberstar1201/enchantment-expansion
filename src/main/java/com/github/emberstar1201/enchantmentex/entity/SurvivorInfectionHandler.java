package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 幸存者少女感染机制（一）：让原版敌对亡灵主动把她列为攻击目标。
 *
 * <p>实现方式：每当有实体加入服务端世界（自然生成 / 刷怪 / 加载区块），
 * 若它是「敌对 + 亡灵」（僵尸、尸壳、溺尸、骷髅、流浪者、凋灵骷髅、幻翼等），
 * 就向其 targetSelector 追加一个 {@link NearestAttackableTargetGoal}，
 * 使其在视野跟随范围内发现并追击幸存者少女。</p>
 *
 * <p>显式排除模组自己的丧尸娘、溺尸娘与幸存者少女：
 * 她们「自己的丧尸形态」不会把同类（包括变回人类的同伴）当作攻击目标。</p>
 *
 * <p>感染结算本身（命中后变回亡灵形态）见
 * {@link SurvivorGirlEntity#hurt}，任意难度必定感染。</p>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorInfectionHandler {

    private SurvivorInfectionHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        // 敌对亡灵：Enemy 覆盖全部 Monster（僵尸/骷髅家族）以及幻翼；
        // 再要求 MobType.UNDEAD，避免把苦力怕、蜘蛛等非亡灵敌对生物卷进来。
        if (!(mob instanceof Enemy) || mob.getMobType() != MobType.UNDEAD) {
            return;
        }
        // 丧尸娘 / 溺尸娘 / 幸存者少女互不攻击，不注入目标 AI
        if (mob instanceof ZombieGirlEntity || mob instanceof DrownedGirlEntity) {
            return;
        }
        // 优先级 3：低于「被伤害反击」与玩家目标，高于闲逛；
        // 玩家不在附近时亡灵就会主动追击幸存者少女。
        // mustSee=false 与僵尸索敌玩家的规则一致，可隔墙感知、靠近后视线追踪。
        mob.targetSelector.addGoal(3,
                new NearestAttackableTargetGoal<>(mob, SurvivorGirlEntity.class, false));
    }
}
