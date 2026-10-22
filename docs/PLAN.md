# Уточнённый план мода «Island World» для Minecraft 26.3 и Fabric 0.164.0+26.3

Учтены все уточнения: спавн на юго-западе, клиентский мод, чёрная комната с порталом и телепортацией, температура по оси север-юг.

---

## 0. Ключевые решения после уточнений

1. **Граница:** расстояние от (0, 0) по формуле Евклида. Предупреждения на 1800, 1900 и 1950 блоках, смерть на 2000 через урон `islandworld:unknown` (без сущности). Текст стадий в обычной игре не выводится.
2. **Спавн:** юго-западный край острова, примерно (−600, ~, −600).
3. **Клиентский мод:** собственный рендерер сущности, пост-эффекты (shader), звуки, payload'ы для галлюцинаций.
4. **Чёрная комната:**
   - Размер 9×9 чанков (144×144 блока) с центром в (0, 64, 0).
   - Пол из барьеров (неразрушимый прозрачный блок).
   - Портал Нижнего мира в центре для выхода.
   - Телепортация при пересечении границы чанков: север↔юг, запад↔восток (wrap-around).
5. **Температурная карта:** линейный градиент север→юг (холодный→жаркий) вместо ванильного 2D-шума.

---

## 1. Структура проекта

```
islandworld/
├─ build.gradle
├─ gradle.properties                      (Java 25, fabric-api 0.164.0+26.3)
├─ src/main/java/com/yourname/islandworld/
│  ├─ IslandWorldMod.java                 (ModInitializer)
│  ├─ IslandWorldClient.java              (ClientModInitializer)
│  ├─ config/
│  │  ├─ IslandConfig.java                (JSON, Cloth Config GUI)
│  │  └─ ConfigManager.java
│  ├─ worldgen/
│  │  ├─ density/
│  │  │  ├─ IslandMaskDensityFunction     (неровная береговая линия)
│  │  │  ├─ NorthSouthTemperature         (температура по Z)
│  │  │  └─ ScaledClimateDensityFunction  (сжатие климата)
│  │  ├─ biome/
│  │  │  ├─ IslandBiomeSource             (гарантия всех биомов)
│  │  │  └─ BiomeRegistry                 (список целевых биомов)
│  │  ├─ structure/
│  │  │  ├─ OceanStrongholdPlacement      (стронгхолды вне острова)
│  │  │  └─ PortalBlocker                 (запрет ручных порталов Энда)
│  │  └─ IslandWorldPreset                (регистрация пресета)
│  ├─ border/
│  │  ├─ BoundaryManager                  (тик, проверка расстояния)
│  │  ├─ Warnings                         (текст стадий только при debug_messages)
│  │  ├─ UnknownDeath                     (урон islandworld:unknown)
│  │  ├─ HallucinationEffect              (серверные эффекты, звуки, фейковые сущности)
│  │  └─ PlayerBoundaryState              (Attachment API)
│  ├─ portal/
│  │  ├─ mixin/MixinNetherPortal          (перехват телепортации)
│  │  ├─ BlackRoomTeleporter              (логика входа в чёрную комнату)
│  │  └─ BlackRoomWrapAround              (телепортация на границе чанков)
│  ├─ dimension/
│  │  ├─ BlackRoomDimension               (регистрация, тип)
│  │  ├─ BlackRoomChunkGenerator          (9×9 чанков, барьеры)
│  │  └─ BlackRoomProtection              (запрет ломания, спавна, взрывов)
│  ├─ network/
│  │  ├─ payload/
│  │  │  ├─ HallucinationPayload          (клиентские эффекты)
│  │  └─ NetworkHandler
│  ├─ command/
│  │  ├─ IslandCommand                    (/island)
│  │  ├─ BiomeReportCommand               (/island biomes)
│  │  └─ LocatePortalsCommand             (/island locate_portals)
│  └─ util/
│     ├─ DistanceUtil                     (проверка радиуса)
│     └─ SpawnUtil                        (установка спавна на юго-западе)
├─ src/client/java/com/yourname/islandworld/client/
│  ├─ render/
│  │  └─ HallucinationOverlay             (пост-эффект, виньетка)
│  ├─ sound/HallucinationSoundManager
│  └─ shader/                             (distortion.json, vignette.json)
└─ src/main/resources/
   ├─ fabric.mod.json                     (entrypoints: main, client)
   ├─ islandworld.mixins.json
   ├─ assets/islandworld/
   │  ├─ lang/ru_ru.json, en_us.json
   │  ├─ sounds.json, sounds/hallucination/
   │  ├─ textures/entity/unknown/
   │  ├─ models/entity/unknown.json
   │  └─ shaders/post/distortion.json
   └─ data/islandworld/
      ├─ dimension/overworld.json, black_room.json
      ├─ dimension_type/black_room.json
      ├─ worldgen/
      │  ├─ world_preset/island.json
      │  ├─ noise_settings/island.json
      │  ├─ density_function/
      │  │  ├─ island_mask.json
      │  │  ├─ north_south_temperature.json
      │  │  └─ scaled_climate.json
      │  ├─ biome/black_room.json
      │  ├─ structure_set/strongholds.json  (океан)
      │  └─ configured_feature/, placed_feature/
      ├─ damage_type/unknown.json
      └─ tags/worldgen/biome/is_overworld.json
```

