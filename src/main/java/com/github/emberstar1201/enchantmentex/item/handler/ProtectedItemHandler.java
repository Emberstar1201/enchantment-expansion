package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

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
//      - 每 4 tick（0.2 秒）扫描一次玩家周围的掉落物
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

        // ============================================================
        // 【性能关键】必须以“玩家”为中心做局部扫描！
        //
        // 历史写法是构造一个覆盖整个维度的 AABB(±3.0E7)，再交给
        // Level#getEntitiesOfClass。看似只查一次很省事，实际代价是灾难性的：
        //   EntitySectionStorage#forEachAccessibleNonEmptySection 内部是
        //   “按 x 轴逐 section 列扫描”的循环：
        //       for (int x = posToSectionCoord(minX); x <= posToSectionCoord(maxX); x++) {
        //           sectionIds.subSet(...).iterator();   // 每列都新建一个迭代器
        //       }
        //   ±3.0E7 换算成 section 坐标是 ±1875000，
        //   即单次调用就要循环 ~375 万次、并新建 ~375 万个 subSet/iterator。
        //   本 Handler 每 4 tick × 每个已加载维度（主世界/下界/末地）各跑一次，
        //   折算下来每秒上千万次循环 + 上千万次临时对象分配 →
        //   GC 风暴把服务端 tick 直接击穿：
        //     ① 铁砧/容器交互的数据包（ServerboundContainerClick 等）
        //        得不到及时处理 → 表现为“铁砧打不开 / 无法附魔”；
        //     ② 退出存档时的保存流程同样跑在服务端主线程 →
        //        表现为“卡在正在保存世界”。
        //
        // 正确做法：仙人掌/火焰伤害只可能发生在“已加载的区块”里，
        // 而服务端已加载区块必然以玩家为中心（模拟距离内才会 tick）。
        // 所以只需对每个玩家取一个小范围 AABB 扫描即可，
        // 既覆盖全部真正会受伤的掉落物，又把单次循环从 375 万降到几十。
        // ============================================================
        List<? extends Player> players = level.players();
        if (players.isEmpty()) return;                    // 无人时直接跳过，零开销

        for (Player player : players) {
            AABB scanBox = player.getBoundingBox().inflate(SCAN_RADIUS);
            List<ItemEntity> protectedItems = level.getEntitiesOfClass(
                    ItemEntity.class,
                    scanBox,
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
                    // 计算弹出方向（方块中心 → 物品的反方向）
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

    // ================================================================
    // 扫描半径：取 160 格。
    // 服务端 simulation-distance 默认 10 个区块 = 160 格，只有这个范围内的
    // 实体才会被 tick（才会真正吃到仙人掌/火焰伤害），因此 160 格已能覆盖
    // 全部需要保护的掉落物；再大只是白白增加开销。
    // ================================================================
    private static final double SCAN_RADIUS = 160.0D;
}
