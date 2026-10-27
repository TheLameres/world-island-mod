package com.thelameres.islandworld.locator

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAssets
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.*


/**
 * Состояние waypoint-метки для блок-энтити (табличка, флаг).
 * Живёт в @Unique-поле миксина; здесь только данные и логика.
 *
 * Сериализация идёт через ValueOutput/ValueInput (в 26.3 BlockEntity.saveAdditional/loadAdditional
 * принимают именно их, а не CompoundTag), чтобы метки переживали перезагрузку мира.
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

    fun bind(pos: BlockPos?) {
        this.position = pos
    }

    /**
     * Сохранение состояния. Вызывается миксином из saveAdditional в дочерний тег
     * "islandworld_waypoint"; сюда приходит уже этот дочерний ValueOutput.
     *
     * Position не сохраняем — она всегда привязывается заново в LevelChunk.setBlockEntity.
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
     * LevelChunk.setBlockEntity зарегистрирует передатчик в WaypointManager,
     * поэтому isEnabled успевает восстановиться к моменту trackWaypoint.
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
     * Включает/выключает метку. Вызывающий сам делает
     * trackWaypoint/untrackWaypoint на ServerWaypointManager.
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

    /** Соединение с конкретным игроком; null, если метка выключена.  */
    fun connectionFor(receiver: ServerPlayer): Optional<WaypointTransmitter.Connection> {
        if (!this.isEnabled || position == null) {
            return Optional.empty<WaypointTransmitter.Connection?>()
        }
        // Далёкие чанки не грузим: ванильный LocatorBar сам шлёт «далеко» вариант для LivingEntity,
        // для блока достаточно не показывать метку, пока чанк не загружен у игрока.
        if (!WaypointTransmitter.isChunkVisible(ChunkPos.containing(position!!), receiver)) {
            return Optional.of<WaypointTransmitter.Connection>(
                BlockWaypointConnection(
                    id,
                    icon,
                    position!!,
                    receiver,
                    false
                )
            )
        }
        return Optional.of<WaypointTransmitter.Connection?>(
            BlockWaypointConnection(
                id,
                icon,
                position!!,
                receiver,
                true
            )
        )
    }
}
