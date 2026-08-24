package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.DarkWalkerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【幽匿行者】附魔事件处理器
//
// 设计思路：本 Handler 不直接干涉监守者或幽匿方块（那部分由两个 Mixin
// 完成），而是承担「状态检测与缓存」职责：
//   默认全局生效：只要靴子有幽匿行者附魔，任何维度/生物群系都视为"庇护中"。
//   若配置 restrictToBiome=true，则额外要求玩家所在生物群系在白名单内。
//
//   通过 PlayerTickEvent 每 tick 检测玩家是否满足"幽匿行者激活条件"：
//     ① 全局开关已启用（DarkWalkerConfig）
//     ② 玩家靴子上有「幽匿行者」附魔
//     ③ 生效范围：默认不限生物群系（全局生效）；若配置
//        restrictToBiome=true，则需玩家所在生物群系在列表中
//
// 检测结果缓存到静态 Set<UUID>，供两个 Mixin 快速查询：
//   - ServerLevelGameEventMixin  : 该玩家触发的振动(gameEvent)直接拦截，
//                                 幽匿感测体/尖啸体收不到任何振动信号。
//   - WardenCanTargetMixin       : 监守者的 canTargetEntity() 判定中，
//                                 对缓存中的玩家一律返回 false —— 监守者
//                                 的实体传感器、振动感知、贴脸碰撞、受击
//                                 愤怒全部经由此方法过滤，因此会彻底无视
//                                 玩家（看得到也"不存在"）。
//
// 为什么选 Set<UUID> 而非每 tick 查询：
//   Mixin 的注入点（gameEvent 分发、监守者AI）每 tick 多次触发，
//   每次都做附魔等级校验 + 生物群系查询会导致无谓开销。
//   用每 tick 一次的缓存，Mixin 端 O(1) 查 UUID 集合即可。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DarkWalkerHandler {

    // 当前处于"幽匿行者激活状态"的玩家 UUID 集合（服务端逻辑线程读写）
    private static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();

    // ========================================================================
    // PlayerTickEvent：每 tick 刷新激活状态缓存
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        boolean active = isDarkWalkerActive(player);

        if (active) {
            ACTIVE_PLAYERS.add(player.getUUID());
        } else {
            ACTIVE_PLAYERS.remove(player.getUUID());
        }
    }

    // ========================================================================
    // 核心判定：玩家是否处于"幽匿行者"激活状态
    // ========================================================================
    public static boolean isDarkWalkerActive(Player player) {
        // ① 全局开关
        if (!DarkWalkerConfig.isEnabled()) return false;

        // ② 靴子上是否有幽匿行者附魔（等级 ≥ 1）
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.DARK_WALKER.get(), boots);
        if (level <= 0) return false;

        // ③ 生效范围判定：
        //    - restrictToBiome=false（默认）：不限生物群系，持有附魔即全局生效
        //    - restrictToBiome=true ：仅当所在生物群系在列表中才生效
        if (!DarkWalkerConfig.restrictToBiome) {
            return true;
        }

        BlockPos pos = player.blockPosition();
        Holder<Biome> biomeHolder = player.level().getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> ForgeRegistries.BIOMES.getKey(biomeHolder.value()));
        if (biomeId == null) return false;

        return DarkWalkerConfig.isBiomeAllowed(biomeId.toString());
    }

    // ========================================================================
    // Mixin 查询入口：指定玩家当前是否被"幽匿行者"庇护
    // ========================================================================
    public static boolean isProtected(Player player) {
        return ACTIVE_PLAYERS.contains(player.getUUID());
    }
}