---

## 2. Этап 1. Генерация острова с неровными краями и температурой север-юг

### 2.1. World Preset и Noise Settings

**`data/islandworld/worldgen/world_preset/island.json`:**
```json
{
  "dimensions": {
    "minecraft:overworld": {
      "type": "minecraft:overworld",
      "generator": {
        "type": "minecraft:noise",
        "biome_source": {
          "type": "islandworld:island_biome_source"
        },
        "settings": "islandworld:island"
      }
    },
    "minecraft:the_nether": { "..." },
    "minecraft:the_end": { "..." }
  }
}
```

Добавьте пресет в тег `minecraft:normal`, чтобы он появился в GUI создания мира.

---

### 2.2. Density Function: Маска острова с неровными краями

**`IslandMaskDensityFunction`** (Java):

```java
public class IslandMaskDensityFunction implements DensityFunction {
    private final DensityFunction noise2d; // Multi-octave 2D noise
    private final double islandRadius = 750.0; // ~1500 блоков диаметр
    private final double edgeSoftness = 100.0;
    private final double noiseAmplitude = 150.0; // амплитуда искажения

    @Override
    public double compute(FunctionContext ctx) {
        double x = ctx.blockX();
        double z = ctx.blockZ();
        
        // Искажение границы 2D-шумом
        double noiseOffset = noise2d.compute(ctx) * noiseAmplitude;
        double distortedRadius = islandRadius + noiseOffset;
        
        double distance = Math.sqrt(x * x + z * z);
        
        // Smooth falloff от центра к краю
        double normalized = (distance - distortedRadius) / edgeSoftness;
        return 1.0 - smoothstep(normalized); // 1 внутри, 0 снаружи
    }
    
    private double smoothstep(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3.0 - 2.0 * x);
    }
}
```

Регистрация через `DensityFunctions.CODEC` и JSON:

**`data/islandworld/worldgen/density_function/island_mask.json`:**
```json
{
  "type": "islandworld:island_mask",
  "noise": {
    "type": "minecraft:noise",
    "noise": "minecraft:offset",
    "xz_scale": 0.005,
    "y_scale": 0
  }
}
```

---

### 2.3. Density Function: Температура север-юг

**`NorthSouthTemperatureDensityFunction`** (Java):

```java
public class NorthSouthTemperatureDensityFunction implements DensityFunction {
    private final double coldZ = -750.0; // северный край
    private final double hotZ = 750.0;   // южный край
    
    @Override
    public double compute(FunctionContext ctx) {
        double z = ctx.blockZ();
        
        // Линейная интерполяция: север (−1) → юг (+1)
        double t = (z - coldZ) / (hotZ - coldZ);
        t = Math.max(0, Math.min(1, t));
        
        // Маппинг в диапазон температуры биомов (−1…+1)
        return t * 2.0 - 1.0; // −1 (холодно) до +1 (жарко)
    }
}
```

**`data/islandworld/worldgen/density_function/north_south_temperature.json`:**
```json
{
  "type": "islandworld:north_south_temperature"
}
```

В `noise_settings/island.json` в `noise_router` замените `temperature` на `islandworld:north_south_temperature`.

---

### 2.4. Сжатие климата

**`ScaledClimateDensityFunction`** масштабирует координаты XZ перед вычислением `vegetation`, `erosion`, `ridges` в 3–5 раз, чтобы уместить разнообразие биомов на острове.

```java
public class ScaledClimateDensityFunction implements DensityFunction {
    private final DensityFunction wrapped;
    private final double scale = 4.0;
    
    @Override
    public double compute(FunctionContext ctx) {
        // Масштабируем координаты
        FunctionContext scaledCtx = new ScaledFunctionContext(ctx, scale);
        return wrapped.compute(scaledCtx);
    }
}
```

