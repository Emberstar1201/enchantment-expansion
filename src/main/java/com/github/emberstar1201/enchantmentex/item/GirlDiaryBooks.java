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
 * <p>每种少女有多个日记变种，驯服时随机选取一个，
 * 不同变种讲述不同的感染/溺水经历。</p>
 *
 * <p>书页内容全部是可翻译组件（{@code book.enchantment_expansion.*_diary*.pageN}），
 * 客户端按各自语言渲染；标题与作者是成书 NBT 的纯字符串（无法本地化），
 * 因此直接写成「中文 / English」双语。</p>
 */
public final class GirlDiaryBooks {
    /** 日记页数（page1 ~ pageN 翻译键按此数量读取）。 */
    private static final int PAGE_COUNT = 4;

    /**
     * 丧尸娘日记变种：地窖、森林、瘟疫。
     * 对应翻译键前缀为 book.enchantment_expansion.{key}.pageN。
     */
    private static final String[] ZOMBIE_DIARY_VARIANTS = {
            "zombie_girl_diary",        // 地窖：村庄遇袭，藏在地窖中被感染
            "zombie_girl_diary_forest", // 森林：采药时在森林深处被怪物围攻
            "zombie_girl_diary_plague"  // 瘟疫：村庄瘟疫蔓延，染病后异变
    };

    /**
     * 溺尸娘日记变种：船难、河流、迷雾。
     * 对应翻译键前缀为 book.enchantment_expansion.{key}.pageN。
     */
    private static final String[] DROWNED_DIARY_VARIANTS = {
            "drowned_girl_diary",        // 船难：暴风雨中翻船沉没
            "drowned_girl_diary_river",  // 河流：被急流卷走沉入河底
            "drowned_girl_diary_fog"     // 迷雾：浓雾中从码头坠水
    };

    /**
     * 幸存者少女日记变种：救助、护送、照顾、治疗。
     * 讲述幸存者少女被丧尸娘/溺尸娘救助的不同经历。
     */
    private static final String[] SURVIVOR_DIARY_VARIANTS = {
            "survivor_girl_diary",         // 救助：被怪物追击时丧尸出手相救，带回营地
            "survivor_girl_diary_observe",  // 护送：迷路时溺尸指路并护送回村
            "survivor_girl_diary_resonance",// 照顾：发烧时丧尸照料三天
            "survivor_girl_diary_heal"      // 治疗：受伤时丧尸采药包扎
    };

    private GirlDiaryBooks() {
    }

    /** 驯服丧尸娘时随机选取一个日记变种放进她的背包。 */
    public static void giveZombieGirlDiary(Mob girl) {
        String key = ZOMBIE_DIARY_VARIANTS[girl.getRandom().nextInt(ZOMBIE_DIARY_VARIANTS.length)];
        give(girl, key, "破损的日记 / Torn Diary");
    }

    /** 驯服溺尸娘时随机选取一个日记变种放进她的背包。 */
    public static void giveDrownedGirlDiary(Mob girl) {
        String key = DROWNED_DIARY_VARIANTS[girl.getRandom().nextInt(DROWNED_DIARY_VARIANTS.length)];
        give(girl, key, "浸水的日记 / Soaked Diary");
    }

    /** 驯服幸存者少女时随机选取一个日记变种放进她的背包。 */
    public static void giveSurvivorGirlDiary(Mob girl) {
        String key = SURVIVOR_DIARY_VARIANTS[girl.getRandom().nextInt(SURVIVOR_DIARY_VARIANTS.length)];
        give(girl, key, "幸存者的日记 / Survivor's Diary");
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
