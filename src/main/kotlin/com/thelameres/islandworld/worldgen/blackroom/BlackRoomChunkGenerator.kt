package com.thelameres.islandworld.worldgen.blackroom

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.thelameres.islandworld.IslandWorldMod
import com.thelameres.islandworld.core.Registrable
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.util.random.WeightedList
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.blending.Blender
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext
import java.util.concurrent.CompletableFuture

/**
 * Генератор «Чёрной комнаты» (план, раздел 5.2).
 *
 * NoiseBasedChunkGenerator в 26.3 объявлен final, поэтому наследуемся от
 * базового ChunkGenerator (он abstract, но не final) и реализуем
 * buildTerrain напрямую: шума нет, только барьерный пол и портал.
 *
 * Чанки вне [-ROOM_HALF_CHUNKS..ROOM_HALF_CHUNKS] остаются пустыми.
 */
open class BlackRoomChunkGenerator(
    biomeSource: BiomeSource,
    private val settings: Holder<NoiseGeneratorSettings>,
) : ChunkGenerator(biomeSource) {

    override fun codec(): MapCodec<out ChunkGenerator> = CODEC

    override fun getGenDepth(): Int = 64

    override fun getSeaLevel(): Int = BlackRoom.FLOOR_Y

    override fun getMinY(): Int = 0

    override fun getBaseHeight(
        x: Int,
        z: Int,
        type: Heightmap.Types,
        level: LevelHeightAccessor,
        random: RandomState,
    ): Int = BlackRoom.FLOOR_Y + 1

    override fun getBaseColumn(
        x: Int,
        z: Int,
        level: LevelHeightAccessor,
        random: RandomState,
    ): NoiseColumn {
        val states = arrayOfNulls<BlockState>(level.height)
        states.fill(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
        return NoiseColumn(level.minY, states.requireNoNulls())
    }

    override fun addDebugScreenInfo(
        info: MutableList<String>,
        random: RandomState,
        pos: BlockPos,
        sampler: SamplerContext,
    ) {
        info.add("Black Room: floor Y=${BlackRoom.FLOOR_Y}")
    }

    override fun buildTerrain(
        chunk: ChunkAccess,
        blender: Blender,
        random: RandomState,
        structures: StructureManager,
        biomes: BiomeManager,
        region: WorldGenRegion?,
        possibleBiomes: MutableSet<Holder<Biome>>
    ): CompletableFuture<ChunkAccess> {
        val pos: ChunkPos = chunk.pos
        if (!isInsideRoom(pos)) {
            return CompletableFuture.completedFuture(chunk)
        }

        // Пол: ChunkAccess принимает мировые координаты
        val barrier: BlockState = Blocks.BARRIER.defaultBlockState()
        val baseX = pos.minBlockX
        val baseZ = pos.minBlockZ
        for (dx in 0 until 16) {
            for (dz in 0 until 16) {
                chunk.setBlockState(BlockPos(baseX + dx, BlackRoom.FLOOR_Y, baseZ + dz), barrier, 0)
            }
        }

        if (pos.x == 0 && pos.z == 0) {
            buildNetherPortal(chunk)
        }
        return CompletableFuture.completedFuture(chunk)
    }

    override fun spawnOriginalMobs(region: WorldGenRegion) {
        // Мобы в комнате запрещены (план, 5.6)
    }

    override fun getMobsAt(
        level: net.minecraft.world.level.Level,
        structures: StructureManager,
        category: MobCategory,
        pos: BlockPos,
    ): WeightedList<net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData> =
        WeightedList.of()

    /** Рамка 4×5 из обсидиана, внутри 2×3 портал, центр рамки (8, 64, 8). */
    private fun buildNetherPortal(chunk: ChunkAccess) {
        val center = BlockPos(8, 64, 8)
        for (y in 0 until 5) {
            for (x in 0 until 4) {
                val p = center.offset(x - 1, y, 0)
                val border = y == 0 || y == 4 || x == 0 || x == 3
                val state = if (border) Blocks.OBSIDIAN.defaultBlockState()
                else Blocks.NETHER_PORTAL.defaultBlockState()
                chunk.setBlockState(p, state)
            }
        }
    }

    private fun isInsideRoom(pos: ChunkPos): Boolean {
        val h = BlackRoom.ROOM_HALF_CHUNKS
        return pos.x in -h..h && pos.z in -h..h
    }

    companion object : Registrable {
        val CODEC: MapCodec<BlackRoomChunkGenerator> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                BiomeSource.CODEC.fieldOf("biome_source").forGetter { it.biomeSource },
                NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter { it.settings },
            ).apply(instance, ::BlackRoomChunkGenerator)
        }

        override fun register() {
            Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                IslandWorldMod.id("black_room_generator"),
                CODEC,
            )
        }
    }
}