Применяется к `vegetation`, `erosion`, `ridges` в JSON.

---

### 2.5. Интеграция маски в `noise_router`

В `noise_settings/island.json` измените:

- **`continents`:** `blend(islandworld:island_mask, vanilla_continents, deep_ocean_value)` — внутри острова суша, снаружи океан.
- **`final_density`:** умножьте на маску, чтобы за пределами острова Y-плотность всегда была отрицательной (пустота или глубокое дно).

Пример (упрощённо):
```json
{
  "noise_router": {
    "temperature": "islandworld:north_south_temperature",
    "continents": {
      "type": "minecraft:blend_density",
      "argument": "islandworld:island_mask",
      "input": "minecraft:overworld/continents"
    },
    "final_density": {
      "type": "minecraft:mul",
      "argument1": "islandworld:island_mask",
      "argument2": "minecraft:overworld/caves/entrances"
    }
  }
}
```

---

### 2.6. Биомы: гарантия присутствия всех

**`IslandBiomeSource`** (Java):

1. Собирает список целевых биомов из тега `#islandworld:required_biomes` (все оверворлдные плюс новые из 26.x).
2. Делит остров на регионы (Voronoi-ячейки по сиду), каждому назначает биом из списка.
3. 3D-биомы (глубокая тьма, пещеры) включаются по условию Y < 0 и параметру шума.
4. Грибное поле размещается как островок у берега (проверка расстояния до края).
5. Оставшиеся биомы заполняются климатической сеткой (температура × vegetation × continentalness).

Команда **`/island biomes report`** сэмплирует сетку 50×50 и выводит:
- список найденных биомов;
- список отсутствующих биомов из тега `#islandworld:required_biomes`;
- рекомендации (увеличить масштаб, добавить регионы).

---

### 2.7. Спавн на юго-западе

**`SpawnUtil`** (Java):

```java
public static void setIslandSpawn(ServerLevel level) {
    // Цель: юго-запад острова, примерно (−600, ~, −600)
    BlockPos target = new BlockPos(-600, 64, -600);
    
    // Поиск безопасной позиции (суша, не в воде)
    BlockPos spawn = findSafeLandSpawn(level, target, 100);
    
    level.setDefaultSpawnPos(spawn, 0.0f);
}

private static BlockPos findSafeLandSpawn(ServerLevel level, BlockPos center, int radius) {
    for (int dx = -radius; dx <= radius; dx += 8) {
        for (int dz = -radius; dz <= radius; dz += 8) {
            BlockPos pos = center.offset(dx, 0, dz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            BlockPos candidate = new BlockPos(pos.getX(), y, pos.getZ());
            
            if (level.getBlockState(candidate.below()).isSolid() &&
                !level.getFluidState(candidate).isEmpty() == false) {
                return candidate;
            }
        }
    }
    return center.atY(64);
}
```

Вызов в `ServerWorldEvents.LOAD` или при первом входе в мир.

---

## 3. Этап 2. Порталы Энда только в океане

### 3.1. Стронгхолды в океане

**`data/islandworld/worldgen/structure_set/strongholds.json`:**

```json
{
  "structures": [
    {
      "structure": "minecraft:stronghold",
      "weight": 1
    }
  ],
  "placement": {
    "type": "islandworld:ocean_concentric_rings",
    "distance": 24,
    "spread": 8,
    "count": 12,
    "island_radius": 800,
    "max_radius": 1900
  }
}
```

**`OceanConcentricRingsPlacement`** (Java):

```java
public class OceanConcentricRingsPlacement extends StructurePlacement {
    private final int islandRadius;
    private final int maxRadius;
    
    @Override
    protected Optional<GenerationStub> getPlacementAt(GenerationContext ctx) {
        ChunkPos chunkPos = ctx.chunkPos();
        double distance = Math.sqrt(chunkPos.x * chunkPos.x + chunkPos.z * chunkPos.z) * 16.0;
        
        // Пропускаем позиции внутри острова и за границей
        if (distance < islandRadius || distance > maxRadius) {
            return Optional.empty();
        }
        
        // Проверяем, что биом океанический
        Holder<Biome> biome = ctx.biomeSource().getNoiseBiome(
            chunkPos.x << 2, 63, chunkPos.z << 2, ctx.randomState().sampler()
        );
        
        if (!biome.is(BiomeTags.IS_OCEAN)) {
            return Optional.empty();
        }
        
        return super.getPlacementAt(ctx);
    }
}
```

---

### 3.2. Запрет ручной постройки портала Энда

**`PortalBlocker`** (миксин на `EndPortalFrameBlock` или событие `UseBlockCallback`):

