package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【终界之星】手持效果事件处理器
//
// 四大效果（当玩家手持 ModItems.END_STAR 时被动触发）：
//
//  1. 创造模式飞行
//     PlayerTickEvent 中设置 player.getAbilities().mayfly
//     创造模式玩家不受影响（原 abilities 就有 mayfly=true）
//     生存模式玩家双手任一持终界之星即可 mayfly=true
//     注意：切换物品时必须恢复 mayfly，否则玩家会卡住飞行状态
//
//  2. 80% 伤害减免（可配置）
//     LivingHurtEvent 中读取配置 END_STAR_DAMAGE_REDUCTION_PERCENT
//     对任意类型伤害 event.setAmount(amount * (1 - reduction))
//     覆盖所有伤害类型，包括监守者音波伤害 SONIC_BOOM
//
//  3. 经验等级伤害加成
//     LivingHurtEvent 中检测玩家（作为伤害来源）是否手持终界之星
//     根据玩家经验等级计算加成：每 10 级 +100% 伤害，上限 +1000%（100 级）
//     公式：bonusPercent = floor(level / 10) * 100，钳制到 1000
//     最终伤害 = 原伤害 × (1 + bonusPercent / 100)
//     对近战/远程/魔法等所有玩家造成的攻击生效
//
//  4. 紫色附魔字符粒子（手持时，仅客户端视觉效果）
//     PlayerTickEvent 每 4 tick 生成 ParticleTypes.ENCHANT 粒子
//     在玩家身体周围生成向上飘动的字符
//
//  5. 丢弃特效（地上的 ItemEntity，仅客户端视觉效果）
//     PlayerTickEvent 客户端侧扫描玩家附近 32 格内的 ItemEntity
//     若物品为终界之星，则在其周围生成 ENCHANT 粒子
//     使玩家更容易发现被丢弃的终界之星
//     【关键】粒子逻辑全部在客户端处理，避免服务端 addParticle 无效的问题
//
//  所有效果支持 Config 中的开关与数值配置。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class EndStarHandler {

    // 调试日志器：用于输出减伤/加成的触发情况，便于排查问题
    private static final Logger LOGGER = LogUtils.getLogger();

    // PersistentData 键：记录玩家上一 tick 是否处于"手持终界之星"状态
    // 用于检测"手持/放下"的状态切换，避免每 tick 重复设置 abilities
    private static final String LAST_HOLDING_ENDSTAR_KEY = "end_star_holding_last";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 每 tick 都要执行（abilities 必须持续维持）
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;

        // 判断玩家是否正在手持终界之星（主手 或 副手 均可）
        boolean isHoldingEndStar = isHoldingEndStar(player);

        // ================================================================
        // 效果 1：创造模式飞行（可通过配置开关）
        // ================================================================
        if (Config.enableEndStarFlight) {
            // 上一 tick 状态（用于检测边缘切换）
            boolean wasHolding = player.getPersistentData()
                    .getBoolean(LAST_HOLDING_ENDSTAR_KEY);

            // 【关键约束】创造模式下的玩家原本就具有 mayfly=true，
            //  但不能在放下终界之星时把创造模式的飞行关了！
            //  → 仅在"生存/冒险模式"的玩家中控制 mayfly 开关
            if (!player.isCreative() && !player.isSpectator()) {
                if (isHoldingEndStar) {
                    // 手持：打开 mayfly
                    if (!player.getAbilities().mayfly) {
                        player.getAbilities().mayfly = true;
                        player.onUpdateAbilities();  // 同步到客户端
                    }
                } else {
                    // 放下：关闭 mayfly，并立即禁止飞行（已经在飞的强制落地）
                    if (player.getAbilities().mayfly) {
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;   // 立即退出飞行
                        player.onUpdateAbilities();
                    }
                }
            }

            // 更新上一 tick 状态
            if (isHoldingEndStar != wasHolding) {
                player.getPersistentData()
                        .putBoolean(LAST_HOLDING_ENDSTAR_KEY, isHoldingEndStar);
            }
        }

        // ================================================================
        // 效果 4 + 5：紫色附魔字符粒子（仅客户端生成）
        //   - 手持时：玩家身体周围生成 ENCHANT 粒子
        //   - 丢弃时：玩家附近 32 格内的终界之星 ItemEntity 周围生成粒子
        // 频率：每 4 tick 生成一次（减少粒子过载）
        // 【关键】所有粒子逻辑在客户端处理：
        //   Level.addParticle() 只在客户端生效，服务端调用无效
        // ================================================================
        if (player.level().isClientSide() && player.tickCount % 4 == 0) {
            // 效果 4：手持时玩家周围粒子
            if (isHoldingEndStar) {
                spawnEnchantParticlesAroundEntity(player.getX(),
                        player.getY() + player.getBbHeight() * 0.5,
                        player.getZ(),
                        player.level());
            }

            // 效果 5：附近丢弃的终界之星粒子
            // 在玩家周围 32 格范围内搜索 ItemEntity（性能友好）
            AABB searchBox = player.getBoundingBox().inflate(32);
            List<ItemEntity> nearbyItems = player.level().getEntitiesOfClass(
                    ItemEntity.class, searchBox);

            for (ItemEntity itemEntity : nearbyItems) {
                if (!itemEntity.getItem().is(ModItems.END_STAR.get())) {
                    continue;
                }
                // 在终界之星 ItemEntity 周围生成粒子
                spawnEnchantParticlesAroundEntity(
                        itemEntity.getX(),
                        itemEntity.getY() + itemEntity.getBbHeight() * 0.5,
                        itemEntity.getZ(),
                        player.level());
            }
        }
    }

    // ========================================================================
    // 效果 2 + 3：伤害减免 + 伤害加成（LivingHurtEvent）
    //
    // 一个事件同时处理两个方向：
    //   - 玩家作为 victim（受伤）：手持终界之星时减免 80% 所有伤害（含音波）
    //   - 玩家作为 attacker（造成伤害）：手持终界之星时根据经验等级加成
    //
    // 【关键修复】方向 B 增加 attacker != victim 检查
    //   原代码未排除"玩家自伤"情况（如摔落/药水/火焰等 source.getEntity() 返回玩家自己）
    //   会导致方向 B（加成）覆盖方向 A（减伤），使减伤效果被抵消甚至伤害反向增加
    //
    // 【优先级】使用 HIGH 确保在大多数其他模组之前执行减伤
    // ========================================================================
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = (source != null && source.getEntity() instanceof Player a) ? a : null;

        // --------------------------------------------------------------
        // 方向 A：玩家受伤（伤害减免）
        // --------------------------------------------------------------
        if (victim != null
                && Config.enableEndStarDamageReduction
                && isHoldingEndStar(victim)) {

            // 读取减伤百分比（0.0~1.0），默认 0.8（80%）
            double reduction = Config.endStarDamageReductionPercent;
            reduction = Math.min(reduction, 1);
            if (reduction >= 0.0) {
                // 减伤为 0，不处理
                float oldAmount = event.getAmount();
                float newAmount = oldAmount * (1.0f - (float) reduction);

                LOGGER.debug("[终界之星] 减伤触发：玩家={}, 原伤害={}, 减免比例={}, 最终伤害={}",
                        victim.getName().getString(), oldAmount, reduction, newAmount);

                event.setAmount(newAmount);
            }
        }

        // --------------------------------------------------------------
        // 方向 B：玩家造成伤害（经验等级伤害加成）
        // 【关键修复】排除 attacker == victim 的情况
        //   避免玩家自伤（摔落/药水等 source 实体为玩家自己）时加成覆盖减伤
        // --------------------------------------------------------------
        if (attacker == null
                || victim == attacker  // 排除自伤
                || !Config.enableEndStarDamageBonus
                || !isHoldingEndStar(attacker)) {
            return;
        }

        // 计算经验等级加成百分比：
        //   每 10 级提供 100% 加成（floor(level/10) * 100）
        //   例：level=0~9 → 0%, level=10~19 → 100%, level=100+ → 1000%
        int bonusPercent = Math.floorDiv(attacker.experienceLevel, 10) * 100;

        // 钳制上限：从配置读取（默认 1000%）
        int maxBonus = Config.endStarMaxBonusPercent;
        if (bonusPercent > maxBonus) {
            bonusPercent = maxBonus;
        }
        if (bonusPercent <= 0) {
            return;  // 0% 加成时直接跳过，避免不必要的浮点运算
        }

        // 最终伤害 = 原伤害 × (1 + 加成百分比/100)
        // 例：原伤害=10, bonusPercent=100 → 10 × 2 = 20
        //     原伤害=10, bonusPercent=1000 → 10 × 11 = 110
        float multiplier = 1.0f + (bonusPercent / 100.0f);
        float oldAmount = event.getAmount();

        LOGGER.debug("[终界之星] 伤害加成触发：玩家={}, 经验等级={}, 加成={}%, 原伤害={}, 最终伤害={}",
                attacker.getName().getString(), attacker.experienceLevel,
                bonusPercent, oldAmount, oldAmount * multiplier);

        event.setAmount(oldAmount * multiplier);
    }

    // ========================================================================
    // 工具方法：检查玩家是否在手持（主手或副手）终界之星
    // ========================================================================
    private static boolean isHoldingEndStar(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return mainHand.is(ModItems.END_STAR.get()) || offHand.is(ModItems.END_STAR.get());
    }

    // ========================================================================
    // 工具方法：在指定坐标周围生成附魔台字符粒子（紫色向上飘动）
    // 粒子类型：ParticleTypes.ENCHANT
    // 每次调用生成 4 个粒子，围绕中心 0.9 格半径的圆上随机分布
    // 同时支持客户端（玩家手持）和服务端（地上 ItemEntity）调用
    // ========================================================================
    private static void spawnEnchantParticlesAroundEntity(
            double centerX, double centerY, double centerZ, Level level) {
        float radius = 0.9f;
        for (int i = 0; i < 4; i++) {
            // 在中心周围 0.9 格半径的圆上随机取点
            double angle = Math.random() * 2 * Math.PI;
            double ox = centerX + Math.cos(angle) * radius;
            double oz = centerZ + Math.sin(angle) * radius;
            // ParticleTypes.ENCHANT 默认向上飘（参数为速度的 deltaY）
            // 第四个参数 count=1 后是 deltax/y/z/speed
            level.addParticle(
                    ParticleTypes.ENCHANT,
                    ox, centerY + (Math.random() - 0.5) * 0.5, oz,
                    (Math.random() - 0.5) * 0.02,   // deltax：小幅水平乱飘
                    0.08 + Math.random() * 0.04,    // deltay：向上飘动速度
                    (Math.random() - 0.5) * 0.02    // deltaz：小幅水平乱飘
            );
        }
    }
}
