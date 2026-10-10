package com.github.emberstar1201.enchantmentex.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 丧尸娘营地坐标存档：每次营地模板成功放置后记录原点，
 * 供 /ee camp locate 指令查询最近营地。随维度存档持久保存。
 */
public final class ZombieGirlCampSavedData extends SavedData {
    private static final String DATA_NAME = "enchantment_expansion_zombie_girl_camps";
    private static final String TAG_POSITIONS = "Positions";

    private final List<BlockPos> camps = new ArrayList<>();

    public static ZombieGirlCampSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ZombieGirlCampSavedData::load, ZombieGirlCampSavedData::new, DATA_NAME);
    }

    private static ZombieGirlCampSavedData load(CompoundTag tag) {
        ZombieGirlCampSavedData data = new ZombieGirlCampSavedData();
        ListTag list = tag.getList(TAG_POSITIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.camps.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : camps) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", pos.getX());
            entry.putInt("Y", pos.getY());
            entry.putInt("Z", pos.getZ());
            list.add(entry);
        }
        tag.put(TAG_POSITIONS, list);
        return tag;
    }

    /** 记录一个新放置的营地原点并标记存档为脏。 */
    public void addCamp(BlockPos pos) {
        camps.add(pos.immutable());
        setDirty();
    }

    /** 查询离指定点最近的已记录营地；无记录返回 null。 */
    public BlockPos findNearest(BlockPos from) {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : camps) {
            double dist = pos.distSqr(from);
            if (dist < best) {
                best = dist;
                nearest = pos;
            }
        }
        return nearest;
    }
}
