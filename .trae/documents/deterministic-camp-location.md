# 营地确定性定位系统方案

## 背景

当前营地位置由 `level.random.nextDouble()` 随机决定，只有区块加载后才知道是否有营地。`/ee locate camp` 指令依赖 `ZombieGirlCampSavedData` 中已记录的营地，未加载区块中的营地无法定位。

用户要求：指令能在未加载区块中直接定位营地，且不能崩溃。

## 方案：确定性哈希 + BiomeSource 免区块加载查询

### 核心思路

用世界种子 + 区块坐标做确定性哈希替代 `level.random`，使同一世界种子的营地分布固定不变。指令通过数学计算即可预测营地位置，无需加载区块。

### 改动文件

#### 1. ZombieGirlCampHandler.java（主要改动）

**确定性哈希函数**（替代 `level.random.nextDouble()`）：
```java
// 使用世界种子和区块坐标计算确定性哈希
private static boolean shouldHaveCamp(long worldSeed, int chunkX, int chunkZ) {
    long hash = worldSeed;
    hash = hash * 128712L + chunkX;
    hash = hash * 987541L + chunkZ;
    double normalized = (double) (hash & 0xFFFFFF) / (double) 0xFFFFFF;
    return normalized < Config.zombieGirlCampSpawnChance;
}
```

**免区块加载的生物群系检查**（替代 `level.getBiome(pos)`）：
```java
// BiomeSource.getNoiseBiome 不加载区块，直接通过噪声计算群系
private static boolean isPlainsBiomeNoChunkLoad(ServerLevel level, int blockX, int blockZ) {
    var biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
    Holder<Biome> biome = biomeSource.getNoiseBiome(blockX >> 2, 64 >> 2, blockZ >> 2);
    // 检查 ResourceLocation 是否为平原类
}
```

**onChunkLoad 改为调用确定性哈希**：删除 `level.random.nextDouble()`，改用 `shouldHaveCamp(level.getSeed(), chunkPos.x, chunkPos.z)`。

#### 2. CampLocateCommand.java（指令搜索逻辑重写）

**locate 方法改为确定性搜索**：
- 不再调用 `ZombieGirlCampSavedData.findNearest()`
- 在玩家周围 10000 格（625 区块）半径内，按 125 区块网格扫描
- 每个网格点：`shouldHaveCamp()` 判定 + `isPlainsBiomeNoChunkLoad()` 群系检查
- 返回最近的匹配坐标
- 扫描量：21×21 = 441 个网格点，纯数学计算，无区块加载，不崩溃

**Y 坐标处理**：指令返回的 Y 固定为 64（地面高度在区块加载时由 `getHeight` 确定，指令中不需要精确 Y）。实际传送后玩家会落到地面。

#### 3. ZombieGirlCampSavedData.java（保留但降级）

保留 `addCamp` 用于记录已生成营地（供调试/其他功能），但指令不再依赖它。可以保留 `findNearest` 作为后备。

### 不改动的部分

- 延迟执行队列（PENDING / onServerTick / MAX_PER_TICK）保持不变
- `tryPlaceCamp` 中的结构放置逻辑保持不变
- `ATTEMPTED_CHUNKS` 防重复机制保持不变
- Config 中概率配置保持不变

### 安全性保证

- `BiomeSource.getNoiseBiome()` 是纯计算方法，不触发区块加载
- 指令搜索只做数学运算和 BiomeSource 查询，不调用 `level.getChunk()` / `level.getBlockEntity()`
- 营地实际生成仍在 `ServerTickEvent` 中延迟执行，无递归死锁风险

## 验证

1. 编译通过
2. 进游戏新建存档，执行 `/ee locate camp`，应返回一个平原坐标
3. 传送到该坐标，确认区块加载后营地生成
4. 多次执行指令，同一世界种子结果一致
5. 进入旧存档不卡死
