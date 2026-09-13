package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Debug-grade Cloud/Fog visual delivery spike. This is intentionally not a
 * terrain material simulation: it samples a few loaded surface columns and
 * sends bounded vanilla particles to nearby players.
 */
final class ForgeAtmospherePrototype {

    private static final int TICK_INTERVAL = 100;
    private static final int HIGH_TERRAIN_ABOVE_SEA = 24;
    private static final int AUTO_PARTICLE_CAP = 16;
    private static final int DEMO_PARTICLE_CAP = 24;
    private static final int[][] SAMPLE_OFFSETS = {
        {0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}, {8, 8}, {-8, -8}, {8, -8}
    };

    private long serverTicks;
    private long automaticPasses;
    private long particlesSent;
    private int playersLastPass;

    void onServerTick(ServerTickEvent.Post event) {
        serverTicks++;
        if (serverTicks % TICK_INTERVAL != 0) {
            return;
        }

        automaticPasses++;
        playersLastPass = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            playersLastPass++;
            emitAutomatic(player);
        }
    }

    void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    void onServerStopped(ServerStoppedEvent event) {
        serverTicks = 0;
        automaticPasses = 0;
        particlesSent = 0;
        playersLastPass = 0;
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dynamicatmosphere")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("demo").executes(context -> demo(context.getSource()))));
    }

    private int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "Dynamic Atmosphere visual delivery spike: active; interval=" + TICK_INTERVAL
                + " ticks, particleCap=" + AUTO_PARTICLE_CAP
                + ", passes=" + automaticPasses
                + ", particles=" + particlesSent
                + ", playersLastPass=" + playersLastPass
                + ". No material simulation or world weather changes."), false);
        return 1;
    }

    private int demo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        sendToPlayer(player, player.getX(), player.getY() + 1.2, player.getZ(), DEMO_PARTICLE_CAP, 2.5, 0.7, 2.5, 0.02);
        source.sendSuccess(() -> Component.literal(
            "Dynamic Atmosphere demo emitted " + DEMO_PARTICLE_CAP + " vanilla cloud particles."), false);
        return DEMO_PARTICLE_CAP;
    }

    private void emitAutomatic(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int sent = 0;

        for (int[] offset : SAMPLE_OFFSETS) {
            if (sent >= AUTO_PARTICLE_CAP) {
                break;
            }

            int x = center.getX() + offset[0];
            int z = center.getZ() + offset[1];
            BlockPos loadedProbe = new BlockPos(x, center.getY(), z);
            if (!level.hasChunkAt(loadedProbe)) {
                continue;
            }

            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surface = new BlockPos(x, surfaceY - 1, z);
            if (!level.hasChunkAt(surface)) {
                continue;
            }

            if (level.getFluidState(surface).is(FluidTags.WATER)) {
                int count = Math.min(3, AUTO_PARTICLE_CAP - sent);
                sendToPlayer(player, x + 0.5, surfaceY + 0.35, z + 0.5, count, 2.0, 0.25, 2.0, 0.01);
                sent += count;
            } else if (surfaceY >= level.getSeaLevel() + HIGH_TERRAIN_ABOVE_SEA) {
                int count = Math.min(4, AUTO_PARTICLE_CAP - sent);
                sendToPlayer(player, x + 0.5, surfaceY + 5.0, z + 0.5, count, 3.0, 0.8, 3.0, 0.015);
                sent += count;
            }
        }
    }

    private void sendToPlayer(
        ServerPlayer player,
        double x,
        double y,
        double z,
        int count,
        double spreadX,
        double spreadY,
        double spreadZ,
        double speed
    ) {
        if (player.serverLevel().sendParticles(
            player, ParticleTypes.CLOUD, false, x, y, z, count, spreadX, spreadY, spreadZ, speed)) {
            particlesSent += count;
        }
    }
}
