package com.github.emberstar1201.enchantmentex.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 丧尸娘营地坐标存档：每次营地模板成功放置后记录原点，
 * 供 /ee camp locate 指令查询最近营地。随维度存档持久保存。
 */
public final class ZombieGirlCampSavedData extends SavedData {
    private static final String DATA_NAME = "enchantment_expansion_zombie_girl_camps";
    private static final String TAG_POSITIONS = "Positions";
    private static final String TAG_CHUNKS = "GeneratedChunks";
    private static final String TAG_IN_PROGRESS = "InProgress";
    private static final String TAG_STARTER_CAMP = "StarterCampPlaced";

    private final List<BlockPos> camps = new ArrayList<>();
    private final Set<Long> generatedChunks = new HashSet<>();
    /** 保存候选区块与原点，崩溃后可从头幂等重放，且避免高度图因半成品改变锚点。 */
    private final java.util.Map<Long, BlockPos> inProgress = new java.util.HashMap<>();
    /** 出生点保底新手营地是否已放置；保证全存档唯一，防止重复触发。 */
    private boolean starterCampPlaced;

    public static ZombieGirlCampSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ZombieGirlCampSavedData::load, ZombieGirlCampSavedData::new, DATA_NAME);
    }

    private static ZombieGirlCampSavedData load(CompoundTag tag) {
        ZombieGirlCampSavedData data = new ZombieGirlCampSavedData();
        ListTag list = tag.getList(TAG_POSITIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            data.camps.add(pos);
            // 兼容旧存档：旧版本只保存原点，用原点所在区块迁移去重记录。
            data.generatedChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        long[] chunks = tag.getLongArray(TAG_CHUNKS);
        for (long chunk : chunks) {
            data.generatedChunks.add(chunk);
        }
        ListTag pending = tag.getList(TAG_IN_PROGRESS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            long chunk = entry.getLong("Chunk");
            data.inProgress.put(chunk,
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        data.starterCampPlaced = tag.getBoolean(TAG_STARTER_CAMP);
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
        tag.putLongArray(TAG_CHUNKS, generatedChunks.stream().mapToLong(Long::longValue).toArray());
        ListTag pending = new ListTag();
        inProgress.forEach((chunk, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Chunk", chunk);
            entry.putInt("X", pos.getX());
            entry.putInt("Y", pos.getY());
            entry.putInt("Z", pos.getZ());
            pending.add(entry);
        });
        tag.put(TAG_IN_PROGRESS, pending);
        tag.putBoolean(TAG_STARTER_CAMP, starterCampPlaced);
        return tag;
    }

    /** 查询指定位置是否已经记录过营地。 */
    public boolean hasCamp(BlockPos pos) {
        return camps.contains(pos);
    }

    /** 查询候选区块是否已经成功生成过营地。 */
    public boolean hasGeneratedChunk(ChunkPos chunkPos) {
        return generatedChunks.contains(ChunkPos.asLong(chunkPos.x, chunkPos.z));
    }

    public BlockPos getInProgress(ChunkPos chunkPos) {
        return inProgress.get(ChunkPos.asLong(chunkPos.x, chunkPos.z));
    }

    public void markInProgress(ChunkPos chunkPos, BlockPos origin) {
        long key = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        if (!generatedChunks.contains(key)) {
            inProgress.put(key, origin.immutable());
            setDirty();
        }
    }

    public void clearInProgress(ChunkPos chunkPos) {
        if (inProgress.remove(ChunkPos.asLong(chunkPos.x, chunkPos.z)) != null) {
            setDirty();
        }
    }

    /** 记录一个新放置的营地原点和候选区块，并标记存档为脏。 */
    public void addCamp(BlockPos pos, ChunkPos chunkPos) {
        long key = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        boolean changed = generatedChunks.add(key);
        changed |= inProgress.remove(key) != null;
        if (!hasCamp(pos)) {
            camps.add(pos.immutable());
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    /** 保留旧调用接口，避免其他代码失效。 */
    public void addCamp(BlockPos pos) {
        addCamp(pos, new ChunkPos(pos));
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

    /** 出生点保底新手营地是否已经放置成功。 */
    public boolean isStarterCampPlaced() {
        return starterCampPlaced;
    }

    /** 标记新手营地已放置；此后服务器启动不再触发保底搜索。 */
    public void markStarterCampPlaced() {
        if (!starterCampPlaced) {
            starterCampPlaced = true;
            setDirty();
        }
    }
}
