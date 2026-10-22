package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.worldgen.blackroom.BlackRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перехват портала Нижнего мира (план, раздел 5.4).
 * Если точка назначения дальше 2000 блоков от центра Overworld,
 * игрок отправляется в Чёрную комнату.
 * <p>
 * ВНИМАНИЕ: сигнатура метода и пакет TeleportTransition взяты из плана и
 * должны быть сверены с маппингами Minecraft 26.3 при первой сборке.
 */
@Mixin(NetherPortalBlock.class)
public class MixinNetherPortal {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void islandworld$onGetDestination(ServerLevel level, Entity entity, BlockPos pos,
                                              CallbackInfoReturnable<TeleportTransition> cir) {
        if (level.dimension() != Level.NETHER) return;
        if (!(entity instanceof ServerPlayer)) return;

        // Координата в Overworld = pos * 8 (план, 5.4)
        double targetX = pos.getX() * 8.0;
        double targetZ = pos.getZ() * 8.0;
        double distance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (distance <= 2000) return;

        ServerLevel blackRoom = level.getServer().getLevel(BlackRoom.DIMENSION);
        if (blackRoom == null) return;

        // Точка входа в комнату — центр (8, 64, 8) по плану
        Vec3 spawn = new Vec3(8.5, BlackRoom.SPAWN_Y, 8.5);
        cir.setReturnValue(new TeleportTransition(
                blackRoom,
                spawn,
                Vec3.ZERO,
                0f, 0f,
                TeleportTransition.DO_NOTHING));
    }
}
