package com.thelameres.islandworld.worldgen.structure

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.thelameres.islandworld.IslandWorldMod
import com.thelameres.islandworld.core.Registrable
import net.minecraft.core.HolderSet
import net.minecraft.core.Registry
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.core.registries.codec.RegistryCodecs
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement
import java.util.*
import kotlin.math.sqrt

/**
 * Концентрические кольца стронгхолдов, которые спавнятся только в океане (план, раздел 3.1).
 *
 * Выбор океана делает сам движок: кольцевые позиции ищутся через [preferredBiomes]
 * (тег `#minecraft:is_ocean`). Здесь дополнительно отсекаются чанки внутри острова
 * (радиус < [islandRadius]) и далеко за пределами (радиус > [maxRadius]).
 *
 * Радиусы задаются в блоках от точки (0, 0), как и остальные параметры острова.
 */
class OceanConcentricRingsPlacement(
    locateOffset: Vec3i,
    frequencyReductionMethod: FrequencyReductionMethod,
    frequency: Float,
    salt: Int,
    exclusionZone: Optional<ExclusionZone>,
    distance: Int,
    spread: Int,
    count: Int,
    preferredBiomes: HolderSet<Biome>,
    val islandRadius: Int,
    val maxRadius: Int,
) : ConcentricRingsStructurePlacement(
    locateOffset,
    frequencyReductionMethod,
    frequency,
    salt,
    exclusionZone,
    distance,
    spread,
    count,
    preferredBiomes,
) {

    override fun isPlacementChunk(state: ChunkGeneratorStructureState, chunkX: Int, chunkZ: Int): Boolean {
        // Центр чанка в блоках, чтобы не отсекать чанки на границе радиуса по углу
        val blockX = (chunkX shl 4) + 8
        val blockZ = (chunkZ shl 4) + 8
        val dist = sqrt(blockX.toDouble() * blockX + blockZ.toDouble() * blockZ)

        if (dist < islandRadius || dist > maxRadius) return false

        return super.isPlacementChunk(state, chunkX, chunkZ)
    }

    override fun codec(): MapCodec<ConcentricRingsStructurePlacement> = CODEC

    companion object : Registrable {
        const val DEFAULT_ISLAND_RADIUS: Int = 800
        const val DEFAULT_MAX_RADIUS: Int = 1900

        /**
         * Кодек типизирован на подкласс; для [codec] он приводится к базовому типу.
         * Тип безопасен: в реестр попадают только экземпляры [OceanConcentricRingsPlacement].
         */
        @Suppress("UNCHECKED_CAST")
        val CODEC: MapCodec<ConcentricRingsStructurePlacement> =
            RecordCodecBuilder.mapCodec<OceanConcentricRingsPlacement> { instance ->
                AbstractSpreadingStructurePlacement.placementCodec(instance)
                    .and(
                        Codec.intRange(0, 1023).fieldOf("distance")
                            .forGetter<OceanConcentricRingsPlacement> { it.distance() })
                    .and(
                        Codec.intRange(0, 1023).fieldOf("spread")
                            .forGetter<OceanConcentricRingsPlacement> { it.spread() })
                    .and(
                        Codec.intRange(1, 4095).fieldOf("count")
                            .forGetter<OceanConcentricRingsPlacement> { it.count() })
                    .and(
                        RegistryCodecs.holderSet(Registries.BIOME).fieldOf("preferred_biomes")
                            .forGetter<OceanConcentricRingsPlacement> { it.preferredBiomes() })
                    .and(
                        Codec.intRange(0, 30_000_000).optionalFieldOf("island_radius", DEFAULT_ISLAND_RADIUS)
                            .forGetter<OceanConcentricRingsPlacement> { it.islandRadius })
                    .and(
                        Codec.intRange(0, 30_000_000).optionalFieldOf("max_radius", DEFAULT_MAX_RADIUS)
                            .forGetter<OceanConcentricRingsPlacement> { it.maxRadius })
                    .apply(instance) { locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone,
                                       distance, spread, count, preferredBiomes, islandRadius, maxRadius ->
                        OceanConcentricRingsPlacement(
                            locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone,
                            distance, spread, count, preferredBiomes, islandRadius, maxRadius,
                        )
                    }
            } as MapCodec<ConcentricRingsStructurePlacement>


        override fun register() {
            Registry.register(
                BuiltInRegistries.STRUCTURE_PLACEMENT,
                IslandWorldMod.id("ocean_concentric_rings"),
                CODEC,
            )
        }
    }
}
