package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.util.TLMSafe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// ========================================================================
// 【晨曦之星】数据管理工具类
//
// 【原理】
//   与拂晓（DawnData）相同的双存储结构：
//     1. 实体 PersistentData（服务器端持久化，不自动同步到客户端）
//     2. 物品 NBT（自动同步到客户端，用于 lore 显示）
//
//   PersistentData 结构：DawnStarData: {
//       charge:        <double>  当前晨光层数（0 ~ 10）
//       activeTicks:   <int>     「晨曦」状态剩余 tick（>0 表示激活中）
//       cooldownTicks: <int>     日出爆发冷却剩余 tick
//       obtained:      <boolean> 是否已通过仪式获得过晨曦之星
//   }
//
//   lore 结构：display.Lore = [ {晨光条}, {状态行} ]
//     每行是 Component.Serializer 序列化后的 JSON 字符串，
//     用 translatable 组件写入，客户端按语言文件本地化。
//
// 【为什么参数类型是 LivingEntity 而不是 Player】
//   车万女仆（EntityMaid）同样是 LivingEntity，Forge 也为它提供了
//   getPersistentData()。把参数放宽到 LivingEntity 后，女仆可以使用
//   与玩家完全相同的晨光 / 晨曦状态存取逻辑，且数据存在女仆自己的
//   PersistentData 里，与玩家互不干扰。
//   （玩家调用点无需改动：Player 是 LivingEntity 的子类。）
// ========================================================================
public class DawnStarData {

    private static final String TAG_ROOT = "DawnStarData";
    private static final String KEY_CHARGE = "charge";
    private static final String KEY_ACTIVE_TICKS = "activeTicks";
    private static final String KEY_COOLDOWN_TICKS = "cooldownTicks";
    private static final String KEY_OBTAINED = "obtained";

    // ========================================================================
    // 数值常量（唯一来源，Handler 与 lore 均引用此处）
    // ========================================================================
    /** 晨光层数上限 */
    public static final double MAX_CHARGE = 10.0D;
    /** 击杀敌对生物积累的晨光层数 */
    public static final double KILL_CHARGE_GAIN = 0.25D;
    /** 主手武器带拂晓附魔时，击杀积累的晨光层数 */
    public static final double DAWN_KILL_CHARGE_GAIN = 2.0D;

    /** 「晨曦」状态持续时间：180 秒 */
    public static final int DAWN_ACTIVE_TICKS = 180 * 20;
    /** 日出爆发冷却：90 秒 */
    public static final int DAWN_COOLDOWN_TICKS = 90 * 20;

    /** 「晨曦」状态伤害倍率 ×1.25 */
    public static final float DAWN_DAMAGE_MULTIPLIER = 1.25F;
    /** 「晨曦」状态暴击率加成 +10% */
    public static final double DAWN_CRIT_BONUS = 0.10D;
    /** 「晨曦」状态暴击伤害倍率（原版暴击倍率） */
    public static final float DAWN_CRIT_MULTIPLIER = 1.5F;
    /** 「晨曦」状态移速加成 +10% */
    public static final double DAWN_SPEED_BONUS = 0.10D;
    /** 「晨曦」状态回血间隔：每 2 秒 */
    public static final int DAWN_REGEN_INTERVAL_TICKS = 40;
    /** 「晨曦」状态每次回复生命值：2.5 点 */
    public static final float DAWN_REGEN_AMOUNT = 2.5F;

    /** 盔甲 NBT 中标记嵌入星星类型的键 */
    public static final String NBT_EMBEDDED_STAR = "EmbeddedStar";
    /** 本星星的嵌入类型字符串 */
    public static final String STAR_TYPE = "dawn_star";

    // ========================================================================
    // 实体 PersistentData 存取（服务器端持久化）
    // ========================================================================

    /** 获取当前晨光层数 */
    public static double getCharge(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getDouble(KEY_CHARGE);
    }

    /** 设置晨光层数（自动夹在 0 ~ MAX_CHARGE 之间） */
    public static void setCharge(LivingEntity entity, double charge) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putDouble(KEY_CHARGE, Math.max(0.0D, Math.min(MAX_CHARGE, charge)));
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 增加晨光层数，返回增加后的层数 */
    public static double addCharge(LivingEntity entity, double amount) {
        double updated = Math.max(0.0D, Math.min(MAX_CHARGE, getCharge(entity) + amount));
        setCharge(entity, updated);
        return updated;
    }

    /** 「晨曦」状态是否激活中 */
    public static boolean isDawnActive(LivingEntity entity) {
        return getActiveTicks(entity) > 0;
    }

    /** 获取「晨曦」状态剩余 tick */
    public static int getActiveTicks(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getInt(KEY_ACTIVE_TICKS);
    }

    /** 设置「晨曦」状态剩余 tick */
    public static void setActiveTicks(LivingEntity entity, int ticks) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putInt(KEY_ACTIVE_TICKS, Math.max(0, ticks));
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 获取日出爆发冷却剩余 tick */
    public static int getCooldownTicks(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getInt(KEY_COOLDOWN_TICKS);
    }

    /** 设置日出爆发冷却剩余 tick */
    public static void setCooldownTicks(LivingEntity entity, int ticks) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putInt(KEY_COOLDOWN_TICKS, Math.max(0, ticks));
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    /** 是否已通过仪式获得过晨曦之星（防止重复发放） */
    public static boolean hasObtained(LivingEntity entity) {
        return entity.getPersistentData().getCompound(TAG_ROOT).getBoolean(KEY_OBTAINED);
    }

