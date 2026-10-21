package com.thelameres.islandworld.worldgen.density

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.Interval
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext

/**
 * Подсовывает вложенной функции координаты X/Z, умноженные на scale.
 * Климатические поля «сжимаются», и на острове 1500x1500 помещается
 * больше биомов.
 */
class ScaledClimateDensityFunction(
	private val wrapped: DensityFunction,
	private val scale: Double
) : DensityFunction {

	override fun compileSampler(context: DensityFunction.CompileContext): DensitySampler =
		Sampler(wrapped.compileSampler(context), scale)

	// Компилятор сам вызывает rule.rewrite() на этом узле и на потомках.
	// Здесь нельзя снова вызывать rule.rewrite(this), иначе получается бесконечная рекурсия.
	override fun rewriteChildren(rule: DfRewriteRule): DensityFunction =
		ScaledClimateDensityFunction(rule.rewrite(wrapped), scale)

	override fun range(): Interval = wrapped.range()

	override fun domainAxes(): Int = wrapped.domainAxes()

	override fun codec(): MapCodec<out DensityFunction> = CODEC

	private class Sampler(
		private val wrapped: DensitySampler,
		private val scale: Double
	) : DensitySampler {

		override fun sampleValue(context: SamplerContext, x: Int, y: Int, z: Int): Float =
			wrapped.sampleValue(context, (x * scale).toInt(), y, (z * scale).toInt())

		override fun sampleVolume(context: SamplerContext, buffer: DensityBuffer, volume: DensityVolume) {
			DensitySampler.sampleVolumeNaive(context, buffer, volume, this)
		}
	}

	companion object {
		val CODEC: MapCodec<ScaledClimateDensityFunction> = RecordCodecBuilder.mapCodec { instance ->
			instance.group(
				DensityFunction.CODEC.fieldOf("wrapped").forGetter { it.wrapped },
				Codec.DOUBLE.optionalFieldOf("scale", 4.0).forGetter { it.scale }
			).apply(instance, ::ScaledClimateDensityFunction)
		}
	}
}
