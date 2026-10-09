package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.ModAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 少女系列成就（冒险页）的条件检测，每秒（20 tick）对每名在线玩家执行一次：
 * <ul>
 *   <li>「友善的亡灵？」：24 格内出现丧尸娘 / 溺尸娘（幸存者少女是人类，不算）；</li>
 *   <li>「令人心疼的少女」：24 格内有自己驯服的丧尸娘 / 溺尸娘；</li>
 *   <li>「美梦重现」：玩家入睡期间，16 格内有正在睡觉的幸存者少女。</li>
 * </ul>
 * 「驶向第2次生命」不走轮询，在 SurvivorGirlEntity.cureFrom 治愈成功时直接发放。
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GirlAdvancementHandler {

    /** 发现 / 驯服成就的检测半径（格）。 */
    private static final double FIND_RANGE = 24.0D;
    /** 共眠成就的检测半径（格）。 */
    private static final double SLEEP_RANGE = 16.0D;

    private GirlAdvancementHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();

        boolean needFound = !ModAdvancements.hasDone(player, ModAdvancements.FRIENDLY_UNDEAD);
        boolean needTamed = !ModAdvancements.hasDone(player, ModAdvancements.TAME_GIRL);
        if (needFound || needTamed) {
            AABB box = player.getBoundingBox().inflate(FIND_RANGE);
            for (ZombieGirlEntity girl : level.getEntitiesOfClass(ZombieGirlEntity.class, box)) {
                // 幸存者少女是人类形态，不触发「亡灵」发现成就
                if (girl instanceof SurvivorGirlEntity) {
                    continue;
                }
                if (needFound) {
                    ModAdvancements.award(player, ModAdvancements.FRIENDLY_UNDEAD);
                    needFound = false;
                }
                if (needTamed && girl.isOwnedBy(player)) {
                    ModAdvancements.award(player, ModAdvancements.TAME_GIRL);
                    needTamed = false;
                }
                if (!needFound && !needTamed) {
                    break;
                }
            }
            if (needFound || needTamed) {
                for (DrownedGirlEntity girl : level.getEntitiesOfClass(DrownedGirlEntity.class, box)) {
                    if (needFound) {
                        ModAdvancements.award(player, ModAdvancements.FRIENDLY_UNDEAD);
                        needFound = false;
                    }
                    if (needTamed && girl.isOwnedBy(player)) {
                        ModAdvancements.award(player, ModAdvancements.TAME_GIRL);
                        needTamed = false;
                    }
                    if (!needFound && !needTamed) {
                        break;
                    }
                }
            }
        }

        // 共眠成就：玩家睡着了、附近也有睡着的幸存者少女
        if (player.isSleeping() && !ModAdvancements.hasDone(player, ModAdvancements.SWEET_DREAMS)) {
            boolean sleepingTogether = !level.getEntitiesOfClass(SurvivorGirlEntity.class,
                    player.getBoundingBox().inflate(SLEEP_RANGE), LivingEntity::isSleeping).isEmpty();
            if (sleepingTogether) {
                ModAdvancements.award(player, ModAdvancements.SWEET_DREAMS);
            }
        }
    }
}
