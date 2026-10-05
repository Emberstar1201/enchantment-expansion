package com.github.emberstar1201.enchantmentex.entity.menu;

import com.github.emberstar1201.enchantmentex.entity.ModMenuTypes;
import com.github.emberstar1201.enchantmentex.entity.ZombieGirlEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/**
 * 丧尸娘背包菜单（服务端容器逻辑，客户端共用同一份槽位定义）。
 *
 * 槽位布局：
 * <pre>
 *   0 ~ 5    丧尸娘真实装备槽（头 / 胸 / 腿 / 脚 / 主手 / 副手），
 *            直接读写 LivingEntity 装备槽，穿上盔甲后护甲等属性由原版实时计算；
 *   6 ~ 21   16 格生肉背包，只接受生肉类物品（见 ZombieGirlEntity#isRawMeat）；
 *   22 ~ 48  玩家背包 27 格；
 *   49 ~ 57  玩家快捷栏 9 格。
 * </pre>
 *
 * 主人拥有完整管理权限：装备和生肉都可以普通点击取放，也可以使用 Shift 快速移动。
 * 生肉槽仍然只接受生肉，装备槽仍然按头胸腿脚、主手和副手分别限制物品类型。
 * 菜单直接操作实体持有的真实装备槽与 SimpleContainer，关闭界面时无需额外同步；
 * 生肉背包随实体 NBT 持久化。
 */
public class ZombieGirlInventoryMenu extends AbstractContainerMenu {

    /** 装备槽数量：头、胸、腿、脚、主手、副手。 */
    public static final int EQUIPMENT_SLOT_COUNT = 6;
    /** 生肉背包格数。 */
    public static final int MEAT_SLOT_COUNT = 16;
    /** 玩家背包起始下标（6 + 16 = 22）。 */
    public static final int PLAYER_INVENTORY_START = EQUIPMENT_SLOT_COUNT + MEAT_SLOT_COUNT;
    /** 玩家快捷栏起始下标（22 + 27 = 49）。 */
    public static final int PLAYER_HOTBAR_START = PLAYER_INVENTORY_START + 27;
    /** 全部槽位总数（49 + 9 = 58）。 */
    public static final int MENU_SLOT_COUNT = PLAYER_HOTBAR_START + 9;

    /** 生肉槽在下标区间中的起止（左闭右开）。 */
    private static final int MEAT_SLOT_END = PLAYER_INVENTORY_START;

