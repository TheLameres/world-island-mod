package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.portal.ReturnPortalStore;
import com.thelameres.islandworld.worldgen.blackroom.BlackRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перехват порталов (план, раздел 5.4).
 * Если точка назначения дальше 2000 блоков от центра Overworld,
 * игрок отправляется в Чёрную комнату.
 * <p>
 * ВНИМАНИЕ: сигнатура метода и пакет TeleportTransition взяты из плана и
 * должны быть сверены с маппингами Minecraft 26.3 при первой сборке.
 */
@Mixin(NetherPortalBlock.class)
public class MixinNetherPortal {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void islandworld$onGetDestination(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir) {
        MinecraftServer server = level.getServer();
        if (!(entity instanceof ServerPlayer player)) return;

        // Выход из Чёрной комнаты: возвращаем в Незер к сохранённому порталу
        if (level.dimension() == BlackRoom.DIMENSION) {
            islandworld$exitToNether(player, server, cir);
            return;
        }

        // Вход в Чёрную комнату из Незера в дальней точке
        if (level.dimension() != Level.NETHER) return;


        // Координата в Overworld = pos * 8 (план, 5.4)
        double targetX = pos.getX() * 8.0;
        double targetZ = pos.getZ() * 8.0;
        double distance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        if (distance <= 2000) return;

        ServerLevel blackRoom = server.getLevel(BlackRoom.DIMENSION);
        if (blackRoom == null) return;

        // Запоминаем портал Незера, через который игрок вошёл в комнату
        ReturnPortalStore.INSTANCE.save(server, player.getUUID(), pos);


        // Точка входа в комнату — центр (8, 64, 8) по плану
        Vec3 spawn = new Vec3(8.5, BlackRoom.SPAWN_Y, 8.5);
        cir.setReturnValue(new TeleportTransition(blackRoom, spawn, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
    }


    @Unique
    private void islandworld$exitToNether(ServerPlayer player, MinecraftServer server, CallbackInfoReturnable<TeleportTransition> cir) {
        BlockPos saved = ReturnPortalStore.INSTANCE.get(server, player.getUUID());
        if (saved == null) return; // нет записи — оставляем стандартное поведение


        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        // Ставим игрока рядом с порталом, на его уровне входа
        Vec3 target = new Vec3(saved.getX() + 0.5, saved.getY(), saved.getZ() + 0.5);
        cir.setReturnValue(new TeleportTransition(nether, target, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
    }
}
