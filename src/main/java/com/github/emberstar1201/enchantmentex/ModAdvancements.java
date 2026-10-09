package com.github.emberstar1201.enchantmentex;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 成就发放工具。
 *
 * <p>少女系列成就（靠近发现 / 驯服 / 治愈 / 共眠）在原版没有对应的
 * 触发器（tame_animal 只认 TamableAnimal），统一使用
 * {@code minecraft:impossible} 触发器 + 代码手动发放。</p>
 */
public final class ModAdvancements {

    /** impossible 触发器成就的统一条件名（与各成就 JSON 中的 criteria 键一致）。 */
    private static final String CRITERION = "granted";

    /** 「友善的亡灵？」：靠近一只丧尸娘或溺尸娘。 */
    public static final String FRIENDLY_UNDEAD = "adventure/friendly_undead";
    /** 「令人心疼的少女」：驯服一只丧尸娘或溺尸娘。 */
    public static final String TAME_GIRL = "adventure/tame_girl";
    /** 「驶向第2次生命」：治愈一只丧尸娘或溺尸娘。 */
    public static final String SECOND_LIFE = "adventure/second_life";
    /** 「美梦重现」：与正在睡觉的幸存者少女一同入睡。 */
    public static final String SWEET_DREAMS = "adventure/sweet_dreams";

    private ModAdvancements() {
    }

    /** 发放成就；已完成或成就不存在时静默跳过。 */
    public static void award(ServerPlayer player, String path) {
        Advancement advancement = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(EnchantmentExpansion.MODID, path));
        if (advancement == null) {
            return;
        }
        if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }

    /** 是否已达成指定成就（用于跳过重复检测，省掉每轮的实体扫描）。 */
    public static boolean hasDone(ServerPlayer player, String path) {
        Advancement advancement = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(EnchantmentExpansion.MODID, path));
        if (advancement == null) {
            return true;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        return progress.isDone();
    }
}