```java
@Mixin(EndPortalFrameBlock.class)
public class MixinEndPortalFrame {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUseFrame(BlockState state, Level level, BlockPos pos, 
                            Player player, InteractionHand hand, 
                            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        double distance = Math.sqrt(pos.getX() * pos.getX() + pos.getZ() * pos.getZ());
        
        if (distance < 800) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("islandworld.portal.blocked"), 
                    true
                );
            }
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
```

---

### 3.3. Команда `/island locate_portals`

Ищет стронгхолды в радиусе 2000, проверяет их координаты и выводит список с пометкой, что все за пределами острова.

---

## 4. Этап 3. Граница 2000 блоков с галлюцинациями и смертью

> Обновлено после уточнений: три предупреждения (1800 / 1900 / 1950), смерть на 2000 через кастомный тип урона без спавна сущности. Сообщения о стадиях в обычной игре не выводятся, эффект виден на экране. Текст стадий включается флагом `debug_messages` только для тестов. Сущность `UnknownEntity` из этого раздела удалена.

### 4.1. Конфиг

**`config/island_config.json`:**

```json
{
  "boundary": {
    "center_x": 0,
    "center_z": 0,
    "warn1_radius": 1800,
    "warn2_radius": 1900,
    "warn3_radius": 1950,
    "kill_radius": 2000,
    "kill_delay_ticks": 160,
    "check_interval_ticks": 10,
    "reset_radius_margin": 100,
    "debug_messages": false
  },
  "death_sound": {
    "victim_sound": "islandworld:unknown_death",
    "distant_sound": "islandworld:unknown_death_distant"
  },
  "hallucinations": {
    "stage1": {
      "effects": ["darkness:1", "nausea:1"],
      "sounds": ["islandworld:whisper"],
      "fake_entities": 1,
      "post_effect": "vignette_light"
    },
    "stage2": {
      "effects": ["darkness:2", "nausea:2", "slowness:1"],
      "sounds": ["islandworld:scream", "islandworld:heartbeat"],
      "fake_entities": 2,
      "post_effect": "distortion_light",
      "screen_shake": true
    },
    "stage3": {
      "effects": ["darkness:2", "nausea:3", "slowness:2"],
      "sounds": ["islandworld:heartbeat", "islandworld:distant_steps"],
      "fake_entities": 3,
      "post_effect": "distortion_heavy",
      "screen_shake": true
    }
  }
}
```

Пояснения:
- `kill_delay_ticks` — 160 тиков (8 секунд), задержка за границей 2000 после третьего предупреждения.
- `reset_radius_margin` — при возврате ниже `warn1_radius − 100` (1700) счётчик предупреждений обнуляется.
- `death_sound` — звук `distant_sound` получают все игроки сервера, кроме жертвы. Ограничения по радиусу нет.
- `debug_messages` — `false` в обычной игре. При `true` в action bar выводится номер стадии (только для тестов).

---

### 4.2. BoundaryManager

Состояние хранится на игроке в `PlayerBoundaryState`: количество выданных предупреждений (0–3) и накопленное время за границей. Enum стадий не нужен, стадия — это `warningsIssued`.

**`border/BoundaryManager.kt`:**

```kotlin
object BoundaryManager {
    private val states = HashMap<UUID, PlayerBoundaryState>()

    lateinit var cfg: BoundaryConfig
        private set

    fun init(config: BoundaryConfig) {
        cfg = config
    }

    fun tick(server: MinecraftServer) {
        if (server.tickCount % cfg.checkIntervalTicks != 0) return
        for (player in server.overworld().players()) {
            tickPlayer(player)
        }
    }

    private fun tickPlayer(player: ServerPlayer) {
        val st = states.getOrPut(player.uuid) { PlayerBoundaryState() }
        val dist = hypot(player.x - cfg.centerX, player.z - cfg.centerZ)

        // Возврат в безопасную зону сбрасывает всё
        if (dist < cfg.warn1Radius - cfg.resetRadiusMargin) {
            st.warningsIssued = 0
            st.beyondKillTicks = 0
            return
        }

        // Предупреждения выдаются строго по порядку, даже если игрок перескочил несколько порогов
        val thresholds = doubleArrayOf(cfg.warn1Radius, cfg.warn2Radius, cfg.warn3Radius)
        while (st.warningsIssued < 3 && dist >= thresholds[st.warningsIssued]) {
            st.warningsIssued++
            Warnings.send(player, st.warningsIssued)
        }

        // Смерть только после всех трёх предупреждений и задержки за границей 2000.
        // Игроки в творческом режиме и наблюдатели не умирают.
        if (dist >= cfg.killRadius && st.warningsIssued == 3 && !isProtected(player)) {
            st.beyondKillTicks += cfg.checkIntervalTicks
            if (st.beyondKillTicks >= cfg.killDelayTicks) {
                UnknownDeath.kill(player)
                states.remove(player.uuid)
            }
        } else {
            st.beyondKillTicks = 0
        }
    }

    // Творческий режим и наблюдатель не получают урон от границы
    private fun isProtected(player: ServerPlayer): Boolean =
        player.isCreative || player.isSpectator

    fun onDisconnect(uuid: UUID) { states.remove(uuid) }
    fun onRespawn(uuid: UUID) { states.remove(uuid) }
}

class PlayerBoundaryState {
    var warningsIssued = 0
    var beyondKillTicks = 0
}
```

