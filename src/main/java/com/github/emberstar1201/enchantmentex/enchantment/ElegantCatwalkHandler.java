package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【优雅猫步】附魔事件处理器
//
// 核心机制：
//   1. 移速加成：通过"固定UUID + AttributeModifier"附加到 MOVEMENT_SPEED，
//      仅当状态发生切换（穿/脱靴子、进入/离开特殊状态）时才移除旧modifier
//      再添加新的，避免每 tick 叠加导致数值爆炸。
//   2. 免疫掉落伤害：在 LivingFallEvent（发生伤害前的取消点）设置
//      event.setDamageMultiplier(0) + setCanceled(true)，保证穿戴者
//      永远不会因为摔落扣血，等同于永久缓降效果。
//   3. 特殊状态（飞行/骑乘/创造飞行）时，移速加成暂停生效，
//      但掉落伤害免疫依然保留（猫落地永远轻盈，即使鞘翅着地也不受伤）。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class ElegantCatwalkHandler {

    // ========================================================================
    // 固定UUID：用于标识本附魔添加的移速Modifier
    // 用固定UUID才能每次精准找到并移除旧的modifier，避免叠加
    // 生成方式：UUID.randomUUID()，字符仅限0-9、a-f
    // ========================================================================
    private static final UUID ELEGANT_CATWALK_SPEED_UUID =
            UUID.fromString("e1b2f3d4-aa77-4c9f-9d0e-1c2d3e4f8a9c");

    // Modifier 名称（调试时用，避免与其他模组 modifier 混淆）
    private static final String MODIFIER_NAME = "ElegantCatwalk_SpeedBoost";

    // 移速加成倍率：1.45 倍（落在用户要求的 1.4~1.5 区间，平衡值）
    // 【Forge 1.20.1 正确常量】AttributeModifier.Operation.MULTIPLY_BASE
    //   即：最终速度 = 基础速度 × (1 + 0.45) = 1.45 倍基础值
    //   与速度药水是乘法叠加：速度I(+20%)下 = 基础 × 1.20 × 1.45 = 1.74 倍
    //   与风踏涟漪不同：该 modifier 使用独立 UUID，两者若强行共存（通过指令），
    //   会因为 UUID 不同而互相叠加，但 checkCompatibility 已阻止这种情况。
    private static final double SPEED_MULTIPLIER = 0.45;  // 1.45 倍基础值

    // 记录玩家上一 tick 是否拥有附魔（用于检测状态切换）
    private static final String PREV_ENABLED_KEY = "ElegantCatwalk_PrevEnabled";

    // ========================================================================
    // 【移速管理】PlayerTickEvent - 每玩家每 tick 触发
    //
    // 状态切换规则：
    //   ① 上一 tick 没有附魔 → 现在有：添加 modifier（MULTIPLIED_BASE, +0.45）
    //   ② 上一 tick 有附魔 → 现在没有：移除 modifier（按 UUID 找）
    //   ③ 上一 tick 有附魔 → 现在仍有且条件一致：什么都不做（不叠加！）
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅在 Phase.END 处理（原版实体 tick 完成后），避免与原版逻辑冲突
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;

        // 1. 检查靴子上是否有优雅猫步附魔
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int enchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.ELEGANT_CATWALK.get(), boots);
        boolean hasEnchant = enchantLevel > 0;

        // 2. 判断移速加成是否应当生效
        // 飞行 / 骑乘 / 创造飞行 → 移速加成暂停（但掉落免疫依然生效，见下方LivingFallEvent）
        boolean shouldApplySpeed = hasEnchant
                && !player.isFallFlying()           // 鞘翅飞行：用鞘翅自身逻辑
                && !player.isPassenger()            // 骑乘（马、船、矿车）：不影响载具速度
                && !player.getAbilities().flying;   // 创造/旁观者飞行：用飞行速度

        // 3. 读取上一 tick 状态（存 PersistentData，独立于玩家死亡重置吗？
        //    PersistentData 会随玩家存档保存，死亡不会清除，适合记录附魔状态）
        int prevEnabled = player.getPersistentData().getInt(PREV_ENABLED_KEY);
        // prevEnabled 取值：0 = 上一 tick 未应用；1 = 上一 tick 已应用
        boolean prevApplied = prevEnabled == 1;

        // 4. 状态变化时才修改属性，避免每 tick 重复 addModifier 造成叠加
        if (prevApplied != shouldApplySpeed) {
            if (shouldApplySpeed) {
                // 切换到"应用加成"：添加 modifier
                addSpeedModifier(player);
                player.getPersistentData().putInt(PREV_ENABLED_KEY, 1);
            } else {
                // 切换到"不应用加成"：移除 modifier
                removeSpeedModifier(player);
                player.getPersistentData().putInt(PREV_ENABLED_KEY, 0);
            }
        }
    }

    // ========================================================================
    // 【掉落伤害免疫】LivingFallEvent - 实体落地前触发（造成伤害前）
    //
    // 用户要求：穿戴者不会受到任何摔落伤害，等同于永久缓降效果
    //   setDamageMultiplier(0)：将伤害倍率设为 0，即使 fallDistance 极大也不掉血
    //   setCanceled(true)：保险，取消整个摔落事件（含原版其他副作用）
    //
    // 注意：此事件对"所有 LivingEntity"生效，不只是玩家，所以在内部还需
    // 再次判断"实体穿靴子 + 有附魔"，避免影响所有生物。
    // ========================================================================
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();

        // 仅处理有"靴子装备槽"的生物（玩家、僵尸尸壳等可穿戴护甲的实体）
        if (entity == null) return;

        ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
        int enchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.ELEGANT_CATWALK.get(), boots);
        if (enchantLevel <= 0) {
            return; // 没附魔 → 让原版正常计算摔落
        }

        // 【猫步轻盈落地】完全免疫摔落伤害
        // 使用双重保险：
        //   1. setDamageMultiplier(0) → 原版后续计算伤害为 0
        //   2. setCanceled(true)     → 防止其他模组/混编依赖事件触发逻辑
        event.setDamageMultiplier(0);
        event.setCanceled(true);

        // 额外：fallDistance 清零，让下一次摔落从 0 开始计算
        entity.fallDistance = 0;
    }

    // ========================================================================
    // 内部工具方法：为玩家添加移速 modifier（仅调用一次，直到移除）
    // ========================================================================
    private static void addSpeedModifier(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) return;  // 防御：极少数情况下属性可能为 null

        // 先检查是否已经存在同 UUID 的 modifier → 如果有，先移除（避免重复）
        AttributeModifier existing = movementSpeed.getModifier(ELEGANT_CATWALK_SPEED_UUID);
        if (existing != null) {
            movementSpeed.removeModifier(existing);
        }

        // 构造新的 modifier
        // 【Forge 1.20.1 正确常量】Operation.MULTIPLY_BASE
        //   作用：final = base + base * amount = base * (1 + amount)
        //   本例：final = base + base * 0.45 = 1.45 * base
        // 与之对应：Operation.MULTIPLY_TOTAL 是 final = current_value * (1 + amount)（连乘）
        // 采用与原版速度药水/深海探索者相同的 MULTIPLY_BASE 操作，兼容性最好。
        AttributeModifier modifier = new AttributeModifier(
                ELEGANT_CATWALK_SPEED_UUID,
                MODIFIER_NAME,
                SPEED_MULTIPLIER,
                AttributeModifier.Operation.MULTIPLY_BASE
        );

        // 添加 permanent modifier（永久，直到主动移除）
        // 注意：addPermanentModifier 会把 modifier 写入 NBT，
        // 与 addTransientModifier（瞬态，不存盘）有区别。
        // 这里采用 permanent，因为玩家保存/加载后需要自动恢复；
        // 但我们仍然在 tick 中主动管理，确保脱靴子立即移除，不受 NBT 保存影响。
        movementSpeed.addPermanentModifier(modifier);
    }

    // ========================================================================
    // 内部工具方法：移除玩家的移速 modifier（按 UUID 精确查找）
    // ========================================================================
    private static void removeSpeedModifier(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) return;

        AttributeModifier modifier = movementSpeed.getModifier(ELEGANT_CATWALK_SPEED_UUID);
        if (modifier != null) {
            // 按 modifier 引用移除（安全做法，确保 UUID 完全一致才移除）
            movementSpeed.removeModifier(modifier);
        }
    }
}
