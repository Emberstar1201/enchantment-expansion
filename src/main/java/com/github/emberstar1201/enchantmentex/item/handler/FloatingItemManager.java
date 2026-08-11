package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.Config;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【终界之星】悬浮物品管理器 + 末影龙击败生成
//
// 职责 1（末影龙击败）：LivingDeathEvent 监听 EnderDragon 死亡
//   - 死亡位置为末地（Level.END）
//   - 在返回传送门中心 BlockPos(0, 63, 0) 上方 2.5 格生成悬浮 ItemEntity
//   - 该 ItemEntity 有以下属性：
//       setNoGravity(true)           不受重力，悬浮在空中
//       setPickUpDelay(60)           3 秒内无法被拾取
//       setFoil(true)的 EndStarItem  始终带有附魔光效
//       lifetime = 配置默认12000     10 分钟后自动消失
//   - 记录到"悬浮物品追踪表"中：trackedItems
//   - 每次击败末影龙（包括复活后再击败）都重新生成一次
//
// 职责 2（服务端每 tick 维护）：ServerTickEvent 每 tick 遍历 trackedItems
//   - 验证 ItemEntity 仍存在，否则移除追踪
//   - 锁定位置到 (0, 63, 0) + 2.5 = y=65.5（受水流/推挤时立即拉回）
//   - 缓慢旋转：每 tick 2~3 度（通过设置 ItemEntity.xRot/yRot）
//   - 生成 PORTAL 紫色传送门粒子（视觉提示，每 3 tick 生成一次）
//   - 达到 END_STAR_DESPAWN_TIME tick 时手动移除（原版 ItemEntity.lifetime 也会处理）
//
// 【调试日志】所有关键步骤均输出 INFO 级日志，便于排查生成失败问题
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class FloatingItemManager {

    // 日志器：用于输出调试信息（事件触发、坐标、生成结果等）
    private static final Logger LOGGER = LogUtils.getLogger();

    // 返回传送门中心坐标（末影龙击败后生成终界之星的位置）
    // 【坐标精确说明】原版末地返回传送门结构：
    //   - 基岩柱：y=55 到 y=63（共 9 层实心基岩）
    //     基岩顶部 Y = 63
    //   - 传送门方块层：y=64（EndPortal / EndPortalFrame，玩家返回的出口）
    //   - 龙蛋生成：y=65（击败末影龙后，传送门柱顶上方 1 格）
    //
    // 终界之星生成位置：基岩顶部 Y (63) + 2.5 = y = 65.5
    //   - 绝对不会卡在基岩内部（基岩只到 y=63）
    //   - 位于传送门方块（y=64）上方 1.5 格，玩家站在传送门中心伸手即可拾取
    //   - 位于龙蛋（y=65）下方 0.5 格~上方 0.5 格之间，与龙蛋高度错开，不重叠
    //   - SPAWN_X/SPAWN_Z 使用 0.5 让物品正好在 BlockPos(0,63,0) 方块的中心
    private static final double SPAWN_X = 0.5;      // 方块中心偏移 0.5
    private static final double SPAWN_Y = 65.5;     // 基岩顶部(63) + 2.5 格
    private static final double SPAWN_Z = 0.5;
    // 对应 Vec3 缓存（用于每 tick 锁定位置）
    private static final Vec3 LOCKED_POS = new Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z);

    // 追踪表：ItemEntity 的 UUID → 创建时的世界时间（tick）
    // 每 tick 在 ServerTickEvent 中维护位置/旋转/粒子
    private static final Map<UUID, SpawnedStarInfo> TRACKED_STARS = new LinkedHashMap<>();

    // 单条悬浮星信息
    private static class SpawnedStarInfo {
        final ItemEntity entity;
        final ServerLevel level;
        final long spawnTick;  // 服务端 tickCount 时刻（用于计算存在时间）
        float rotationDegrees;  // 当前旋转角（每 tick 2~3 度）
        final float rotationSpeed;  // 每 tick 旋转速度（2~3 度，随机）

        SpawnedStarInfo(ItemEntity entity, ServerLevel level, long spawnTick) {
            this.entity = entity;
            this.level = level;
            this.spawnTick = spawnTick;
            this.rotationDegrees = 0.0f;
            // 2~3 度随机（每 tick），360 度一圈约 120~180 tick（6~9 秒）
            this.rotationSpeed = 2.0f + level.random.nextFloat() * 1.0f;
        }
    }

    // ========================================================================
    // 末影龙死亡时生成终界之星
    // 【事件】LivingDeathEvent - FORGE 总线事件，任何生物死亡时触发
    // ========================================================================
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 1. 必须是 EnderDragon（末影龙）死亡
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        LOGGER.info("[终界之星] 检测到末影龙死亡事件！实体={}, 位置=({},{},{})",
                dragon.getName().getString(),
                dragon.getX(), dragon.getY(), dragon.getZ());

        // 2. 必须是在服务端的末地（Level.END）
        Level level = dragon.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            LOGGER.warn("[终界之星] 末影龙死亡，但 level 不是 ServerLevel！实际类型={}",
                    level.getClass().getName());
            return;
        }

        if (serverLevel.dimension() != Level.END) {
            LOGGER.warn("[终界之星] 末影龙死亡，但维度不是 Level.END！实际维度={}",
                    serverLevel.dimension().location());
            return;
        }

        LOGGER.info("[终界之星] 维度校验通过，准备在 {} 生成终界之星", LOCKED_POS);

        // 3. 在 (0.5, 66.5, 0.5) 位置生成终界之星
        spawnFloatingEndStar(serverLevel);
    }

    // ========================================================================
    // 生成终界之星悬浮 ItemEntity 并加入追踪表
    // ========================================================================
    private static void spawnFloatingEndStar(ServerLevel serverLevel) {
        // 物品栈：终界之星 ×1
        ItemStack starStack = new ItemStack(ModItems.END_STAR.get(), 1);
        LOGGER.info("[终界之星] 物品栈创建成功：{} ×{}", starStack.getItem(), starStack.getCount());

        // 【改进】使用带坐标的构造方法，更可靠
        //   ItemEntity(Level, double x, double y, double z, ItemStack)
        //   该构造方法内部会自动 setPos 和 setItem
        ItemEntity itemEntity = new ItemEntity(
                serverLevel,
                LOCKED_POS.x, LOCKED_POS.y, LOCKED_POS.z,
                starStack
        );

        // 无初速度（锁定位置，防止掉下去）
        itemEntity.setDeltaMovement(Vec3.ZERO);
        // 【关键】无重力：终界之星在空中悬浮，不下落
        itemEntity.setNoGravity(true);
        // 拾取延迟：3 秒（60 tick），玩家有时间观察，不会刚生成就误拾取
        // 覆盖原配置值，按用户需求固定为 60 tick
        itemEntity.setPickUpDelay(60);
        // 不可被火球/爆炸破坏（保护物品不掉落虚空）
        itemEntity.setInvulnerable(true);
        // 不被熔岩点燃（末地没有熔岩但防万一）
        itemEntity.fireImmune();

        LOGGER.info("[终界之星] ItemEntity 创建完成，UUID={}, 位置=({},{},{})",
                itemEntity.getUUID(), itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());

        // 加入世界
        // 【关键】addFreshEntity 返回 boolean：
        //   - true  = 添加成功
        //   - false = 添加失败（如 UUID 冲突、实体被拒绝等）
        boolean added = serverLevel.addFreshEntity(itemEntity);

        if (added) {
            LOGGER.info("[终界之星] ✅ 物品实体成功加入世界！");

            // 加入追踪表（每 tick 维护旋转/粒子/位置锁定）
            long now = serverLevel.getServer().getTickCount();
            UUID id = itemEntity.getUUID();
            TRACKED_STARS.put(id, new SpawnedStarInfo(itemEntity, serverLevel, now));
            LOGGER.info("[终界之星] 已加入追踪表，当前追踪数量={}", TRACKED_STARS.size());
        } else {
            LOGGER.error("[终界之星] ❌ addFreshEntity 返回 false！物品实体添加失败！");
            LOGGER.error("[终界之星] 可能原因：UUID 冲突 / 实体被其他模组拒绝 / 维度限制");
        }
    }

    // ========================================================================
    // 每 tick 维护所有已生成的终界之星
    //   - 验证仍存在
    //   - 锁定位置
    //   - 每 tick 旋转 2~3 度
    //   - 每 3 tick 生成 PORTAL 紫色传送门粒子
    //   - 到期手动删除
    // ========================================================================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 仅 END 阶段执行（所有实体行为完成后才做"维护"）
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // 没有追踪中的星星直接返回
        if (TRACKED_STARS.isEmpty()) {
            return;
        }

        long currentTick = event.getServer().getTickCount();
        int despawnTime = Config.endStarDespawnTime;

        // 迭代所有追踪中的星星（允许在循环中移除失效条目）
        Iterator<Map.Entry<UUID, SpawnedStarInfo>> iterator = TRACKED_STARS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnedStarInfo> entry = iterator.next();
            SpawnedStarInfo info = entry.getValue();
            ItemEntity entity = info.entity;

            // 1. 检查 ItemEntity 是否已被移除/丢弃/拾取
            //    拾取后 entity.isRemoved() = true
            //    达到 lifetime 时 ItemEntity 也会被移除
            if (entity == null || !entity.isAlive() || entity.isRemoved()) {
                LOGGER.info("[终界之星] 悬浮星被移除/拾取，从追踪表中剔除。UUID={}", entry.getKey());
                iterator.remove();
                continue;
            }

            // 2. 检查是否超过最大存在时间（到期手动删除，兜底）
            long age = currentTick - info.spawnTick;
            if (age >= despawnTime) {
                LOGGER.info("[终界之星] 悬浮星到期（age={}），手动删除。UUID={}", age, entry.getKey());
                entity.discard();  // 立即删除
                iterator.remove();
                continue;
            }

            // 3. 位置锁定：始终保持在 (0.5, 65.5, 0.5)
            //    （防止水流/推挤/爆炸推动）
            double dx = entity.getX() - LOCKED_POS.x;
            double dy = entity.getY() - LOCKED_POS.y;
            double dz = entity.getZ() - LOCKED_POS.z;
            if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001 || Math.abs(dz) > 0.001) {
                entity.setPos(LOCKED_POS.x, LOCKED_POS.y, LOCKED_POS.z);
                entity.setDeltaMovement(Vec3.ZERO);  // 同时清零速度
            } else {
                // 位置正确也清零速度（防止持续飘移）
                entity.setDeltaMovement(Vec3.ZERO);
            }

            // 4. 缓慢旋转：每 tick 增加 rotationSpeed 度（2~3 度）
            //    设置 yRot 以实现视觉旋转
            info.rotationDegrees += info.rotationSpeed;
            // 归一化到 0~360 范围，避免 float 精度溢出
            if (info.rotationDegrees > 360.0f) {
                info.rotationDegrees -= 360.0f;
            }
            // 设置 Y 轴旋转（等价于旋转物品展示）
            entity.setYRot(info.rotationDegrees);
            entity.yRotO = info.rotationDegrees;  // 前一刻旋转角（用于渲染插值）
            entity.setXRot(0.0f);  // X 轴不旋转（保持水平）
            entity.xRotO = 0.0f;
            // 上下轻微浮动：用 sin(age) 做 -0.1 ~ 0.1 的微调
            double bobY = Math.sin(age * 0.05) * 0.1;
            entity.setPos(LOCKED_POS.x, LOCKED_POS.y + bobY, LOCKED_POS.z);

            // 5. 每 3 tick 生成 PORTAL 紫色传送门粒子
            //    数量=4，在 LOCKED_POS 周围 0.5 格立方体中随机
            if (currentTick % 3 == 0) {
                spawnPortalParticles(info.level);
            }
        }
    }

    // ========================================================================
    // 视觉提示：紫色传送门粒子（ParticleTypes.PORTAL）
    // 每 3 tick 生成 4 个，围绕 LOCKED_POS 中心球体分布
    // PORTAL 粒子在原版中就是紫色末地传送门粒子，适合末地主题
    // ========================================================================
    private static void spawnPortalParticles(ServerLevel serverLevel) {
        double cx = LOCKED_POS.x;
        double cy = LOCKED_POS.y;
        double cz = LOCKED_POS.z;
        double radius = 0.5;

        // 4 个粒子（每 3 tick）= 约 27 粒子/秒，视觉效果明显但不卡顿
        for (int i = 0; i < 4; i++) {
            // 在 0.5 半径的球壳中随机取点
            double theta = Math.random() * 2 * Math.PI;
            double phi = Math.acos(2 * Math.random() - 1);
            double r = radius * (0.5 + Math.random() * 0.5);
            double px = cx + r * Math.sin(phi) * Math.cos(theta);
            double py = cy + r * Math.sin(phi) * Math.sin(theta);
            double pz = cz + r * Math.cos(phi);
            // PORTAL 粒子：传入速度为其颜色/速度参数（末地粒子自带紫色）
            serverLevel.sendParticles(
                    ParticleTypes.PORTAL,
                    px, py, pz,
                    1,           // count=1
                    0, 0, 0,     // delta x/y/z
                    0            // speed（PORTAL 不使用此参数，取随机）
            );
        }
    }
}