Регистрация в `IslandWorldMod.kt`: `ServerTickEvents.END_SERVER_TICK`, `ServerPlayConnectionEvents.DISCONNECT`, `ServerPlayerEvents.AFTER_RESPAWN`.

---

### 4.3. Warnings и HallucinationEffect

**`border/Warnings.kt`:**

```kotlin
object Warnings {
    fun send(player: ServerPlayer, stage: Int) {
        // Текст стадии только при debug_messages (для тестов)
        if (BoundaryManager.cfg.debugMessages) {
            player.displayClientMessage(Component.literal("[Boundary] стадия $stage"), true)
        }
        // Основной сигнал для игрока — визуальные эффекты
        HallucinationEffect.apply(player, stage)
    }
}
```

**`border/HallucinationEffect.kt`** (серверная часть):
- Применяет эффекты из `hallucinations.stageN.effects` (`darkness`, `nausea`, `slowness`).
- Проигрывает звуки из `sounds` (только у этого игрока, через `ClientboundSoundPacket`).
- Отправляет `HallucinationPayload(stage)` клиенту для шейдеров и виньетки.
- Фейковые сущности (зомби за спиной) спавнятся через `ClientboundAddEntityPacket` с **сохранённым ID**. Удаляются точным `ClientboundRemoveEntitiesPacket(id)` через 3 секунды. Хак с фиксированным ID `-9999` из старой версии не используется.

**`network/payload/HallucinationPayload.kt`:**

```kotlin
data class HallucinationPayload(val stage: Int) : CustomPayload {
    companion object {
        val ID = CustomPayload.Id<HallucinationPayload>(IslandWorldIds.id("hallucination"))
    }
    override fun id() = ID
}
```

`BoundaryStagePayload` из старого плана удалён: один payload покрывает и синхронизацию стадии, и визуальные эффекты.

---

### 4.4. Клиент: шейдеры, виньетка, звуки

**`IslandWorldClient.kt`:**
- Принимает `HallucinationPayload` и вызывает `HallucinationOverlay.apply(stage)`.
- Регистрация `UnknownEntityRenderer` **удалена**.

**`HallucinationOverlay`:**
- `stage 1`: виньетка (`vignette.json`).
- `stage 2`: лёгкое искажение (`distortion.json`, интенсивность 0.05) и встряхивание камеры.
- `stage 3`: сильное искажение (интенсивность 0.12), усиленная виньетка, тряска.

Тряска камеры — только клиентская. Серверный `setXRot` из старой версии убран.

Файлы: `shaders/post/distortion.json`, `shaders/post/vignette.json`, `sounds.json` для `islandworld:whisper`, `scream`, `heartbeat`, `distant_steps`, `unknown_death`, `unknown_death_distant`.

---

### 4.5. Смерть от незримой сущности и звуки смерти

Сущности нет. Игрок умирает от кастомного типа урона `islandworld:unknown`, поэтому ванильная система смертей сама подставляет имя игрока в текст.

**Творческий режим и наблюдатель.** Игроки в творческом режиме (`player.isCreative`) и в режиме наблюдателя (`player.isSpectator`) этот урон не получают. Проверка `isProtected()` стоит в `BoundaryManager` перед вызовом `UnknownDeath.kill()`, поэтому для них не выполняется ни урон, ни звуки смерти. Предупреждения и галлюцинации у них остаются, потому что они не связаны со смертью. Если игрок переключился в творческий режим уже после смерти по таймеру, таймер сбрасывается, потому что условие не выполняется.

