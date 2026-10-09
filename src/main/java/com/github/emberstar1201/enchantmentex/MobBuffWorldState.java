package com.github.emberstar1201.enchantmentex;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** 把开局选择保存到世界，而不是保存到某一个玩家。 */
public final class MobBuffWorldState extends SavedData {
    private static final String DATA_NAME = EnchantmentExpansion.MODID + "_mob_buff_choice";
    private static final String TAG_PROMPTED = "Prompted";
    private static final String TAG_ENABLED = "Enabled";

    private boolean prompted;
    private boolean enabled;

    private MobBuffWorldState() {
        this.enabled = true;
    }

    private MobBuffWorldState(CompoundTag tag) {
        this.prompted = tag.getBoolean(TAG_PROMPTED);
        this.enabled = !tag.contains(TAG_ENABLED) || tag.getBoolean(TAG_ENABLED);
    }

    public static MobBuffWorldState get(ServerLevel level) {
        MobBuffWorldState state = level.getDataStorage().computeIfAbsent(
                MobBuffWorldState::new,
                MobBuffWorldState::new,
                DATA_NAME);
        MobBuffRuntime.setEnabled(
                state.isPrompted() ? state.isEnabled() : MobBuffConfig.enabled);
        return state;
    }

    public boolean isPrompted() {
        return prompted;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void choose(boolean enabled) {
        this.prompted = true;
        this.enabled = enabled;
        MobBuffRuntime.setEnabled(enabled);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(TAG_PROMPTED, this.prompted);
        tag.putBoolean(TAG_ENABLED, this.enabled);
        return tag;
    }
}