    /** 当前打开的丧尸娘实体。 */
    private final ZombieGirlEntity zombieGirl;
    public ZombieGirlInventoryMenu(int containerId, Inventory playerInventory, ZombieGirlEntity zombieGirl) {
        super(ModMenuTypes.ZOMBIE_GIRL_INVENTORY.get(), containerId);
        this.zombieGirl = zombieGirl;
        // 装备槽直接映射实体槽位，Slot 父类要求一个 Container，传入永不使用的空实现容器
        Container dummyContainer = new SimpleContainer(EQUIPMENT_SLOT_COUNT);

        // ---- 第一行：6 个装备槽（水平居中，x 起始 34），y = 20 ----
        this.addSlot(new ZombieGirlEquipmentSlot(zombieGirl, EquipmentSlot.HEAD,
                dummyContainer, 0, 34, 20));
        this.addSlot(new ZombieGirlEquipmentSlot(zombieGirl, EquipmentSlot.CHEST,
                dummyContainer, 1, 34 + 18, 20));
        this.addSlot(new ZombieGirlEquipmentSlot(zombieGirl, EquipmentSlot.LEGS,
                dummyContainer, 2, 34 + 36, 20));
        this.addSlot(new ZombieGirlEquipmentSlot(zombieGirl, EquipmentSlot.FEET,
                dummyContainer, 3, 34 + 54, 20));
        this.addSlot(new ZombieGirlEquipmentSlot(zombieGirl, EquipmentSlot.MAINHAND,
                dummyContainer, 4, 34 + 72, 20));
        this.addSlot(new ZombieGirlEquipmentSlot(zombieGirl, EquipmentSlot.OFFHAND,
                dummyContainer, 5, 34 + 90, 20));

        // ---- 第二、三行：16 格生肉背包（每行 8 格），y = 44 / 62 ----
        for (int i = 0; i < MEAT_SLOT_COUNT; i++) {
            int column = i % 8;
            int row = i / 8;
            this.addSlot(new MeatSlot(zombieGirl.getMeatInventory(), i,
                    8 + column * 18, 44 + row * 18));
        }

        // ---- 玩家背包 27 格（3 行 × 9 列），y = 96 起 ----
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 96 + row * 18));
            }
        }
        // ---- 玩家快捷栏 9 格，y = 152 ----
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 152));
        }
    }

    /**
     * Shift + 左键快速移动。
     * 实体侧槽位（0 ~ 21）可以转移回玩家背包；
     * 玩家背包内：生肉优先送入生肉背包，装备 / 武器 / 盾牌送入对应实体装备槽，
     * 其它物品不允许 Shift。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = sourceSlot.getItem();
        ItemStack remainder = current.copy();

        // 从丧尸娘槽位 Shift 取出：装备和生肉都转回主人背包。
        if (index < PLAYER_INVENTORY_START) {
            if (!this.moveItemStackTo(remainder, PLAYER_INVENTORY_START, MENU_SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ZombieGirlEntity.isRawMeat(remainder)) {
            // 生肉 → 16 格生肉背包
            if (!this.moveItemStackTo(remainder, EQUIPMENT_SLOT_COUNT, MEAT_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 装备 / 武器 / 盾牌 → 对应的唯一实体装备槽
            int targetIndex = resolveEquipmentSlotIndex(remainder);
            if (targetIndex < 0
                    || !this.moveItemStackTo(remainder, targetIndex, targetIndex + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        // moveItemStackTo 会直接修改 remainder 数量；同步回源槽位
        if (index < PLAYER_INVENTORY_START) {
            // 实体槽不是普通 Container，必须把未能转移的余量写回实体装备 / 肉槽。
            sourceSlot.set(remainder);
        } else if (remainder.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        if (remainder.getCount() == current.getCount()) {
            // 一个物品都没移走，视为操作失败
            return ItemStack.EMPTY;
        }
        sourceSlot.onTake(player, current);
        return current;
    }

    /**
     * 计算物品应放入哪个装备槽（返回菜单下标 0 ~ 5），无法装备返回 -1。
     * 盔甲 / 头盔类用原版 LivingEntity#getEquipmentSlotForItem 判定，
     * 与右键给生物穿戴、玩家自身穿戴的规则保持一致；
     * 主手只接受剑 / 斧 / 三叉戟，副手接受盾牌。
     */
    private static int resolveEquipmentSlotIndex(ItemStack stack) {
        EquipmentSlot vanillaSlot = LivingEntity.getEquipmentSlotForItem(stack);
        return switch (vanillaSlot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            // 原版把武器、盾牌等非穿戴物品统一归类为 MAINHAND，需要进一步细分
            case MAINHAND -> ZombieGirlEntity.isMeleeWeapon(stack) ? 4
                    : stack.getItem() instanceof ShieldItem ? 5 : -1;
            default -> -1;
        };
    }

    /**
     * 菜单保持有效的条件：丧尸娘存活、仍被驯服、玩家仍是主人且距离不超过 8 格。
     * 任一条件不满足（跑远 / 转手 / 实体死亡）时客户端自动关闭界面。
     */
    @Override
    public boolean stillValid(Player player) {
        return this.zombieGirl.isAlive()
                && !this.zombieGirl.isRemoved()
                && this.zombieGirl.isTamed()
                && this.zombieGirl.isOwnedBy(player)
                && player.distanceToSqr(this.zombieGirl) <= 64.0D;
    }

    /**
     * 直接映射丧尸娘真实装备槽的菜单槽位。
     * 所有读写都绕过父类 Container，直接访问 LivingEntity 装备槽，
     * 因此盔甲值、武器伤害、附魔修饰符等均由原版装备系统实时结算。
     */
    private static class ZombieGirlEquipmentSlot extends Slot {
        private final ZombieGirlEntity zombieGirl;
        private final EquipmentSlot equipmentSlot;

        ZombieGirlEquipmentSlot(ZombieGirlEntity zombieGirl, EquipmentSlot equipmentSlot,
                                Container dummyContainer, int index, int x, int y) {
            super(dummyContainer, index, x, y);
            this.zombieGirl = zombieGirl;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public ItemStack getItem() {
            return this.zombieGirl.getItemBySlot(this.equipmentSlot);
        }

        @Override
        public void set(ItemStack stack) {
            this.zombieGirl.setItemSlot(this.equipmentSlot, stack);
            if (!stack.isEmpty()) {
                // 通过界面放入的装备死亡时必定掉落，保证玩家可以回收
                this.zombieGirl.setDropChance(this.equipmentSlot, 1.0F);
            }
        }

        @Override
        public boolean hasItem() {
            return !this.getItem().isEmpty();
        }

        @Override
        public void setChanged() {
            // 数据直接写入实体，不需要容器变更回调
        }

        /** 从实体真实装备槽取出物品，并立即更新实体装备属性。 */
        @Override
        public ItemStack remove(int amount) {
            ItemStack equipped = this.getItem();
            if (equipped.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = equipped.split(Math.min(amount, equipped.getCount()));
            this.zombieGirl.setItemSlot(this.equipmentSlot, equipped);
            return removed;
        }

        /** 主人可以自由拿取装备，也可以重新放入装备。 */
        @Override
        public boolean mayPickup(Player player) {
            return this.zombieGirl.isOwnedBy(player);
        }

        /** 装备槽不可堆叠。 */
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        /** 按槽位类型限制可放入的物品。 */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return switch (this.equipmentSlot) {
                case HEAD, CHEST, LEGS, FEET ->
                        LivingEntity.getEquipmentSlotForItem(stack) == this.equipmentSlot;
                case MAINHAND -> ZombieGirlEntity.isMeleeWeapon(stack);
                case OFFHAND -> stack.getItem() instanceof ShieldItem;
                default -> false;
            };
        }
    }

    /**
     * 16 格生肉背包槽位：只接受生肉，主人可以自由存取。
     */
    private static class MeatSlot extends Slot {

        MeatSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ZombieGirlEntity.isRawMeat(stack);
        }

        /** 主人可以自由拿取背包中的生肉。 */
        @Override
        public boolean mayPickup(Player player) {
            return true;
        }
    }
}
