package com.thelameres.islandworld.worldgen.density

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Interval
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Маска острова: 1.0 внутри, 0.0 снаружи, smoothstep на границе.
 *
 * Радиус искажается 2D-шумом, поэтому берег получается неровным.
 *
 * API 26.3: DensityFunction больше не имеет compute(); вместо этого
 * функция компилируется в DensitySampler, который движок вызывает
 * по объёму (sampleVolume) и по точке (sampleValue).
 */
class IslandMaskDensityFunction(
	private val noise: DensityFunction,
	private val islandRadius: Double,
	private val edgeSoftness: Double,
	private val noiseAmplitude: Double
) : DensityFunction {

	override fun compileSampler(context: DensityFunction.CompileContext): DensitySampler =
		MaskSampler(noise.compileSampler(context), islandRadius, edgeSoftness, noiseAmplitude)

	// См. комментарий в ScaledClimateDensityFunction: без вызова rule.rewrite(this).
	override fun rewriteChildren(rule: DfRewriteRule): DensityFunction =
		IslandMaskDensityFunction(rule.rewrite(noise), islandRadius, edgeSoftness, noiseAmplitude)

	override fun range(): Interval = Interval.of(0.0f, 1.0f)

	/** Маска зависит от X и Z; по Y — только если шум сам зависит от Y. */
	override fun domainAxes(): Int =
		noise.domainAxes() or DensityFunction.AXIS_X or DensityFunction.AXIS_Z

	override fun codec(): MapCodec<out DensityFunction> = CODEC

	private class MaskSampler(
		private val noise: DensitySampler,
		private val islandRadius: Double,
		private val edgeSoftness: Double,
		private val noiseAmplitude: Double
	) : DensitySampler {

		override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float {
			val radius = islandRadius + noise.sampleValue(context, x, y, z) * noiseAmplitude
			val distance = sqrt(x.toDouble() * x + z.toDouble() * z)
			val normalized = (distance - radius) / edgeSoftness
			return (1.0 - smoothstep(normalized)).toFloat()
		}

		override fun sampleVolume(context: SamplerContext, buffer: DensityBuffer, volume: DensityVolume) {
			DensitySampler.sampleVolumeNaive(context, buffer, volume, this)
		}
	}

	companion object {
		private fun smoothstep(x: Double): Double {
			val t = max(0.0, min(1.0, x))
			return t * t * (3.0 - 2.0 * t)
		}

		val CODEC: MapCodec<IslandMaskDensityFunction> = RecordCodecBuilder.mapCodec { instance ->
			instance.group(
				DensityFunction.CODEC.fieldOf("noise").forGetter { it.noise },
				Codec.DOUBLE.optionalFieldOf("island_radius", 750.0).forGetter { it.islandRadius },
				Codec.DOUBLE.optionalFieldOf("edge_softness", 100.0).forGetter { it.edgeSoftness },
				Codec.DOUBLE.optionalFieldOf("noise_amplitude", 150.0).forGetter { it.noiseAmplitude }
			).apply(instance, ::IslandMaskDensityFunction)
		}
	}
}
