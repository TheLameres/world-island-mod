# Проработка 4.4 — клиентские эффекты границы (после проверки API 26.3)

> Статус: **реализовано в патчах 0001–0005** (ветка `feature/4.4-client-effects`, база `1aef9c7`).
> Документ фиксирует решения, принятые после сверки с клиентским jar Minecraft 26.3 и Fabric API 0.164.0+26.3.
> Предыдущая версия (`PHASE-4.4.md` в черновике) частично устарела — здесь то, что реально.

## 1. Решения

| Вопрос | Решение | Почему |
|---|---|---|
| Фейковые сущности | Выброшены | Самый ломкий кусок (рассинхрон, коллизии); эффект достигается шейдером и тряской |
| Конфиг эффектов | Серверный, клиенту уходит готовый дескриптор | Одна точка правды; стадия на сервере и клиенте не расходится |
| Звуки | Не в этой серии | Нет записей; сервер уже умеет `ClientboundSoundPacket` (4.5) |
| Пост-эффекты | Ванильный путь `setActivePostEffects` + JSON в `post_effect/` | В 26.3 нет публичного `GameRenderer.loadEffect`; собственный загрузчик не нужен |
| Тряска | Инжект в `Camera.getViewRotationMatrix` | `setRotation` и `rotation()` читает игровая логика и прицеливание; тряска не должна их трогать |
| Ползунки | `options.screenEffectScale` (в плане ошибочно `distortionEffectScale`) | Имя из `Options` 26.3 |
| Выключение целиком | `island_config.json: boundary.client_effects` (по умолчанию `true`) | Админ выключает для всех; поле nullable, иначе Gson молча отключил бы эффекты у старых конфигов |

## 2. Что подтверждено по jar, а что нет

**Подтверждено** (`javap` по `minecraft-client-26.3.jar` и `fabric-api-0.164.0+26.3`):
- `LocalPlayer.setActivePostEffects(List<Identifier>)`, пакет `ClientboundPostEffectsPacket`.
- `Camera.getViewRotationMatrix(Matrix4f)` и цепочка `rotation()` → `cachedViewRotMatrix`.
- `Options.screenEffectScale()`, `fovEffectScale()`, `darknessEffectScale()`.
- `CustomPacketPayload.Type`, `StreamCodec.of`, `FriendlyByteBuf.writeByteArray/readByteArray`.
- `PayloadTypeRegistry.clientboundPlay()`, `ClientPlayNetworking.registerGlobalReceiver`, `ServerPlayNetworking.send` / `canSend`.
- `ClientPlayConnectionEvents.DISCONNECT`, `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`,
  `ServerPlayerEvents.AFTER_RESPAWN`, `ResourceManagerHelper` / `SimpleSynchronousResourceReloadListener`.
- Формат пост-эффекта (`targets`/`passes`/`inputs`/`output`/`uniforms`) — по `blur.json`, `invert.json`.

**Не подтверждено** (нужна игра или GPU):
- Визуальный результат шейдеров. `glslangValidator` проверил синтаксис GLSL 330, но не привязку `uniform`-блоков и не картинку.
- Поведение `setActivePostEffects` при перезагрузке ресурсов (логика в `onResourcesReloaded` — по предположению).
- Тряска: амплитуда 1.2° подобрана на глаз.

**Не проверено вообще:**
- Сборка через Gradle/Loom. В песочнице `services.gradle.org` даёт редирект, Loom не запускался.
  Проверено `kotlinc` 2.2.20 и `javac` 25 против скачанного клиентского jar 26.3 и библиотек.
  Расхождения с Loom возможны (версия Kotlin в проекте 2.4.21, компилятор песочницы 2.2.20).

## 3. Патчи

| № | Коммит | Содержимое |
|---|---|---|
| 0001 | `Add HallucinationPayload contract` | `HallucinationMessage` (формат), `HallucinationPayload` (пакет), регистрация типа, тесты кодека |
| 0002 | `Send hallucination stage descriptors from server` | `StageConfig`, `client_effects`, отправка из `BoundaryManager`, сброс `stage = 0` |
| 0003 | `Add client hallucination controller and vanilla post effects` | `HallucinationController` (шесть правил сброса), `HallucinationClient`, тесты контроллера |
| 0004 | `Add camera shake via view matrix mixin` | `CameraShake`, `CameraShakeMixin`, client-конфиг миксинов, удаление `ClientMixin.java` |
| 0005 | `Add post effect assets for hallucination stages` | три `post_effect/*.json` и два `shaders/post/*.fsh` |
| 0006 | этот документ | `docs/PHASE-4.4.md` |

Каждый патч применяется через `git am` поверх предыдущего и на чистом `1aef9c7`.

## 4. Правила сброса (контроллер)

Сброс эффекта происходит по шести событиям:

1. Пакет `stage = 0` — возврат в безопасную зону, смерть (`UnknownDeath`), смена измерения.
2. Истёк `durationTicks` — `HallucinationController.tick()`.
3. Смерть игрока — `onPlayerDied()`.
4. Смена измерения — `onDimensionChanged()`.
5. Выход из мира — `ClientPlayConnectionEvents.DISCONNECT`.
6. Перезагрузка ресурсов — `onResourcesReloaded()` переприменяет эффект, если он активен.

Повторный пакет той же стадии не усиливает эффект: сбрасывается только таймер (идемпотентность).

## 5. Конфиг

```json
"boundary": {
  "client_effects": true
},
"hallucinations": {
  "stage2": {
    "effects": ["darkness:2", "nausea:2", "slowness:1"],
    "post_effect": "distortion_light",
    "screen_shake": true,
    "duration_ticks": 300,
    "intensity": 0.6
  }
}
```

Старый `island_config.json` без этих ключей грузится: `post_effect` — null, `screen_shake` — false,
`duration_ticks` — 300, `intensity` — 0.5, `client_effects` — включено.

## 6. Что осталось до релиза

- [ ] Сборка через Gradle в CI (`./gradlew build`) — основная проверка, которой в песочнице нет.
- [ ] Визуальная проверка в `runClient`: три стадии различимы; сброс снимает эффект на всех шести событиях.
- [ ] Мультиплеер: эффект видит только пересёкший границу.
- [ ] Ползунки доступности на нуле → ни шейдера, ни тряски.
- [ ] Выделенный сервер стартует без клиентских классов (проверить по логу).
- [ ] Старый конфиг грузится без правок.

## 7. Вне этой серии

- Звуки галлюцинаций (4.4 без звуков по решению).
- Фейковые сущности (выброшены).
- Обновление до Fabric API 0.165 / Minecraft 26.4 (отдельная задача).
- Автотесты клиентской части: проверены только чистая логика (кодек, контроллер, математика тряски).
