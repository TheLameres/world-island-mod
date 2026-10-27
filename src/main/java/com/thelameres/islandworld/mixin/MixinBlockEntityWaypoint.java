package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.locator.BlockWaypointState;
import com.thelameres.islandworld.locator.BlockWaypointTransmitter;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Добавляет Waypoint-возможность табличкам и флагам.
 * <p>
 * ВАЖНО: здесь нет ни одного @Inject. `setLevel` объявлен в BlockEntity, а не в
 * SignBlockEntity/BannerBlockEntity, поэтому инъекция по этому имени в целевой класс
 * не находится и валит загрузку. Регистрация/снятие передатчика живёт в
 * MixinLevelChunkWaypoint, где вызывается LevelChunk.setBlockEntity.
 */
@Mixin({SignBlockEntity.class, BannerBlockEntity.class})
public abstract class MixinBlockEntityWaypoint implements BlockWaypointTransmitter {

    @Unique
    private final BlockWaypointState islandworld$waypointState = new BlockWaypointState();

    @Override
    public BlockWaypointState islandworld$waypointState() {
        return this.islandworld$waypointState;
    }
}
