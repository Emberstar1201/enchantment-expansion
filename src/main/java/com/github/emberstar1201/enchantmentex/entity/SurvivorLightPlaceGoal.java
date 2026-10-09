package com.github.emberstar1201.enchantmentex.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// ========================================================================
// 【拟人行为：照明】走到哪亮到哪的小灯匠
//
//   只要她的 64 格随身背包里装着任何发光方块（火把 / 萤石 / 灯笼 /
//   菌光体 / 蛙明灯……判定标准是方块的光照亮度 > 0，不限种类），
//   站在暗处（光照 < 7）就会顺手放一个：
//   - 地下矿道天然是暗的——挖矿时自动插火把；
//   - 夜晚主世界地表光照只有 4，昏暗地区同样满足——夜里赶路也会沿途放光。
//
//   摆放位置：脚下方块优先（火把这类无碰撞光源直接插在脚下），
//   四个相邻格其次（萤石这类实心光源绝不放进自己或别人身体里，防窒息）。
//   放置位置的光照会立即超过阈值，天然形成间距，不会一秒插一根。
// ========================================================================
public class SurvivorLightPlaceGoal extends Goal {

    /** 两次放置之间的最小间隔（tick），防止极端情况下刷屏。 */
    private static final int PLACE_COOLDOWN_TICKS = 60;
    /** 暗处判定：当前所在位置最大光照低于该值才算「黑」。 */
    private static final int DARKNESS_THRESHOLD = 7;

    private final SurvivorGirlEntity mob;
    /** 下一次允许放置的最早 tick。 */
    private int nextPlaceTick;

    public SurvivorLightPlaceGoal(SurvivorGirlEntity mob) {
        this.mob = mob;
        // 不占用任何 flag：与赶路（跟随/闲逛/挖矿/农事）完全并行，
        // 她边走边插火把，不会被任何 Goal 抢占或反过来打断别人
    }

    @Override
    public boolean canUse() {
        if (this.mob.getWorkMode() != GirlWorkMode.LIGHTING
                || this.mob.level().isClientSide
                || this.mob.tickCount < this.nextPlaceTick
                || !this.mob.isAlive() || this.mob.isSleeping() || this.mob.isPassenger()
                || this.mob.isInWater() || this.mob.getTarget() != null) {
            return false;
        }
        // 暗处判定：地下矿道天然满足；夜晚地表（夜空光照约 4）同样满足
        if (this.mob.level().getMaxLocalRawBrightness(this.mob.blockPosition())
                >= DARKNESS_THRESHOLD) {
            return false;
        }
        // 背包里得有发光方块
        return !this.findLightBlock().isEmpty();
    }

    @Override
    public void start() {
        this.tryPlaceLight();
    }

    @Override
    public boolean canContinueToUse() {
        // 一次性动作：start() 放完就结束
        return false;
    }

    /**
     * 尝试在脚下或身旁放置一个发光方块。
     */
    private void tryPlaceLight() {
        Level level = this.mob.level();
        ItemStack lightStack = this.findLightBlock();
        if (lightStack.isEmpty() || !(lightStack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        BlockState lightState = blockItem.getBlock().defaultBlockState();
        BlockPos feet = this.mob.blockPosition();
        // 候选位：脚下方块优先（火把类直接插脚下），四邻其次（实心光源放旁边）
        BlockPos[] candidates = {feet, feet.north(), feet.south(), feet.west(), feet.east()};
        for (BlockPos pos : candidates) {
            if (!level.getBlockState(pos).isAir() || !lightState.canSurvive(level, pos)) {
                continue;
            }
            boolean solid = !lightState.getCollisionShape(level, pos).isEmpty();
            // 实心光源绝不放进自己站的格子（防把自己卡在方块里）
            if (solid && pos.equals(feet)) {
                continue;
            }
            // 实心光源也不砸进其他生物的身体里（防窒息）
            if (solid && this.isEntityInside(pos)) {
                continue;
            }
            level.setBlock(pos, lightState, 3);
            lightStack.shrink(1);
            level.playSound(null, pos, lightState.getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.nextPlaceTick = this.mob.tickCount + PLACE_COOLDOWN_TICKS;
            return;
        }
        // 没找到能放的位置（比如悬空 / 全被占），稍后再试
        this.nextPlaceTick = this.mob.tickCount + PLACE_COOLDOWN_TICKS;
    }

    /** 目标格子里是否站着其他生物（实心光源防窒息检查）。 */
    private boolean isEntityInside(BlockPos pos) {
        AABB box = new AABB(pos);
        for (LivingEntity entity : this.mob.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity != this.mob && entity.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在她的 64 格随身背包里找第一组发光方块。
     * 判定标准：方块的默认状态光照亮度 > 0——任何发光方块都充当照明材料。
     */
    private ItemStack findLightBlock() {
        SimpleContainer backpack = this.mob.getMeatInventory();
        for (int i = 0; i < backpack.getContainerSize(); i++) {
            ItemStack stack = backpack.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            if (blockItem.getBlock().defaultBlockState().getLightEmission() > 0) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
