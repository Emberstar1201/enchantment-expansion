package com.github.emberstar1201.enchantmentex.item;

import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;

/**
 * 驯服少女时塞进她随身背包的日记成书：
 * 丧尸娘 → 「破损的日记」，溺尸娘 → 「浸水的日记」。
 *
 * <p>书页内容全部是可翻译组件（{@code book.enchantment_expansion.*_diary.pageN}），
 * 客户端按各自语言渲染；标题与作者是成书 NBT 的纯字符串（无法本地化），
 * 因此直接写成「中文 / English」双语。</p>
 */
public final class GirlDiaryBooks {
    /** 日记页数（page1 ~ pageN 翻译键按此数量读取）。 */
    private static final int PAGE_COUNT = 4;

    private GirlDiaryBooks() {
    }

    /** 驯服丧尸娘时把「破损的日记」放进她的背包。 */
    public static void giveZombieGirlDiary(Mob girl) {
        give(girl, "zombie_girl_diary", "破损的日记 / Torn Diary");
    }

    /** 驯服溺尸娘时把「浸水的日记」放进她的背包。 */
    public static void giveDrownedGirlDiary(Mob girl) {
        give(girl, "drowned_girl_diary", "浸水的日记 / Soaked Diary");
    }

    /** 组装成书并放入她的随身背包；背包满时掉落在她脚下。 */
    private static void give(Mob girl, String diaryKey, String title) {
        if (!(girl instanceof FriendlyGirlInventory inventory)) {
            return;
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString(WrittenBookItem.TAG_TITLE, title);
        tag.putString(WrittenBookItem.TAG_AUTHOR, "一位少女 · A Girl");
        tag.putInt(WrittenBookItem.TAG_GENERATION, 0);
        ListTag pages = new ListTag();
        for (int i = 1; i <= PAGE_COUNT; i++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable(
                            "book.enchantment_expansion." + diaryKey + ".page" + i))));
        }
        tag.put(WrittenBookItem.TAG_PAGES, pages);
        ItemStack rest = inventory.getMeatInventory().addItem(book);
        if (!rest.isEmpty()) {
            girl.spawnAtLocation(rest);
        }
    }
}
