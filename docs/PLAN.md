# Уточнённый план мода «Island World» для Minecraft 26.3 и Fabric 0.164.0+26.3

Учтены все уточнения: спавн на юго-западе, клиентский мод, чёрная комната с порталом и телепортацией, температура по оси север-юг.

---

## 0. Ключевые решения после уточнений

1. **Граница:** расстояние от (0, 0) по формуле Евклида, радиус 2000 блоков.
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
│  │  ├─ BoundaryStage                    (enum: SAFE, WARN1, WARN2, KILL)
│  │  ├─ HallucinationEffect              (пакеты, звуки, фальшивые сущности)
│  │  └─ PlayerBoundaryState              (Attachment API)
│  ├─ entity/
│  │  ├─ UnknownEntity                    (сущность-убийца)
│  │  └─ UnknownEntityRenderer            (клиент, модель)
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
│  │  │  └─ BoundaryStagePayload          (синхронизация стадии)
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
│  │  ├─ UnknownEntityRenderer
│  │  ├─ UnknownEntityModel
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

## 4. Этап 3. Граница 2000 блоков с галлюцинациями и убийством

### 4.1. Конфиг

**`config/island_config.json`:**

```json
{
  "boundary": {
    "center_x": 0,
    "center_z": 0,
    "warn1_radius": 1800,
    "warn2_radius": 1900,
    "kill_radius": 2000,
    "kill_delay_seconds": 8,
    "check_interval_ticks": 10
  },
  "hallucinations": {
    "stage1": {
      "effects": ["darkness:1", "nausea:1"],
      "sounds": ["islandworld:whisper", "islandworld:distant_steps"],
      "fake_entities": 1,
      "post_effect": "vignette_light"
    },
    "stage2": {
      "effects": ["darkness:2", "nausea:2", "slowness:1"],
      "sounds": ["islandworld:scream", "islandworld:heartbeat"],
      "fake_entities": 3,
      "post_effect": "distortion_heavy",
      "screen_shake": true
    }
  }
}
```

---

### 4.2. BoundaryManager

**`BoundaryManager`** (тик-событие):

```java
public class BoundaryManager {
    private static final Map<UUID, PlayerBoundaryState> states = new HashMap<>();
    
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        
        for (ServerPlayer player : overworld.players()) {
            UUID uuid = player.getUUID();
            PlayerBoundaryState state = states.computeIfAbsent(uuid, k -> new PlayerBoundaryState());
            
            double distance = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
            BoundaryStage newStage = calculateStage(distance);
            
            if (newStage != state.stage) {
                state.stage = newStage;
                state.stageTimer = 0;
                sendStagePacket(player, newStage);
            }
            
            state.stageTimer++;
            
            switch (newStage) {
                case WARN1 -> HallucinationEffect.applyStage1(player, state);
                case WARN2 -> HallucinationEffect.applyStage2(player, state);
                case KILL -> handleKillStage(player, state);
            }
        }
    }
    
    private static void handleKillStage(ServerPlayer player, PlayerBoundaryState state) {
        if (state.stageTimer > IslandConfig.BOUNDARY.killDelaySeconds * 20) {
            UnknownEntity entity = new UnknownEntity(player.level());
            entity.setPos(player.getX(), player.getY(), player.getZ());
            entity.setTarget(player);
            player.level().addFreshEntity(entity);
            
            state.entitySpawned = true;
        }
    }
}
```

---

### 4.3. HallucinationEffect

**`HallucinationEffect`** (серверная часть):

```java
public class HallucinationEffect {
    public static void applyStage1(ServerPlayer player, PlayerBoundaryState state) {
        // Эффекты зелий
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, true, false));
        
        // Звуки (только для этого игрока)
        if (state.stageTimer % 100 == 0) {
            player.connection.send(new ClientboundSoundPacket(
                SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.AMBIENT,
                player.getX(), player.getY(), player.getZ(),
                1.0f, 0.8f,
                player.level().random.nextLong()
            ));
        }
        
        // Фальшивая сущность вдали (пакет spawn)
        if (state.stageTimer == 20 && state.fakeEntities.isEmpty()) {
            spawnFakeEntity(player, 20.0);
        }
        
        // Клиентский пост-эффект
        NetworkHandler.sendToClient(player, new HallucinationPayload("vignette_light", 1));
    }
    
    private static void spawnFakeEntity(ServerPlayer player, double distance) {
        // Спавним фальшивого зомби позади игрока
        Vec3 behind = player.position().add(player.getLookAngle().scale(-distance));
        
        ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(
            /* временный ID */ -9999,
            UUID.randomUUID(),
            behind.x, behind.y, behind.z,
            0, 0,
            EntityType.ZOMBIE,
            0,
            Vec3.ZERO,
            0
        );
        
        player.connection.send(packet);
        
        // Удаляем через 3 секунды
        player.server.execute(() -> {
            player.connection.send(new ClientboundRemoveEntitiesPacket(-9999));
        }, 60);
    }
}
```

