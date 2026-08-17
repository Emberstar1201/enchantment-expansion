package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.enchantment.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// ========================================================================
// 人权剑（Sword of the Free Will）物品类
//
// 【基础属性】
//   攻击伤害：10（自定义 Tier 基础伤害 0 + 修饰符 10 = 总伤害加成 10）
//   攻击速度：4.5（原版基础 4.0 + 0.5）
//   耐久度：无限（canBeDepleted() 返回 false）
//
// 【内置附魔】（通过 inventoryTick 自动补充 NBT 数据）
//   锋利 X、亡灵杀手 X、击退 II、拂晓 I
//
// 【获取方式】
//   击败凋灵掉落（由 SwordOfTheFreeWillHandler 实现）
// ========================================================================
public class SwordOfTheFreeWill extends SwordItem {

    // NBT 标记键：用于标记该物品的附魔数据已初始化
    private static final String NBT_ENCH_INIT = "SOTFW_EnchInit";

    // ================================================================
    // 自定义 Tier：基础伤害为 0，耐久很高但不可损坏
    // ================================================================
    private static final Tier FREE_WILL_TIER = new Tier() {
        @Override public int getUses() { return 99999; }
        @Override public float getSpeed() { return 9.0f; }
        @Override public float getAttackDamageBonus() { return 0; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.NETHERITE_BLOCK);
        }
    };

    public SwordOfTheFreeWill() {
        super(FREE_WILL_TIER, 10, 0.5f,
                new Item.Properties()
                        .rarity(Rarity.EPIC)
                        .stacksTo(1)
                        .fireResistant()
        );
    }

    // ================================================================
    // inventoryTick：核心方法——确保每次 tick 时物品都有附魔 NBT
    //
    // 为什么在这里做而不是在构造时做？
    //   因为 Item 的构造函数不创建 ItemStack，NBT 数据只能在
    //   ItemStack 创建后的首次 tick 时动态补充。
    //
    // 为什么要每次 tick 都检查？
    //   首次 tick 时补充 NBT 后设置标记位，后续不再执行。
    //   单次 NBT 读取开销极小（微秒级），不影响性能。
    //
    // 覆盖场景：
    //   - /give 命令创建 → 首次进入背包 tick 时补充
    //   - 凋灵掉落 → Handler 的 createEnchantedSword() 已补充
    //   - 创造模式物品栏取出 → 首次 tick 时补充
    //   - 其他模组/Caps 创建 → 首次 tick 时补充
    // ================================================================
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity,
                              int slot, boolean selected) {
        // 仅服务端处理 NBT（客户端也调用 inventoryTick，但不修改 NBT）
        if (level.isClientSide) return;

        CompoundTag tag = stack.getOrCreateTag();

        // 已初始化 → 跳过
        if (tag.getBoolean(NBT_ENCH_INIT)) return;

        // ================================================================
        // 未初始化 → 添加附魔 NBT 数据
        // ================================================================
        ListTag enchList = new ListTag();

        // 锋利 X
        CompoundTag sharpness = new CompoundTag();
        sharpness.putString("id", "minecraft:sharpness");
        sharpness.putShort("lvl", (short) 10);
        enchList.add(sharpness);

        // 亡灵杀手 X
        CompoundTag smite = new CompoundTag();
        smite.putString("id", "minecraft:smite");
        smite.putShort("lvl", (short) 10);
        enchList.add(smite);

        // 击退 II
        CompoundTag knockback = new CompoundTag();
        knockback.putString("id", "minecraft:knockback");
        knockback.putShort("lvl", (short) 2);
        enchList.add(knockback);

        // 拂晓 I
        CompoundTag dawn = new CompoundTag();
        dawn.putString("id", "enchantment_expansion:dawn");
        dawn.putShort("lvl", (short) 1);
        enchList.add(dawn);

        tag.put("Enchantments", enchList);
        tag.putBoolean(NBT_ENCH_INIT, true);
        stack.setTag(tag);
    }

    // ================================================================
    // getAllEnchantments：将内置附魔加入返回值
    //
    // 注意：这个方法主要用于 UI 显示（创造模式物品栏、F3+H 调试）
    // 实际附魔效果由 NBT Enchantments 标签保证，EnchantmentHelper
    // 只读取 NBT 标签，不调用此方法。
    // ================================================================
    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> map = super.getAllEnchantments(stack);
        map.put(Enchantments.SHARPNESS, 10);
        map.put(Enchantments.SMITE, 10);
        map.put(Enchantments.KNOCKBACK, 2);
        map.put(ModEnchantments.DAWN.get(), 1);
        return map;
    }

    // ================================================================
    // 耐久度无限
    // ================================================================
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    // 不消耗耐久（damageItem 返回 0 表示不产生任何损伤）
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount,
                                                    T entity, Consumer<T> onBroken) {
        return 0;
    }

    // ================================================================
    // 不可在附魔台/铁砧上额外附魔
    // ================================================================
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    // ================================================================
    // 支持剑的 ToolAction（横扫攻击等）
    // ================================================================
    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction);
    }

    // ================================================================
    // 始终带有附魔光效
    // ================================================================
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ================================================================
    // 工具提示：显示内置附魔
    // 人权剑的附魔数据由 inventoryTick 自动补充到 NBT，
    // 父类的 appendHoverText 会自动读取 NBT Enchantments 标签并显示。
    // ================================================================
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // 没有额外需要添加的文本——NBT 附魔由父类自动显示
    }
}