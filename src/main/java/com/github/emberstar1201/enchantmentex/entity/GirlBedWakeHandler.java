package com.github.emberstar1201.enchantmentex.entity;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 友好娘睡觉机制（二）：玩家右键她们正睡着的床时，先把人唤起。
 *
 * <p>覆盖三类共用睡眠行为的实体：{@link ZombieGirlEntity}（含其治愈后的
 * {@link SurvivorGirlEntity}）与 {@link DrownedGirlEntity}。</p>
 *
 * <p>原版 {@link BedBlock#use} 在床 OCCUPIED 时只会尝试把「村民」踢下床
 * （kickVillagerOutOfBed 只匹配 Villager 类），这些少女不是村民，
 * 不处理的话玩家点床只会收到「此床已占用」，也无法把出生点设在这张床上。</p>
 *
 * <p>Forge 的 RightClickBlock 事件在 BedBlock.use 之前触发：这里调用
 * stopSleeping()（同步清除两半床的 OCCUPIED 状态并把她移到床边），
 * 不取消事件——随后同一次右键的原版上床逻辑正常执行，玩家正常入睡并
 * 绑定出生点；她们自身的睡眠流程从不触碰玩家重生点数据。</p>
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GirlBedWakeHandler {

    private GirlBedWakeHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBed(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        BlockPos clicked = event.getPos();
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }
        // 右键床脚半块时先换算到床头
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? clicked : clicked.relative(state.getValue(BedBlock.FACING));

        // 找正躺在这张床（床头或床脚坐标匹配）上的友好娘
        List<LivingEntity> sleepers = level.getEntitiesOfClass(
                LivingEntity.class, new AABB(head),
                girl -> isSleepingGirl(girl)
                        && girl.getSleepingPos()
                                .map(pos -> pos.equals(head) || pos.equals(clicked))
                                .orElse(false));
        for (LivingEntity girl : sleepers) {
            // 清除 OCCUPIED、把她移动到床边安全站位；本次右键随后正常执行玩家入睡
            girl.stopSleeping();
        }
    }

    /** 共用睡眠系统的友好娘：丧尸娘（含幸存者少女子类）或溺尸娘。 */
    private static boolean isSleepingGirl(LivingEntity entity) {
        return entity.isSleeping()
                && (entity instanceof ZombieGirlEntity || entity instanceof DrownedGirlEntity);
    }
}
