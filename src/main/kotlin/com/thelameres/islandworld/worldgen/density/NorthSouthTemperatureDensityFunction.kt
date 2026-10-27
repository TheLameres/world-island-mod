package com.thelameres.islandworld.worldgen.density

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Interval
import net.minecraft.world.level.levelgen.densityfunction.*
import kotlin.math.max
import kotlin.math.min

/**
 * Температура как линейный градиент по Z: север (coldZ) → -1.0, юг (hotZ) → +1.0.
 * Не зависит от X и Y.
 */
class NorthSouthTemperatureDensityFunction(
    private val coldZ: Double,
    private val hotZ: Double
) : DensityFunction {

    override fun compileSampler(context: DensityFunction.CompileContext): DensitySampler = Sampler(coldZ, hotZ)

    override fun rewriteChildren(rule: DfRewriteRule): DensityFunction = this

    override fun range(): Interval = Interval.of(-1.0f, 1.0f)

    override fun domainAxes(): Int = DensityFunction.AXIS_Z

    override fun codec(): MapCodec<out DensityFunction> = CODEC

    private class Sampler(private val coldZ: Double, private val hotZ: Double) : DensitySampler {

        override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float {
            val t = max(0.0, min(1.0, (z - coldZ) / (hotZ - coldZ)))
            return (t * 2.0 - 1.0).toFloat()
        }

        override fun sampleVolume(context: SamplerContext, buffer: DensityBuffer, volume: DensityVolume) {
            DensitySampler.sampleVolumeNaive(context, buffer, volume, this)
        }
    }

    companion object {
        val CODEC: MapCodec<NorthSouthTemperatureDensityFunction> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.DOUBLE.optionalFieldOf("cold_z", -750.0).forGetter { it.coldZ },
                Codec.DOUBLE.optionalFieldOf("hot_z", 750.0).forGetter { it.hotZ }
            ).apply(instance, ::NorthSouthTemperatureDensityFunction)
        }
    }
}
