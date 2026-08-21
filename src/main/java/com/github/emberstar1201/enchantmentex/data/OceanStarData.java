package com.github.emberstar1201.enchantmentex.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

// ========================================================================
// 「海洋之星」世界持久化数据
//
// 记录"哪些海洋神殿实例已经发放过海洋之星"，保证：
//   1. 每个海洋神殿实例仅生成一个海洋之星
//   2. 已发放的记录写入存档，重新加载/区块刷新后不会重复生成
//
// 唯一标识：StructureStart.getChunkPos() 的区块坐标（每个神殿实例唯一），
// 编码为 long 存入 LongSet。
// ========================================================================
public class OceanStarData extends SavedData {

    private static final String TAG_KEY = "HandledOceanMonuments";

    private final LongSet handledMonuments = new LongOpenHashSet();

    public static OceanStarData create() {
        return new OceanStarData();
    }

    public static OceanStarData load(CompoundTag tag) {
        OceanStarData data = new OceanStarData();
        for (long key : tag.getLongArray(TAG_KEY)) {
            data.handledMonuments.add(key);
        }
        return data;
    }

    /** 该神殿实例是否已发放过海洋之星 */
    public boolean isHandled(long key) {
        return handledMonuments.contains(key);
    }

    /** 标记该神殿实例已发放，自动置脏以便存档 */
    public void markHandled(long key) {
        handledMonuments.add(key);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(TAG_KEY, handledMonuments.toLongArray());
        return tag;
    }

    /** 获取主世界维度的海洋之星数据（存储于 level.dat 同级的 data/ 目录，持久生效） */
    public static OceanStarData get(ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(OceanStarData::load, OceanStarData::create,
                "enchantment_expansion_ocean_star");
    }
}