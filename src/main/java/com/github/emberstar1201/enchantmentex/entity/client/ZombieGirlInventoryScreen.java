package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.entity.menu.ZombieGirlInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 丧尸娘背包界面（客户端渲染）。
 *
 * 不使用 GUI 贴图，全部用纯色矩形程序化绘制：浅灰面板 + 深灰边框 + 原版风格凹槽槽位，
 * 槽位坐标直接遍历菜单中的 {@link Slot}，与服务端菜单定义天然保持一致，
 * 避免新增资源文件与坐标错位。
 *
 * 界面布局（176 × 268）：
 * <pre>
 *   y = 6    标题「丧尸娘背包」
 *   y = 18   6 个装备槽（头 / 胸 / 腿 / 脚 / 主手 / 副手）
 *   y = 38 起 64 格随身背包（8 列 × 8 行，任意物品可放入），最后一行 y = 162
 *   y = 180  玩家背包标签
 *   y = 190 / 208 / 226  玩家背包 27 格
 *   y = 244  玩家快捷栏 9 格
 * </pre>
 */
public class ZombieGirlInventoryScreen extends AbstractContainerScreen<ZombieGirlInventoryMenu> {

    /** 面板与槽位配色（ARGB），尽量贴近原版箱子界面观感。 */
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_SLOT_BORDER = 0xFF8B8B8B;
    private static final int COLOR_SLOT_INSIDE = 0xFF373737;

    public ZombieGirlInventoryScreen(ZombieGirlInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 268;
        // 玩家背包标签位于 64 格随身背包与玩家槽位之间（首行玩家槽 y=190，标签 y=180）
        this.inventoryLabelY = 180;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int originX = this.leftPos;
        int originY = this.topPos;

        // 主面板背景
        graphics.fill(originX, originY,
                originX + this.imageWidth, originY + this.imageHeight, COLOR_PANEL);
        // 四周 1 像素深色边框
        graphics.fill(originX, originY,
                originX + this.imageWidth, originY + 1, COLOR_BORDER);
        graphics.fill(originX, originY + this.imageHeight - 1,
                originX + this.imageWidth, originY + this.imageHeight, COLOR_BORDER);
        graphics.fill(originX, originY,
                originX + 1, originY + this.imageHeight, COLOR_BORDER);
        graphics.fill(originX + this.imageWidth - 1, originY,
                originX + this.imageWidth, originY + this.imageHeight, COLOR_BORDER);

        // 所有槽位：1 像素浅灰外框 + 16×16 深色凹槽，模拟原版槽位外观
        for (Slot slot : this.menu.slots) {
            int slotX = originX + slot.x;
            int slotY = originY + slot.y;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, COLOR_SLOT_BORDER);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, COLOR_SLOT_INSIDE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题与玩家背包标签沿用默认绘制；
        // 背包已放开为任意物品，不再需要旧的「生肉背包」分区标签。
        super.renderLabels(graphics, mouseX, mouseY);
    }
}
