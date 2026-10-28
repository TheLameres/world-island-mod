package com.thelameres.islandworld.hallucination

import com.thelameres.islandworld.IslandWorldMod
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Пакет сервер -> клиент с дескриптором стадии (план 4.4).
 *
 * Сетевой кодек выведен из HallucinationMessage.CODEC через ByteBufCodecs.fromCodec,
 * поэтому формат на проводе и в конфиге описан одним кодеком.
 */
class HallucinationPayload(val message: HallucinationMessage) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val ID: Identifier = IslandWorldMod.id("hallucination")
        val TYPE: CustomPacketPayload.Type<HallucinationPayload> = CustomPacketPayload.Type(ID)

        private val MESSAGE_STREAM: StreamCodec<ByteBuf, HallucinationMessage> =
            ByteBufCodecs.fromCodec(HallucinationMessage.CODEC)

        /** Регистрируется через [com.thelameres.islandworld.network.PayloadRegistry], не самостоятельно. */
        val CODEC: StreamCodec<ByteBuf, HallucinationPayload> = StreamCodec.composite(
            MESSAGE_STREAM,
            HallucinationPayload::message,
            ::HallucinationPayload,
        )
    }
}
