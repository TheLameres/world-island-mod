package com.thelameres.islandworld.locator

import net.minecraft.core.BlockPos
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAssets
import java.util.*


/**
 * Состояние waypoint-метки, привязанное к конкретной табличке/флагу.
 * Живёт в @Unique-поле миксина; здесь только данные и сериализация.
 *
 * Передатчиком для менеджера этот класс НЕ является — см. [RegistryWaypoint].
 * Здесь хранится только «паспорт» точки: включена ли она, её UUID, имя и цвет.
 */
class BlockWaypointState(
    var isEnabled: Boolean = false,
    private var id: UUID = UUID.randomUUID(),
    private var position: BlockPos? = null,
    private val icon: Waypoint.Icon = Waypoint.Icon(),
    var name: String? = null,
) {
    /** Метка включается вручную (командой/взаимодействием), чтобы не каждая табличка была точкой.  */

    init {
        this.icon.style = WaypointStyleAssets.DEFAULT
    }

    fun id(): UUID {
        return id
    }

    fun position(): BlockPos? {
        return position
    }

    fun icon(): Waypoint.Icon {
        return icon
    }

    fun nameOrNull(): String? {
        return name
    }

    fun getColorRgb(): Int? {
        return icon.color.orElse(null)
    }

    fun bind(pos: BlockPos?) {
        this.position = pos
    }

    /**
     * Сохранение состояния. Вызывается миксином из saveAdditional в дочерний тег
     * "islandworld_waypoint"; сюда приходит уже этот дочерний ValueOutput.
     *
     * position не сохраняем — она всегда восстанавливается из blockPos самого блока.
     */
    fun writeTo(out: ValueOutput) {
        out.putBoolean("enabled", isEnabled)
        // UUID как строка: так он читается без Codec и переживает перезапуск,
        // сохраняя идентичность метки на клиенте.
        out.putString("id", id.toString())
        name?.let { out.putString("name", it) }
        icon.color.ifPresent { rgb -> out.putInt("color", rgb) }
    }

    /**
     * Загрузка состояния. Вызывается миксином из loadAdditional до того, как
     * LevelChunk.setBlockEntity восстановит точку в реестре, поэтому isEnabled
     * успевает прочитаться раньше, чем произойдёт trackWaypoint.
     */
    fun readFrom(input: ValueInput) {
        isEnabled = input.getBooleanOr("enabled", false)
        if (!isEnabled) {
            return
        }

        val rawId = input.getStringOr("id", "")
        id = if (rawId.isEmpty()) {
            UUID.randomUUID()
        } else {
            try {
                UUID.fromString(rawId)
            } catch (e: IllegalArgumentException) {
                // Битый UUID в сохранении — не роняем загрузку чанка, просто выдаём новый
                UUID.randomUUID()
            }
        }

        name = input.getString("name").orElse(null)
        // Пусто, если тег отсутствует — цвет вернётся к дефолтному стилю
        icon.color = input.getInt("color")
    }

    /**
     * Включает/выключает метку. Вызывающий сам синхронизирует реестр
     * и делает trackWaypoint/untrackWaypoint на ServerWaypointManager.
     */
    fun setEnabled(value: Boolean): Boolean {
        if (this.isEnabled == value) {
            return false
        }
        this.isEnabled = value
        if (value) {
            this.id = UUID.randomUUID()
        }
        return true
    }

    fun setColor(rgb: Int?) {
        this.icon.color = Optional.ofNullable(rgb)
    }
}