    /** 标记已获得晨曦之星 */
    public static void setObtained(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData().getCompound(TAG_ROOT);
        data.putBoolean(KEY_OBTAINED, true);
        entity.getPersistentData().put(TAG_ROOT, data);
    }

    // ========================================================================
    // 持有判定：手持 / 副手 / 盔甲嵌入
    // ========================================================================

    /** 判断物品是否为晨曦之星本体 */
    public static boolean isDawnStar(ItemStack stack) {
        return stack.is(com.github.emberstar1201.enchantmentex.item.ModItems.DAWN_STAR.get());
    }

    /** 判断盔甲是否嵌入了晨曦之星 */
    public static boolean hasEmbeddedDawnStar(ItemStack armorStack) {
        return armorStack.hasTag()
                && STAR_TYPE.equals(armorStack.getTag().getString(NBT_EMBEDDED_STAR));
    }

    /** 是否持有（手持/副手）或穿戴（嵌入盔甲）晨曦之星 */
    public static boolean isHoldingOrWearingDawnStar(LivingEntity entity) {
        if (isDawnStar(entity.getMainHandItem()) || isDawnStar(entity.getOffhandItem())) {
            return true;
        }
        for (ItemStack armorPiece : entity.getArmorSlots()) {
            if (!armorPiece.isEmpty() && hasEmbeddedDawnStar(armorPiece)) {
                return true;
            }
        }
        // 女仆（车万女仆，软前置）：饰品栏里的晨曦之星同样视为「携带」。
        // 未安装车万女仆时 isTouhouMaid 恒为 false，此处等同空转。
        if (TLMSafe.isTouhouMaid(entity)) {
            for (ItemStack stack : TLMSafe.collectMaidBaubles(entity)) {
                if (!stack.isEmpty() && isDawnStar(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========================================================================
    // lore 刷新：把晨光层数与状态写入物品 NBT 的 display.Lore
    //   - 写入位置：主手、副手的晨曦之星；以及嵌入了晨曦之星的盔甲
    //   - 每行都是 translatable 组件序列化后的 JSON，客户端按语言文件渲染
    // ========================================================================
    public static void refreshLore(LivingEntity entity) {
        double charge = getCharge(entity);
        int activeTicks = getActiveTicks(entity);
        int cooldownTicks = getCooldownTicks(entity);

        List<Component> lore = buildLore(charge, activeTicks, cooldownTicks);

        applyLore(entity.getMainHandItem(), lore);
        applyLore(entity.getOffhandItem(), lore);
        for (ItemStack armorPiece : entity.getArmorSlots()) {
            if (!armorPiece.isEmpty() && hasEmbeddedDawnStar(armorPiece)) {
                applyLore(armorPiece, lore);
            }
        }
        // 女仆（车万女仆，软前置）：饰品栏中的晨曦之星也要同步 lore
        if (TLMSafe.isTouhouMaid(entity)) {
            for (ItemStack stack : TLMSafe.collectMaidBaubles(entity)) {
                applyLore(stack, lore);
            }
        }
    }

    /** 把当前晨光层数/状态写入指定物品（用于刚发放、尚未进入背包的星星） */
    public static void writeLoreTo(ItemStack stack, LivingEntity entity) {
        applyLore(stack, buildLore(getCharge(entity), getActiveTicks(entity), getCooldownTicks(entity)));
    }

    /** 是否存在"已持有但还没写过 lore"的相关物品（如刚嵌入盔甲的星星） */
    public static boolean needsLoreRefresh(LivingEntity entity) {
        if (needsRefresh(entity.getMainHandItem()) || needsRefresh(entity.getOffhandItem())) {
            return true;
        }
        for (ItemStack armorPiece : entity.getArmorSlots()) {
            if (needsRefresh(armorPiece)) {
                return true;
            }
        }
        if (TLMSafe.isTouhouMaid(entity)) {
            for (ItemStack stack : TLMSafe.collectMaidBaubles(entity)) {
                if (needsRefresh(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean needsRefresh(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!isDawnStar(stack) && !hasEmbeddedDawnStar(stack)) return false;
        return !(stack.hasTag() && stack.getTag().getCompound("display").contains("Lore"));
    }

    /** 构建 lore 行（晨光条 + 状态行） */
    private static List<Component> buildLore(double charge, int activeTicks, int cooldownTicks) {
        List<Component> lore = new ArrayList<>(2);

        int filled = (int) Math.floor(charge);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < (int) MAX_CHARGE; i++) {
            bar.append(i < filled ? '▮' : '▯');
        }
        lore.add(Component.translatable("item.enchantment_expansion.dawn_star.lore.charge",
                bar.toString(), trimNumber(charge)));

        if (activeTicks > 0) {
            lore.add(Component.translatable("item.enchantment_expansion.dawn_star.lore.active",
                    activeTicks / 20));
        } else if (cooldownTicks > 0) {
            lore.add(Component.translatable("item.enchantment_expansion.dawn_star.lore.cooldown",
                    cooldownTicks / 20));
        } else {
            lore.add(Component.translatable("item.enchantment_expansion.dawn_star.lore.idle"));
        }

        return lore;
    }

    /** 晨光层数显示文本：去掉无意义的小数尾巴（5.0 → 5，5.25 → 5.25） */
    private static String trimNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.format("%.2f", value);
    }

    /** 把 lore 写入物品 NBT；非目标物品直接跳过 */
    private static void applyLore(ItemStack stack, List<Component> lore) {
        if (stack.isEmpty()) return;
        if (!isDawnStar(stack) && !hasEmbeddedDawnStar(stack)) return;

        ListTag loreTag = new ListTag();
        for (Component line : lore) {
            loreTag.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        stack.getOrCreateTagElement("display").put("Lore", loreTag);
    }
}
