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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;

/**
 * Debug-grade Cloud/Fog visual delivery spike. This is intentionally not a
 * terrain material simulation: it samples a few loaded surface columns and
 * sends bounded vanilla particles to nearby players.
 */
final class ForgeAtmospherePrototype {

    private static final int SAMPLE_INTERVAL = 100;
    private static final int RENDER_INTERVAL = 20;
    private static final int HIGH_TERRAIN_ABOVE_SEA = 24;
    private static final int AUTO_PARTICLE_CAP = 16;
    private static final int FOG_PARTICLE_CAP = 32;
    private static final int FOG_PACKET_CAP = 6;
    private static final int DEMO_PARTICLE_CAP = 24;
    private static final int MAX_FOG_PATCHES = 96;
    private static final int FOG_EXPIRY_TICKS = 1000;
    private static final int MAX_WATER_DEPTH = 8;
    private static final int FOG_RADIUS = 32;
    private static final int FOG_VERTICAL_RADIUS = 32;
    private static final int[][] SAMPLE_OFFSETS = {
        {0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}, {8, 8}, {-8, -8}, {8, -8}
    };

    private long serverTicks;
    private long automaticPasses;
    private long particlesSent;
    private long fogParticlesSent;
    private long fogPatchesExpired;
    private int playersLastPass;
    private final FogPatchTracker<FogPatchKey> fogPatches =
        new FogPatchTracker<>(MAX_FOG_PATCHES, FOG_EXPIRY_TICKS, SAMPLE_INTERVAL);

    void onServerTick(ServerTickEvent.Post event) {
        serverTicks++;
        if (serverTicks % SAMPLE_INTERVAL == 0) {
            automaticPasses++;
            playersLastPass = 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                playersLastPass++;
                sampleAutomatic(player);
            }
            fogPatchesExpired += fogPatches.advance(serverTicks);
        }
        if (serverTicks % RENDER_INTERVAL == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                renderFog(player);
            }
        }
    }

    void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    void onServerStopped(ServerStoppedEvent event) {
        serverTicks = 0;
        automaticPasses = 0;
        particlesSent = 0;
        fogParticlesSent = 0;
        fogPatchesExpired = 0;
        playersLastPass = 0;
        fogPatches.clear();
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dynamicatmosphere")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("demo").executes(context -> demo(context.getSource()))));
    }

    private int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "Dynamic Atmosphere visual delivery spike: active; sampleInterval=" + SAMPLE_INTERVAL
                + " ticks, renderInterval=" + RENDER_INTERVAL
                + " ticks, cloudCap=" + AUTO_PARTICLE_CAP
                + ", fogParticleCap=" + FOG_PARTICLE_CAP
                + ", fogPacketCap=" + FOG_PACKET_CAP
                + ", passes=" + automaticPasses
                + ", particles=" + particlesSent
                + ", playersLastPass=" + playersLastPass
                + ", fogPatches=" + fogPatches.size() + "/" + MAX_FOG_PATCHES
                + ", fogParticles=" + fogParticlesSent
                + ", fogExpired=" + fogPatchesExpired
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

    private void sampleAutomatic(ServerPlayer player) {
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
                int depth = sampleLoadedWaterDepth(level, surface);
                fogPatches.observe(new FogPatchKey(level.dimension(), surface.immutable()), depth, serverTicks);
            } else if (surfaceY >= level.getSeaLevel() + HIGH_TERRAIN_ABOVE_SEA) {
                int count = Math.min(4, AUTO_PARTICLE_CAP - sent);
                sent += sendToPlayer(
                    player, x + 0.5, surfaceY + 5.0, z + 0.5, count, 3.0, 0.8, 3.0, 0.015);
            }
        }
    }

    private int sampleLoadedWaterDepth(ServerLevel level, BlockPos surface) {
        int depth = 0;
        for (int offset = 0; offset < MAX_WATER_DEPTH; offset++) {
            BlockPos sample = surface.below(offset);
            if (!level.hasChunkAt(sample) || !level.getFluidState(sample).is(FluidTags.WATER)) {
                break;
            }
            depth++;
        }
        return Math.max(1, depth);
    }

    private void renderFog(ServerPlayer player) {
        int sent = 0;
        int packets = 0;
        BlockPos playerPos = player.blockPosition();

        for (FogPatchTracker.Patch<FogPatchKey> patch : fogPatches.patches().stream()
            .filter(candidate -> candidate.key().dimension().equals(player.serverLevel().dimension()))
            .filter(candidate -> player.serverLevel().hasChunkAt(candidate.key().pos()))
            .filter(candidate -> isNear(playerPos, candidate.key().pos()))
            .sorted(Comparator.comparingLong(candidate -> horizontalDistanceSquared(playerPos, candidate.key().pos())))
            .toList()) {
            if (sent >= FOG_PARTICLE_CAP || packets >= FOG_PACKET_CAP) {
                break;
            }
            FogPatchKey key = patch.key();
            int count = Math.min(patch.strength(), FOG_PARTICLE_CAP - sent);
            int delivered = sendToPlayer(
                player,
                key.pos().getX() + 0.5,
                key.pos().getY() + 1.15,
                key.pos().getZ() + 0.5,
                count,
                2.8 + patch.strength() * 0.25,
                0.35,
                2.8 + patch.strength() * 0.25,
                0.005);
            sent += delivered;
            fogParticlesSent += delivered;
            packets++;
        }
    }

    private boolean isNear(BlockPos player, BlockPos patch) {
        long dy = Math.abs((long) player.getY() - patch.getY());
        return dy <= FOG_VERTICAL_RADIUS
            && horizontalDistanceSquared(player, patch) <= (long) FOG_RADIUS * FOG_RADIUS;
    }

    private long horizontalDistanceSquared(BlockPos player, BlockPos patch) {
        long dx = (long) player.getX() - patch.getX();
        long dz = (long) player.getZ() - patch.getZ();
        return dx * dx + dz * dz;
    }

    private int sendToPlayer(
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
            return count;
        }
        return 0;
    }

    private record FogPatchKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