**`HallucinationPayload`** (Fabric Networking API v1):

```java
public record HallucinationPayload(String effectName, int intensity) implements CustomPayload {
    public static final Id<HallucinationPayload> ID = 
        new Id<>(new ResourceLocation("islandworld", "hallucination"));
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
```

---

### 4.4. Клиентская часть: рендер и шейдеры

**`IslandWorldClient.java`:**

```java
public class IslandWorldClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HallucinationPayload.ID, 
            (payload, context) -> {
                context.client().execute(() -> {
                    HallucinationOverlay.apply(payload.effectName(), payload.intensity());
                });
            }
        );
        
        EntityRendererRegistry.register(ModEntities.UNKNOWN, UnknownEntityRenderer::new);
    }
}
```

**`HallucinationOverlay`:**

```java
public class HallucinationOverlay {
    private static String currentEffect = "";
    private static int intensity = 0;
    private static final Random random = new Random();
    
    public static void apply(String effect, int level) {
        currentEffect = effect;
        intensity = level;
        
        // Загружаем пост-процессинг шейдер
        Minecraft mc = Minecraft.getInstance();
        if (effect.equals("distortion_heavy")) {
            mc.gameRenderer.loadEffect(new ResourceLocation("islandworld", "shaders/post/distortion.json"));
        }
        
        // Встряхивание камеры
        if (level >= 2) {
            shakeCamera();
        }
    }
    
    private static void shakeCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            float shake = 0.5f;
            mc.player.setXRot(mc.player.getXRot() + (random.nextFloat() - 0.5f) * shake);
            mc.player.setYRot(mc.player.getYRot() + (random.nextFloat() - 0.5f) * shake);
        }
    }
}
```

**`assets/islandworld/shaders/post/distortion.json`:**

```json
{
  "targets": ["swap"],
  "passes": [
    {
      "name": "islandworld:distortion",
      "intarget": "minecraft:main",
      "outtarget": "swap",
      "uniforms": [
        { "name": "Intensity", "values": [0.05, 0.05, 0.05] },
        { "name": "Time", "values": [0.0] }
      ]
    },
    {
      "name": "blit",
      "intarget": "swap",
      "outtarget": "minecraft:main"
    }
  ]
}
```

Fragment shader (`distortion.fsh`) с wave-искажением координат текстуры.

---

### 4.5. UnknownEntity

**`UnknownEntity.java`:**

```java
public class UnknownEntity extends Monster {
    public UnknownEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
    }
    
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 2.0, false));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }
    
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // неубиваема
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Телепортация к цели, если далеко
        if (getTarget() instanceof Player player) {
            if (distanceToSqr(player) > 100) {
                Vec3 behind = player.position().add(player.getLookAngle().scale(-3));
                teleportTo(behind.x, behind.y, behind.z);
            }
            
            // Убийство при контакте
            if (distanceToSqr(player) < 4) {
                player.hurt(damageSources().source(ModDamageTypes.UNKNOWN, this), Float.MAX_VALUE);
                this.discard();
            }
        }
    }
}
```

**`UnknownEntityRenderer`:**

```java
public class UnknownEntityRenderer extends MobRenderer<UnknownEntity, UnknownEntityModel> {
    private static final ResourceLocation TEXTURE = 
        new ResourceLocation("islandworld", "textures/entity/unknown/unknown.png");
    
    public UnknownEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new UnknownEntityModel(context.bakeLayer(ModModelLayers.UNKNOWN)), 0.5f);
    }
    
    @Override
    public ResourceLocation getTextureLocation(UnknownEntity entity) {
        return TEXTURE;
    }
}
```

Модель — гуманоидная или абстрактная (тень с глазами), создаётся через Blockbench.

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
  "islandworld.boundary.warn1": "Вам не следует идти дальше...",
  "islandworld.boundary.warn2": "Поверните назад, пока не поздно!",
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
| **6. Граница** | `BoundaryManager`, галлюцинации, `UnknownEntity` | 6–8 дней |
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
