package com.thelameres.islandworld.border

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files

data class BoundaryConfig(
	@SerializedName("center_x") val centerX: Double = 0.0,
	@SerializedName("center_z") val centerZ: Double = 0.0,
	@SerializedName("warn1_radius") val warn1Radius: Double = 1800.0,
	@SerializedName("warn2_radius") val warn2Radius: Double = 1900.0,
	@SerializedName("warn3_radius") val warn3Radius: Double = 1950.0,
	@SerializedName("kill_radius") val killRadius: Double = 2000.0,
	@SerializedName("kill_delay_ticks") val killDelayTicks: Int = 160,
	@SerializedName("check_interval_ticks") val checkIntervalTicks: Int = 10,
	@SerializedName("reset_radius_margin") val resetRadiusMargin: Double = 100.0,
	@SerializedName("debug_messages") val debugMessages: Boolean = false,
)

/** Стадия галлюцинаций: эффекты в формате "name:level", например "darkness:1". */
data class StageConfig(
	val effects: List<String> = emptyList(),
)

data class IslandConfigFile(
	val boundary: BoundaryConfig = BoundaryConfig(),
	val hallucinations: Map<String, StageConfig> = linkedMapOf(
		"stage1" to StageConfig(listOf("darkness:1", "nausea:1")),
		"stage2" to StageConfig(listOf("darkness:2", "nausea:2", "slowness:1")),
		"stage3" to StageConfig(listOf("darkness:2", "nausea:3", "slowness:2")),
	),
)

object IslandConfigLoader {
	private val LOGGER = LoggerFactory.getLogger("islandworld/config")
	private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
	private const val FILE_NAME = "island_config.json"

	/** Читает config/island_config.json; при отсутствии создаёт файл со значениями по умолчанию. */
	fun load(): IslandConfigFile {
		val path = FabricLoader.getInstance().configDir.resolve(FILE_NAME)
		if (!Files.exists(path)) {
			val defaults = IslandConfigFile()
			try {
				Files.createDirectories(path.parent)
				Files.writeString(path, GSON.toJson(defaults))
				LOGGER.info("Создан файл конфигурации {}", path)
			} catch (e: Exception) {
				LOGGER.error("Не удалось создать {}", path, e)
			}
			return defaults
		}
		return try {
			GSON.fromJson(Files.readString(path), IslandConfigFile::class.java) ?: IslandConfigFile()
		} catch (e: Exception) {
			LOGGER.error("Ошибка чтения {}, используются значения по умолчанию", path, e)
			IslandConfigFile()
		}
	}
}