Во время смерти звучат два кастомных звука:
- `islandworld:unknown_death` — слышит только жертва. Отправляется в её соединение, без позиции, громкость 1.0.
- `islandworld:unknown_death_distant` — слышат **все остальные игроки сервера**, независимо от расстояния. Звук позиционный, в точке смерти. Чтобы он был слышен на любом расстоянии в пределах мира, затухание задано большим (`attenuation_distance: 4000`, см. `sounds.json`). Жертва в этот список не входит, чтобы она не слышала два звука.

Порядок в `kill()`: сначала звук жертве, затем звук всем остальным игрокам, и только потом урон. Так звуки успевают уйти до экрана смерти.

**`border/UnknownDeath.kt`:**

```kotlin
object UnknownDeath {
    val DAMAGE_TYPE: ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, IslandWorldIds.id("unknown"))

    val VICTIM_SOUND: Holder<SoundEvent> =
        Holder.direct(SoundEvent.createVariableRangeEvent(IslandWorldIds.id("unknown_death")))
    val DISTANT_SOUND: Holder<SoundEvent> =
        Holder.direct(SoundEvent.createVariableRangeEvent(IslandWorldIds.id("unknown_death_distant")))

    fun kill(player: ServerPlayer) {
        val level = player.level()
        val seed = level.random.nextLong()

        // 1. Жертва: звук только ей, без позиции
        player.connection.send(
            ClientboundSoundPacket(VICTIM_SOUND, SoundSource.MASTER,
                player.x, player.y, player.z, 1.0f, 1.0f, seed)
        )

        // 2. Все остальные игроки сервера: позиционный звук в точке смерти, без ограничения радиусом
        for (other in player.server.playerList.players) {
            if (other === player) continue
            other.connection.send(
                ClientboundSoundPacket(DISTANT_SOUND, SoundSource.HOSTILE,
                    player.x, player.y, player.z, 1.0f, 1.0f, seed)
            )
        }

        // 3. Урон: тип и текст смерти
        player.hurt(player.damageSources().source(DAMAGE_TYPE), Float.MAX_VALUE)
    }
}
```

Конфиг `death_sound` читается в data class `DeathSoundConfig(victimSound, distantSound)` и кладётся в `BoundaryConfig.deathSound`.

**`assets/islandworld/sounds.json`:**

```json
{
  "unknown_death": {
    "subtitle": "islandworld.subtitle.unknown_death",
    "sounds": [{ "name": "islandworld:unknown_death", "stream": false }]
  },
  "unknown_death_distant": {
    "subtitle": "islandworld.subtitle.unknown_death",
    "sounds": [{
      "name": "islandworld:unknown_death_distant",
      "stream": false,
      "attenuation_distance": 4000
    }]
  }
}
```

`attenuation_distance: 4000` держит звук слышимым по всей области границы (диаметр 4000 блоков). Громкость на больших расстояниях всё равно ниже, поэтому далёкие игроки слышат его тише. Субтитры для глухих можно не добавлять, тогда ключ `subtitle` убрать. Файлы `.ogg` (`unknown_death.ogg`, `unknown_death_distant.ogg`) в `assets/islandworld/sounds/` нужно подготовить заранее. Без них клиент покажет ошибку загрузки звука.

**`data/islandworld/damage_type/unknown.json`:**

```json
{
  "message_id": "islandworld.unknown",
  "scaling": "never",
  "exhaustion": 0.0
}
```

**`data/minecraft/tags/damage_type/bypasses_invulnerability.json`** — тотем и бессмертие не спасают. Имя тега проверить в 26.3.

```json
{
  "replace": false,
  "values": ["islandworld:unknown"]
}
```

**Локализация:**

| Ключ | ru_ru | en_us |
|---|---|---|
| `death.attack.islandworld.unknown` | `%s был уничтожен незримой сущностью` | `%s was destroyed by an unseen entity` |
| `islandworld.subtitle.unknown_death` | `Незримая сущность` | `Unseen entity` |

Ключи `islandworld.boundary.warn1–3` **не добавляются**: в обычной игре текст стадий не выводится. Для `debug_messages` текст захардкожен в `Warnings`, локализовать его можно позже.

**Открытые вопросы:**
- Наблюдатель (`spectator`) защищён так же, как творческий режим. Решение принято, открытых вопросов по защите нет.
- Проверить в `runClient`, что `death.attack` из `lang` мода подхватывается до построения сообщения.
- Сигнатура `ClientboundSoundPacket` и `SoundEvent.createVariableRangeEvent` в 26.3 могла измениться: проверить при реализации.
- Громкость звука подобрать на слух: при `attenuation_distance: 4000` дальние игроки слышат его тише. Если нужна одинаковая громкость для всех, позиционный пакет заменить на звук без координат.

---

## 5. Этап 4. Чёрная комната

