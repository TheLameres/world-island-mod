package com.thelameres.islandworld.locator

import com.thelameres.islandworld.IslandWorldMod
import com.thelameres.islandworld.core.PlayerScoped
import com.thelameres.islandworld.core.Registrable
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import java.util.*

/**
 * Серверная часть нативного LocatorBar.
 *
 * Управляет атрибутами ванильного локатора:
 *  - `waypoint_receive_range` обнуляется без компаса в руке (main/off hand);
 *  - `waypoint_transmit_range` обнуляется, если на игроке голова `PLAYER_HEAD`.
 *
 * Своего HUD и сетевого канала у мода нет: список игроков строит движок
 * из атрибутов, отрисовывает ваниль.
 *
 * ВАЖНО про респавн: при смерти создаётся НОВЫЙ объект ServerPlayer, а
 * `ServerPlayer.restoreFrom` переносит только базовые значения
 * (`AttributeMap.assignBaseValues`) и постоянные модификаторы
 * (`assignPermanentModifiers`). Временные модификаторы
 * (`addTransientModifier`), которыми мы обнуляем диапазоны, НЕ переносятся.
 * Поэтому после смерти у нового игрока атрибуты девственно чистые, а
 * `lastAttributeState` помнит старое состояние по UUID и считает, что
 * применять нечего. Отсюда и «залипший» LocatorBar с точками.
 *
 * Лечится двумя вещами: сбрасывать кэш при респавне и не доверять кэшу,
 * а сверяться с фактическим наличием модификатора на атрибуте.
 */
object CompassLocator : Registrable, PlayerScoped {

    private val RECEIVE_HIDDEN_ID: Identifier =
        Identifier.fromNamespaceAndPath(IslandWorldMod.MOD_ID, "locator_receive_hidden")
    private val TRANSMIT_HIDDEN_ID: Identifier =
        Identifier.fromNamespaceAndPath(IslandWorldMod.MOD_ID, "locator_transmit_hidden")

    /** ADD_MULTIPLIED_TOTAL с -1 даёт итоговое значение 0: значение *= (1 + (-1)). */
    private const val ZERO_MULTIPLIER = -1.0

    /**
     * Последнее применённое состояние атрибутов (receive, transmit) по UUID.
     * Кэш нужен, чтобы не трогать атрибуты каждый тик: их изменение рассылается клиентам.
     */
    private val lastAttributeState: MutableMap<UUID, Pair<Boolean, Boolean>> = mutableMapOf()

    override fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            syncWaypointAttributes(server)
        }

        // После смерти создаётся новый ServerPlayer без наших временных модификаторов
        // атрибутов, поэтому кэш по UUID надо сбросить — иначе бар останется с точками
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            forget(newPlayer.uuid)
        }
    }

    /**
     * Снимок кэша lastAttributeState для диагностики: игрок -> (receive, transmit).
     * Возвращается копия, поэтому вызывающий не может изменить состояние подсистемы.
     * Записи могут относиться к игрокам, которые уже вышли: очистка идёт на DISCONNECT,
     * а не на уровне сессии, поэтому снимок честно показывает то, что реально в памяти.
     */
    fun attributeSnapshot(): Map<UUID, Pair<Boolean, Boolean>> {
        return lastAttributeState.toMap()
    }

    /**
     * Сброс кэша: после респавна это новый объект игрока, и его атрибуты не содержат
     * наших временных модификаторов, даже если кэш помнит обратное. Без сброса состояние
     * «был с компасом» сохранялось бы, и бар оставался бы видимым.
     */
    override fun forget(uuid: UUID) {
        lastAttributeState.remove(uuid)
    }

    /**
     * Атрибуты нативного LocatorBar: обходим всех игроков сервера, включая тех,
     * кто в PLAYER_HEAD.
     */
    private fun syncWaypointAttributes(server: MinecraftServer) {
        val players = server.playerList.players
        lastAttributeState.keys.retainAll(players.map { it.uuid }.toSet())

        players.forEach { player ->
            val receiveVisible = player.isHoldingCompass()
            val transmitVisible = !player.isWearingPlayerHead()
            val state = receiveVisible to transmitVisible

            val receiveAttr = player.getAttribute(Attributes.WAYPOINT_RECEIVE_RANGE)
            val transmitAttr = player.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE)

            // Кэшу доверяем только если он совпадает с фактическим состоянием атрибутов.
            // Иначе (например, после респавна или пересоздания игрока) применяем заново.
            if (lastAttributeState[player.uuid] == state
                && receiveAttr.modifiersMatch(RECEIVE_HIDDEN_ID, receiveVisible)
                && transmitAttr.modifiersMatch(TRANSMIT_HIDDEN_ID, transmitVisible)
            ) {
                return@forEach
            }

            receiveAttr.applyModifier(RECEIVE_HIDDEN_ID, receiveVisible)
            transmitAttr.applyModifier(TRANSMIT_HIDDEN_ID, transmitVisible)
            lastAttributeState[player.uuid] = state
        }
    }


    private fun Player.isHoldingCompass(): Boolean =
        mainHandItem.`is`(Items.COMPASS) || offhandItem.`is`(Items.COMPASS)

    private fun Player.isWearingPlayerHead(): Boolean =
        getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD)

    /** true — атрибут уже в нужном состоянии: скрывающий модификатор есть/отсутствует как надо. */
    private fun AttributeInstance?.modifiersMatch(id: Identifier, visible: Boolean): Boolean {
        this ?: return true
        val hasModifier = getModifier(id) != null
        // visible = true → модификатора быть не должно; visible = false → должен быть
        return hasModifier != visible
    }

    /** Ставит или снимает скрывающий модификатор: [visible] = false обнуляет диапазон. */
    private fun AttributeInstance?.applyModifier(id: Identifier, visible: Boolean) {
        this ?: return
        removeModifier(id)
        if (!visible) {
            addTransientModifier(
                AttributeModifier(id, ZERO_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            )
        }
    }
}
