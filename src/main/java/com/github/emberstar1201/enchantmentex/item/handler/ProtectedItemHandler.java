package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【宝物安全守护】Handler
//
// 目标：保护终界之星、海洋之星等稀有宝物不因“仙人掌/火焰/岩浆”等环境
// 因素被销毁（作为 ItemEntity 掉落在地上时）。
//
// 机制：
//   1. 防 火 / 防岩浆：
//      - 已经在 EndStarItem / OceanStarItem 的 Properties.fireResistant()
//        和类层面 isFireResistant() 重写做了双保险。
//      - 这里作为第三层保险：每 tick 如果检测到在燃烧立刻 clearFire()。
//   2. 防仙人掌：
//      - 每 4 tick（0.2 秒）扫描一次世界中的 ItemEntity
//      - 如果物品是终界之星 / 海洋之星，且当前/下方是仙人掌方块：
//        给予“向上+向外”的速度把物品弹离仙人掌，防止卡在仙人掌内。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProtectedItemHandler {

    // ================================================================
    // 节流：LevelTick 每 tick 都触发，但我们只在每 4 tick（0.2s）执行 1 次
    // ================================================================
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;           // 仅服务端（所有掉落物位置权威）
        long time = event.level.getGameTime();
        if (time % 4L != 0L) return;                     // 0.2s 节流，省性能

        Level level = event.level;

        // 世界边界 AABB 不可用（不同子版本 API 差异），
        // 直接使用“整个主世界范围”的超大 AABB，让 getEntitiesOfClass
        // 在服务端侧遍历所有掉落物；配合 0.2s 节流性能开销可忽略。
        AABB hugeBox = new AABB(
                -3.0E7, -2048.0, -3.0E7,
                3.0E7,  2048.0,  3.0E7
        );
        var protectedItems = level.getEntitiesOfClass(
                ItemEntity.class,
                hugeBox,
                ie -> {
                    ItemStack s = ie.getItem();
                    return !s.isEmpty()
                            && (s.is(ModItems.END_STAR.get())
                            || s.is(ModItems.OCEAN_STAR.get()));
                }
        );

        for (ItemEntity itemEntity : protectedItems) {
            // ========== 第三层：防燃烧兜底 ==========
            if (itemEntity.isOnFire() || itemEntity.fireImmune()) {
                // 直接清零火焰 tick，防止被火/岩浆销毁
                itemEntity.clearFire();
            }

            // ========== 防仙人掌：检测并弹开 ==========
            BlockPos pos = itemEntity.blockPosition();
            BlockState feetState = level.getBlockState(pos);
            BlockState belowState = level.getBlockState(pos.below());
            boolean onCactus = feetState.is(Blocks.CACTUS) || belowState.is(Blocks.CACTUS);

            if (onCactus) {
                // 计算弹出方向（玩家中心 → 远离仙人掌中心的反方向）
                Vec3 center = Vec3.atCenterOf(pos);
                double dx = itemEntity.getX() - center.x;
                double dz = itemEntity.getZ() - center.z;
                double distSq = dx * dx + dz * dz;
                if (distSq < 0.0001) {
                    // 正好在仙人掌中心上 → 随机水平方向弹出
                    double ang = level.random.nextDouble() * Math.PI * 2;
                    dx = Math.cos(ang);
                    dz = Math.sin(ang);
                } else {
                    double len = Math.sqrt(distSq);
                    dx /= len;
                    dz /= len;
                }
                // 上抛 0.28 + 水平弹出 0.18：足够把物品从仙人掌上顶开
                itemEntity.setDeltaMovement(new Vec3(dx * 0.18, 0.28, dz * 0.18));
                itemEntity.hurtMarked = true;    // 通知服务端位置同步更新
            }
        }
    }
}