### 5.1. Dimension Type

**`data/islandworld/dimension_type/black_room.json`:**

```json
{
  "ultrawarm": false,
  "natural": false,
  "piglin_safe": true,
  "respawn_anchor_works": false,
  "bed_works": false,
  "has_raids": false,
  "has_skylight": false,
  "has_ceiling": true,
  "coordinate_scale": 1.0,
  "ambient_light": 0.0,
  "fixed_time": 18000,
  "monster_spawn_light_level": 15,
  "monster_spawn_block_light_limit": 0,
  "logical_height": 256,
  "min_y": 0,
  "height": 256,
  "infiniburn": "#minecraft:infiniburn_overworld"
}
```

---

### 5.2. Dimension и ChunkGenerator

**`data/islandworld/dimension/black_room.json`:**

```json
{
  "type": "islandworld:black_room",
  "generator": {
    "type": "islandworld:black_room_generator",
    "biome_source": {
      "type": "minecraft:fixed",
      "biome": "islandworld:black_room"
    }
  }
}
```

**`BlackRoomChunkGenerator`** (Java):

```java
public class BlackRoomChunkGenerator extends NoiseBasedChunkGenerator {
    private static final int ROOM_SIZE_CHUNKS = 9;
    private static final int HALF = ROOM_SIZE_CHUNKS / 2;
    
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
                                                         RandomState random, StructureManager structures,
                                                         ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        
        // Проверяем, что чанк в пределах 9×9
        if (Math.abs(pos.x) > HALF || Math.abs(pos.z) > HALF) {
            return CompletableFuture.completedFuture(chunk); // пустой чанк
        }
        
        // Заполняем пол барьерами на Y=63
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlockState(new BlockPos(x, 63, z), Blocks.BARRIER.defaultBlockState(), false);
            }
        }
        
        // Центральный чанк (0,0): портал Незера на (8, 64, 8)
        if (pos.x == 0 && pos.z == 0) {
            buildNetherPortal(chunk);
        }
        
        return CompletableFuture.completedFuture(chunk);
    }
    
    private void buildNetherPortal(ChunkAccess chunk) {
        BlockPos center = new BlockPos(8, 64, 8);
        // Рамка из обсидиана 4×5
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 4; x++) {
                BlockPos pos = center.offset(x - 1, y, 0);
                if (y == 0 || y == 4 || x == 0 || x == 3) {
                    chunk.setBlockState(pos, Blocks.OBSIDIAN.defaultBlockState(), false);
                } else {
                    chunk.setBlockState(pos, Blocks.NETHER_PORTAL.defaultBlockState(), false);
                }
            }
        }
    }
}
```

---

### 5.3. Биом чёрной комнаты

**`data/islandworld/worldgen/biome/black_room.json`:**

```json
{
  "has_precipitation": false,
  "temperature": 0.5,
  "downfall": 0.0,
  "effects": {
    "sky_color": 0,
    "fog_color": 0,
    "water_color": 0,
    "water_fog_color": 0,
    "mood_sound": {
      "sound": "minecraft:ambient.cave",
      "tick_delay": 6000,
      "block_search_extent": 8,
      "offset": 2.0
    }
  },
  "spawners": {},
  "spawn_costs": {},
  "carvers": {},
  "features": []
}
```

---

### 5.4. Перехват портала Незера

**`MixinNetherPortal`** (миксин на `Portal` или `NetherPortal#getPortalDestination`):

```java
@Mixin(NetherPortal.class)
public class MixinNetherPortal {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void onGetDestination(ServerLevel level, Entity entity, BlockPos pos,
                                   CallbackInfoReturnable<TeleportTransition> cir) {
        if (level.dimension() == Level.NETHER && entity instanceof ServerPlayer player) {
            // Координата в Оверворлде = pos * 8
            double targetX = pos.getX() * 8.0;
            double targetZ = pos.getZ() * 8.0;
            double distance = Math.sqrt(targetX * targetX + targetZ * targetZ);
            
            if (distance > 2000) {
                // Телепортация в чёрную комнату
                ServerLevel blackRoom = player.server.getLevel(ModDimensions.BLACK_ROOM);
                Vec3 spawn = new Vec3(0.5, 64.0, 0.5);
                
                TeleportTransition transition = new TeleportTransition(
                    blackRoom,
                    spawn,
                    Vec3.ZERO,
                    0, 0,
                    TeleportTransition.DO_NOTHING
                );
                
                cir.setReturnValue(transition);
            }
        }
    }
}
```

---

### 5.5. Телепортация wrap-around на границе

