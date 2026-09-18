package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

// ========================================================================
// 【星星×车万女仆】饰品注册入口
//
// 【为什么必须要有这个类】
//   车万女仆的饰品栏（BaubleItemHandler）在插入物品时会调用
//   `BaubleManager.getBauble(ItemStack) != null` 作为唯一合法性判断，
//   而 `BaubleManager.BAUBLES` 是一张在 `BaubleManager.init()` 末尾就被
//   `ImmutableMap.copyOf(...)` 冻结的只读表。
//   因此仅靠"反射往里塞"是行不通的（init 之后再 put 会抛异常），
//   唯一正确的做法就是在 init 期间通过官方扩展点注册。
//
// 【官方扩展点工作流程】（已通过反编译 BaubleManager 字节码确认）
//   1. 车万女仆启动时调用 AnnotatedInstanceUtil.getModExtensions()，
//      遍历所有模组的注解扫描结果，收集带 @LittleMaidExtension 的类；
//      对每个类执行 Class.forName + newInstance()，要求：公开类 + 公开无参构造。
//   2. 把这些实例塞进 TouhouLittleMaid.EXTENSIONS。
//   3. BaubleManager.init() 中先绑定它自带的 11 个饰品，再遍历 EXTENSIONS
//      并回调本类的 bindMaidBauble(manager)。
//   4. 最后冻结 BAUBLES。
//
// 【软兼容保证】
//   本类是全项目唯一直接 import 车万女仆 API 的类。
//   - 未安装车万女仆时：没有任何代码会 Class.forName 本类
//     （Forge 的注解扫描只读 class 字节，不加载类；
//     只有车万女仆自己才会去加载 @LittleMaidExtension 标记的类），
//     因此不会出现 NoClassDefFoundError。
//   - 注意：本类绝对不能加 @Mod.EventBusSubscriber，
//     否则 FML 会在模组构造期无条件加载它 → 未装车万女仆时崩溃。
//
// 【效果逻辑在哪】
//   本类只负责"让星星能放进饰品栏"这一件事（IMaidBauble 默认方法全为空实现）。
//   具体效果（减伤/生命上限/夜视/水下免疫等）统一由 MaidStarHandler 用
//   Forge 事件实现，避免逻辑分散在车万女仆的钩子里难以维护。
// ========================================================================
@LittleMaidExtension
public class MaidStarBaubleExtension implements ILittleMaid {

    // 所有星星共用同一个无状态饰品实例：效果由事件处理器按物品类型区分，
    // 饰品实例本身不需要携带任何数据，因此无需为每颗星星各建一个对象。
    private static final IMaidBauble STAR_BAUBLE = new IMaidBauble() {
    };

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        bind(manager, ModItems.END_STAR);
        bind(manager, ModItems.OCEAN_STAR);
        bind(manager, ModItems.LIFE_STAR);
        bind(manager, ModItems.VOID_STAR);
        bind(manager, ModItems.STARLIGHT_STAR);
        // 晨曦之星：仅允许放入饰品栏收藏，女仆暂无对应效果
        // （其效果依赖玩家专属的晨光状态机，见 MaidStarHandler 类头说明）
        bind(manager, ModItems.DAWN_STAR);
    }

    /**
     * 把一颗星星登记为女仆饰品。
     * BaubleManager#bind(RegistryObject, IMaidBauble) 内部以 RegistryObject 作 Map key，
     * 而 RegistryObject 的 equals/hashCode 走 ResourceLocation 值比较，
     * 与 getBauble(ItemStack) 时新建的 RegistryObject 能正确匹配。
     */
    private static void bind(BaubleManager manager, RegistryObject<Item> star) {
        manager.bind(star, STAR_BAUBLE);
    }
}
