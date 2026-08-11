package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// ========================================================================
// 【成就调试处理器】
//
// 用于排查"终末已至，新的开始"成就不触发/不显示的问题。
//
// 监听两个关键事件：
//   1. EntityItemPickupEvent - 玩家捡起物品时触发
//      → 检测玩家是否捡起了终界之星
//      → 输出日志确认 inventory_changed 触发器的前置条件是否满足
//
//   2. AdvancementEvent - 玩家获得成就时触发
//      → 输出所有成就解锁日志
//      → 特别标记本模组的成就
//
// 【排查思路】
//   如果"捡起终界之星"日志出现但"成就要解锁"日志未出现：
//     → 说明成就 JSON 未加载（检查路径/格式）
//     → 或父节点 minecraft:end/root 未解锁（需先进入末地）
//
//   如果两个日志都未出现：
//     → 说明事件监听器未注册（检查 @Mod.EventBusSubscriber）
//
// 生产环境可删除此类，不影响功能。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID)
public class AdvancementDebugHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 本模组成就的 ResourceLocation（与 JSON 文件路径对应）
    private static final ResourceLocation END_STAR_ADVANCEMENT_ID =
            new ResourceLocation(EnchantmentExpansion.MODID, "the_end_new_beginning");

    // ========================================================================
    // 事件 1：玩家捡起物品
    // 【用途】确认终界之星是否被成功捡起
    //   这是 inventory_changed 触发器的前置条件
    // ========================================================================
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        ItemEntity itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItem();

        // 只关注终界之星的拾取
        if (!stack.is(ModItems.END_STAR.get())) {
            return;
        }

        LOGGER.info("[成就调试] 玩家 {} 捡起终界之星！数量={}, 物品ID={}",
                player.getName().getString(),
                stack.getCount(),
                stack.getItem());

        // 检查玩家是否在服务端（成就触发必须在服务端）
        if (player.level().isClientSide()) {
            LOGGER.warn("[成就调试] ⚠️ 拾取事件在客户端触发！成就在客户端不会触发，请检查。");
        } else {
            LOGGER.info("[成就调试] ✅ 拾取事件在服务端触发，inventory_changed 触发器应被满足");
        }

        // 检查玩家是否已解锁父成就 minecraft:end/root
        // （只有父成就解锁后，子成就才会显示 toast）
        // 【注意】getAdvancements() 方法在 ServerPlayer 上，不在 Player 上
        if (player instanceof ServerPlayer serverPlayer && player.getServer() != null) {
            Advancement rootAdv = player.getServer().getAdvancements()
                    .getAdvancement(new ResourceLocation("minecraft", "end/root"));
            if (rootAdv != null) {
                boolean rootDone = serverPlayer.getAdvancements()
                        .getOrStartProgress(rootAdv)
                        .isDone();
                LOGGER.info("[成就调试] 父成就 minecraft:end/root 解锁状态: {}",
                        rootDone ? "✅ 已解锁" : "❌ 未解锁（需先进入末地维度）");
            } else {
                LOGGER.warn("[成就调试] ⚠️ 找不到原版成就 minecraft:end/root！");
            }
        }
    }

    // ========================================================================
    // 事件 2：玩家获得成就
    // 【用途】确认成就是否被成功解锁
    // ========================================================================
    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent event) {
        Advancement advancement = event.getAdvancement();
        ResourceLocation id = advancement.getId();

        // 输出所有成就解锁日志
        LOGGER.info("[成就调试] 玩家 {} 获得成就: {}",
                event.getEntity().getName().getString(),
                id);

        // 特别标记本模组的成就
        if (id.getNamespace().equals(EnchantmentExpansion.MODID)) {
            LOGGER.info("[成就调试] ✅✅✅ 本模组成就被触发！成就ID={}", id);

            if (id.equals(END_STAR_ADVANCEMENT_ID)) {
                LOGGER.info("[成就调试] 🎉🎉🎉 \"终末已至，新的开始\" 成就成功解锁！");
            }
        }
    }
}