**`BlackRoomWrapAround`** (тик-событие или миксин на `Entity#tick`):

```java
public class BlackRoomWrapAround {
    private static final int ROOM_SIZE = 9 * 16; // 144 блока
    private static final int HALF = ROOM_SIZE / 2;
    
    public static void tick(ServerLevel level) {
        if (level.dimension() != ModDimensions.BLACK_ROOM) return;
        
        for (Entity entity : level.getAllEntities()) {
            double x = entity.getX();
            double z = entity.getZ();
            
            boolean wrapped = false;
            
            if (x > HALF) {
                x = -HALF + (x - HALF);
                wrapped = true;
            } else if (x < -HALF) {
                x = HALF + (x + HALF);
                wrapped = true;
            }
            
            if (z > HALF) {
                z = -HALF + (z - HALF);
                wrapped = true;
            } else if (z < -HALF) {
                z = HALF + (z + HALF);
                wrapped = true;
            }
            
            if (wrapped) {
                entity.teleportTo(x, entity.getY(), z);
            }
        }
    }
}
```

---

### 5.6. Защита комнаты

**`BlackRoomProtection`:**

```java
public class BlackRoomProtection {
    public static void register() {
        // Запрет ломания блоков
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level.dimension() == ModDimensions.BLACK_ROOM) {
                return false;
            }
            return true;
        });
        
        // Запрет размещения блоков
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.dimension() == ModDimensions.BLACK_ROOM) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        
        // Отключение спавна мобов
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (level.dimension() == ModDimensions.BLACK_ROOM && entity instanceof Mob) {
                entity.discard();
            }
        });
        
        // Респавн: вернуть на остров
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer.level().dimension() == ModDimensions.BLACK_ROOM) {
                ServerLevel overworld = newPlayer.server.getLevel(Level.OVERWORLD);
                BlockPos spawn = overworld.getSharedSpawnPos();
                newPlayer.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(), 0, 0);
            }
        });
    }
}
```

---

## 6. Дополнительные детали

### 6.1. Команды

**`/island biomes report`** — сканирует остров, выводит список биомов.

**`/island locate_portals`** — ищет стронгхолды, показывает их координаты.

**`/island boundary test <player>`** — телепортирует игрока на 1850/1950/2050 для теста границы.

---

### 6.2. Локализация

**`assets/islandworld/lang/ru_ru.json`:**

```json
{
  "death.attack.islandworld.unknown": "%s был поглощён неизвестной сущностью",
  "islandworld.portal.blocked": "Портал не активируется так близко к центру острова",
  "generator.islandworld.island": "Остров в океане"
}
```

---

### 6.3. Оптимизация

- Проверка границы: раз в 10 тиков, кэш состояний.
- Галлюцинации: ограниченное число пакетов, таймеры.
- Генерация: Density Function кэшируется автоматически движком.

---

## 7. Порядок работ

| Этап | Задачи | Срок |
|---|---|---|
| **1. Шаблон** | gradle, структура, конфиг | 1–2 дня |
| **2. Генерация острова** | Маска, температура, сжатие климата, пресет | 5–7 дней |
| **3. Биомы** | `IslandBiomeSource`, гарантия всех биомов, команда `/island biomes` | 4–6 дней |
| **4. Спавн** | Размещение на юго-западе | 1 день |
| **5. Порталы Энда** | Стронгхолды в океане, запрет ручных | 2–3 дня |
| **6. Граница** | `BoundaryManager`, `Warnings`, `HallucinationEffect`, `UnknownDeath` | 6–8 дней |
| **7. Клиент** | Рендерер, шейдеры, звуки, пост-эффекты | 4–5 дней |
| **8. Чёрная комната** | Измерение, генератор, портал, wrap-around, защита | 4–5 дней |
| **9. Тесты** | Все сценарии, балансировка | 3–5 дней |

**Итого:** 4–6 недель.

---

## 8. Главные риски

1. **API версии 26.3:** проверьте названия классов (`TeleportTransition`, `DensityFunction`, `Portal`) через `genSources`.
2. **Температура север-юг:** интеграция в `noise_router` может потребовать дополнительных blend-функций.
3. **Гарантия биомов:** самая сложная часть; начните с неё.
4. **Клиентские шейдеры:** требуют знания OpenGL; можно упростить до базовой виньетки.
5. **Wrap-around телепортация:** проверьте, что entity velocity сохраняется.

---

> Примечание: это исходный план. Текущее состояние по разделам описано в `README.md` в корне репозитория. Примеры кода в плане — набросок, API и реализация в проекте (Kotlin) отличаются.
