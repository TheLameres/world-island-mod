package com.thelameres.islandworld.client.hallucination

import com.thelameres.islandworld.client.shake.CameraShake
import com.thelameres.islandworld.core.Registrable
import com.thelameres.islandworld.hallucination.HallucinationMessage
import com.thelameres.islandworld.hallucination.HallucinationPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory

/**
 * Клиентская сторона галлюцинаций (план 4.4): приёмник дескриптора и применение ванильных пост-эффектов.
 *
 * Minecraft-часть тонкая: решения принимает [HallucinationController], здесь только применяем результат.
 * Пост-эффекты 26.3 не загружаются собственным кодом: мы выставляем список идентификаторов
 * через LocalPlayer.setActivePostEffects, а JSON лежат в assets/islandworld/post_effect (патч 0005).
 *
 * Ползунок ванили options.screenEffectScale на 0 полностью отключает визуал: эффект не применяется.
 */
object HallucinationClient: Registrable {
    private val LOGGER = LoggerFactory.getLogger("islandworld/client/hallucination")
    private val controller = HallucinationController()

    override fun register() {
        ClientPlayNetworking.registerGlobalReceiver(HallucinationPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute { onMessage(payload.message) }
        }

        // Тик клиента: истечение таймера и снятие эффекта
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            if (controller.tick()) apply(null)
            updateShake()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            if (controller.onDisconnect()) apply(null)
        }

        // Перезагрузка ресурсов сбрасывает список пост-эффектов клиента; переприменяем, если эффект активен
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            object : SimpleSynchronousResourceReloadListener {
                override fun getFabricId(): Identifier = Identifier.fromNamespaceAndPath("islandworld", "hallucination_reload")
                override fun onResourceManagerReload(manager: ResourceManager) {
                    if (controller.onResourcesReloaded()) apply(controller.current?.message)
                }
            }
        )
    }

    /** Вызывается из сетевого приёмника, уже на главном потоке. */
    private fun onMessage(message: HallucinationMessage) {
        if (controller.onMessage(message)) apply(controller.current?.message)
    }

    /** Смерть и смена измерения вызываются из клиентского тика по состоянию игрока. */
    fun onPlayerDied() {
        if (controller.onPlayerDied()) apply(null)
    }

    fun onDimensionChanged() {
        if (controller.onDimensionChanged()) apply(null)
    }

    /** Тряска: амплитуда по текущему возрасту эффекта; без screen_shake или при сбросе — ноль. */
    private fun updateShake() {
        val active = controller.current
        val message = active?.message
        if (message == null || !message.screenShake) {
            CameraShake.setCurrent(0f, 0f)
            return
        }
        val screenScale = Minecraft.getInstance().options.screenEffectScale().get()
        // Ползунок «Эффекты экрана» распространяется и на тряску: при нуле смещение не применяем.
        if (screenScale <= 0.0) {
            CameraShake.setCurrent(0f, 0f)
            return
        }
        val (yaw, pitch) = CameraShake.offsetDegrees(active.ageTicks, message.intensity, message.durationTicks)
        CameraShake.setCurrent(yaw, pitch)
    }

    private fun apply(message: HallucinationMessage?) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        // Ползунок визуальных эффектов экрана на нуле: ничего не показываем, даже если сервер прислал эффект.
        val screenScale = mc.options.screenEffectScale().get()
        val postEffect = message?.postEffect?.takeIf { screenScale > 0.0 }
        player.setActivePostEffects(if (postEffect != null) listOf(Identifier.parse(postEffect)) else emptyList())
        LOGGER.debug("hallucination stage={} post={}", message?.stage ?: 0, postEffect)
    }
}
