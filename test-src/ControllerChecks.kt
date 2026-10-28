import com.thelameres.islandworld.client.hallucination.HallucinationController
import com.thelameres.islandworld.hallucination.HallucinationMessage

// Проверки контроллера (план 4.4, шесть правил сброса и идемпотентность). Без JUnit.
object CtrlCheck {
    var failures = 0
    var checks = 0
}

fun ctrlCheck(name: String, cond: Boolean) {
    CtrlCheck.checks++
    if (!cond) {
        CtrlCheck.failures++
        println("FAIL: $name")
    }
}

fun ctrlMsg(stage: Int, dur: Int = 300, post: String? = "islandworld:vignette_light", shake: Boolean = false) =
    HallucinationMessage(stage, 0.5f, post, shake, dur)

fun controllerChecks() {
    // --- Контроллер: сброс ---
    run {
        val c = HallucinationController()
        c.onMessage(ctrlMsg(1))
        ctrlCheck("stage 0 сбрасывает", c.onMessage(HallucinationMessage.reset()) && c.current == null)
    }
    run {
        val c = HallucinationController()
        c.onMessage(ctrlMsg(1, dur = 3))
        repeat(2) { ctrlCheck("тик до истечения", !c.tick()) }
        ctrlCheck("истечение снимает эффект", c.tick() && c.current == null)
    }
    run {
        val c = HallucinationController()
        c.onMessage(ctrlMsg(1)); ctrlCheck("смерть", c.onPlayerDied() && c.current == null)
    }
    run {
        val c = HallucinationController()
        c.onMessage(ctrlMsg(1)); ctrlCheck("смена измерения", c.onDimensionChanged() && c.current == null)
    }
    run {
        val c = HallucinationController()
        c.onMessage(ctrlMsg(1)); ctrlCheck("выход из мира", c.onDisconnect() && c.current == null)
    }
    run {
        val c = HallucinationController()
        c.onMessage(ctrlMsg(1))
        c.tick(); c.tick()
        ctrlCheck("ресурспак пересоздаёт эффект", c.onResourcesReloaded() && c.current?.ageTicks == 0)
        ctrlCheck("сброс без эффекта — no-op", HallucinationController().onPlayerDied() == false)
    }

    // --- Идемпотентность ---
    run {
        val c = HallucinationController()
        c.onMessage(ctrlMsg(2, dur = 100))
        repeat(50) { c.tick() }
        val changed = c.onMessage(ctrlMsg(2, dur = 100))
        ctrlCheck("повтор той же стадии не меняет дескриптор", !changed)
        ctrlCheck("повтор перезапускает таймер", c.current?.ageTicks == 0)
        ctrlCheck("повтор не копит силу", c.current?.message?.intensity == 0.5f)
    }


    println("controller checks=${CtrlCheck.checks} failures=${CtrlCheck.failures}")
    if (CtrlCheck.failures > 0) kotlin.system.exitProcess(1)
}
