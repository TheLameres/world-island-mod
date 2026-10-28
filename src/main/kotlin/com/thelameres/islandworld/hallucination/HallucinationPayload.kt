package com.thelameres.islandworld.hallucination

import com.thelameres.islandworld.IslandWorldMod
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Пакет сервер → клиент: готовый дескриптор стадии галлюцинаций (план 4.4, решение 3).
 * Клиент только исполняет дескриптор; конфиг и решения остаются на сервере.
 *
 * Тело — [HallucinationMessage.encode] одним byte-array: формат живёт в одном месте и тестируется без Minecraft.
 */
class HallucinationPayload(val message: HallucinationMessage) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: Identifier = IslandWorldMod.id("hallucination")
        val TYPE: CustomPacketPayload.Type<HallucinationPayload> = CustomPacketPayload.Type(ID)

        /**
         * Неверные байты превращаются в reset(), а не в исключение: пакет от сервера не должен ронять клиент.
         */
        val CODEC: StreamCodec<FriendlyByteBuf, HallucinationPayload> = StreamCodec.of(
            { buf, payload -> buf.writeByteArray(payload.message.encode()) },
            { buf -> HallucinationPayload(HallucinationMessage.decode(buf.readByteArray()) ?: HallucinationMessage.reset()) },
        )

        /** Регистрация типа на клиентской стороне; сервер регистрирует тот же тип для отправки. */
        fun registerType() {
            PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC)
        }
    }
}
