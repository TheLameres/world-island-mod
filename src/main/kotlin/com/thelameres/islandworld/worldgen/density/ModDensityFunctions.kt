package com.thelameres.islandworld.worldgen.density

import com.thelameres.islandworld.IslandWorldMod
import com.thelameres.islandworld.core.Registrable
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Регистрация кастомных density functions.
 *
 * Ванильный реестр minecraft:density_function_type — это реестр
 * MapCodec<out DensityFunction>, поэтому регистрируем именно кодеки.
 */
object ModDensityFunctions : Registrable {

    override fun register() {
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            IslandWorldMod.id("island_mask"),
            IslandMaskDensityFunction.CODEC
        )
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            IslandWorldMod.id("north_south_temperature"),
            NorthSouthTemperatureDensityFunction.CODEC
        )
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            IslandWorldMod.id("scaled_climate"),
            ScaledClimateDensityFunction.CODEC
        )
    }
}
