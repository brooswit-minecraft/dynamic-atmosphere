package io.github.brooswitminecraft.dynamicatmosphere;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Compiled only with -PworldAcceptance. Every mutation is in the new CI-only disposable world. */
@EventBusSubscriber(modid = DynamicAtmosphereMod.MODID)
public final class WorldAcceptance {
    private static final BlockPos BASE = new BlockPos(520,100,520);
    private static final BlockPos EDGE = new BlockPos(16015,100,16015);
    private static final BlockPos FLORA = new BlockPos(776,0,776);
    private static final Map<String,Object> results = new LinkedHashMap<>();
    private static int ticks, edgeReadyTick;
    private static boolean finished;
    private static ForgeAtmospherePrototype prototype;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (finished) return;
        var server = event.getServer();
        var level = server.overworld();
        try {
            if (++ticks == 1) {
                var field = DynamicAtmosphereMod.class.getDeclaredField("prototype");
                field.setAccessible(true);
                prototype = (ForgeAtmospherePrototype) field.get(null);
                loadNeighborhood(level, BASE);
                loadNeighborhood(level, FLORA);
                clearRoom(level);
                // Only this far-away central chunk is FULL. Direct fixture writes avoid
                // notifying/loading neighbors before the production unloaded-edge check.
                var edge = level.getChunk(EDGE);
                edge.setBlockState(EDGE.below(), Blocks.MYCELIUM.defaultBlockState(), false);
                edge.setBlockState(EDGE, Blocks.AIR.defaultBlockState(), false);
            }
            if (ticks == 60) {
                check(prototype.materialState(level, AtmosphereMaterial.MOLD, BASE).isPresent(), "fixture chunks not imported");
                smallAndGiant(level);
                cows(level);
                producers(level);
                int beforeChunks = level.getChunkSource().getLoadedChunksCount();
                check(!level.hasChunkAt(EDGE.east()), "unloaded-edge fixture unexpectedly has its east neighbor");
                seed(level, EDGE, 100);
                int cost = prototype.moldGameplay().processCellAndConsume(level,
                        new BlockPos(16012,100,16012), new TargetRandom(3,0,3));
                check(cost == 0 && amount(level,EDGE) == 100 && level.getBlockState(EDGE).isAir(), "unloaded edge placed or debited");
                check(!level.hasChunkAt(EDGE.east()) && beforeChunks == level.getChunkSource().getLoadedChunksCount(), "placement loaded a chunk");
                results.put("unloaded_edge_no_placement_debit_or_load", true);
                loadNeighborhood(level, EDGE); // Explicit test setup, never production loading.
                edgeReadyTick = ticks + 30;
            }
            if (edgeReadyTick > 0 && ticks == edgeReadyTick) {
                seed(level, EDGE, 100);
                int cost = prototype.moldGameplay().processCellAndConsume(level,
                        new BlockPos(16012,100,16012), new TargetRandom(3,0,3));
                check(cost == 16 && amount(level,EDGE) == 84 && level.getBlockState(EDGE).is(Blocks.RED_MUSHROOM), "loaded edge did not place and debit exactly once");
                results.put("loaded_edge_success_exact_debit", true);
                results.put("ticks", ticks);
                finish(server, null);
            }
            if (ticks > 400) throw new IllegalStateException("fixture timeout");
        } catch (Throwable failure) { finish(server, failure); }
    }

    private static void smallAndGiant(ServerLevel level) {
        var gameplay = prototype.moldGameplay();
        seed(level, BASE, 15);
        check(gameplay.processCellAndConsume(level,BASE,new TargetRandom(0,0,0)) == 0
                && amount(level,BASE) == 15 && level.getBlockState(BASE).isAir(), "insufficient small funds mutated world");
        seed(level, BASE, 100);
        check(gameplay.processCellAndConsume(level,BASE,new TargetRandom(0,0,0)) == 16
                && amount(level,BASE) == 84 && level.getBlockState(BASE).is(Blocks.RED_MUSHROOM), "small mushroom success/debit");
        check(gameplay.processCellAndConsume(level,BASE,new TargetRandom(0,0,0)) == 0 && amount(level,BASE) == 84, "double debit");
        level.setBlock(BASE,Blocks.AIR.defaultBlockState(),3);
        level.setBlock(BASE.below(),Blocks.AIR.defaultBlockState(),3);
        seed(level,BASE,100);
        check(gameplay.processCellAndConsume(level,BASE,new TargetRandom(0,0,0)) == 0
                && amount(level,BASE) == 100 && level.getBlockState(BASE).isAir(), "invalid survival charged");
        results.put("small_success_failure_insufficient_exact_debit", true);
        for (var mushroom : new net.minecraft.world.level.block.Block[] {Blocks.RED_MUSHROOM,Blocks.BROWN_MUSHROOM}) {
            clearRoom(level);
            level.setBlock(BASE,mushroom.defaultBlockState(),3);
            level.setBlock(BASE.above(),Blocks.STONE.defaultBlockState(),3);
            seed(level,BASE,1000);
            check(gameplay.processCellAndConsume(level,BASE,new TargetRandom(0,0,0)) == 0
                    && amount(level,BASE) == 1000 && level.getBlockState(BASE).is(mushroom), "blocked giant charged or lost mushroom");
            level.setBlock(BASE.above(),Blocks.AIR.defaultBlockState(),3);
            check(gameplay.processCellAndConsume(level,BASE,new TargetRandom(0,0,0)) == 128
                    && amount(level,BASE) == 872 && level.getBlockState(BASE).is(Blocks.MUSHROOM_STEM), "giant success/exact debit");
            var cap = mushroom == Blocks.RED_MUSHROOM ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK;
            boolean found = false;
            for (BlockPos p : BlockPos.betweenClosed(BASE.offset(-5,1,-5),BASE.offset(5,15,5))) found |= level.getBlockState(p).is(cap);
            check(found,"wrong giant mushroom structure");
        }
        results.put("red_brown_vanilla_giants_success_failure_exact_debit",true);
        clearRoom(level);
    }

    private static void cows(ServerLevel level) {
        var cow = EntityType.COW.create(level);
        check(cow != null,"cow missing");
        cow.moveTo(BASE.getX()+.5,BASE.getY(),BASE.getZ()+.5,0,0);
        cow.setNoAi(true);
        check(level.addFreshEntity(cow),"cow spawn failed");
        seed(level,BASE,255);
        check(prototype.moldGameplay().processCow(cow,new TargetRandom(0,0,0)) == 0 && cow.isAlive()
                && amount(level,BASE) == 255,"insufficient cow conversion charged");
        seed(level,BASE,1000);
        check(prototype.moldGameplay().processCow(cow,new TargetRandom(0,0,0)) == 256
                && amount(level,BASE) == 744 && cow.isRemoved(),"cow conversion/debit");
        check(level.getEntitiesOfClass(MushroomCow.class,new AABB(BASE).inflate(2)).size() == 1,"mooshroom not present");
        check(prototype.moldGameplay().processCow(cow,new TargetRandom(0,0,0)) == 0 && amount(level,BASE) == 744,"cow double charge");
        results.put("cow_success_insufficient_no_double_charge",true);
    }

    @SuppressWarnings("unchecked")
    private static void producers(ServerLevel level) throws Exception {
        var chunk = level.getChunk(FLORA.getX() >> 4, FLORA.getZ() >> 4);
        var desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
        chunk.fillBiomesFromNoise((x,y,z,sampler) -> desert, level.getChunkSource().randomState().sampler());
        for (int x=768;x<784;x++) for(int z=768;z<784;z++) level.setBlock(new BlockPos(x,32,z),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(FLORA,Blocks.MOSS_BLOCK.defaultBlockState(),3);
        level.setBlock(FLORA.above(),Blocks.AIR.defaultBlockState(),3);
        BlockPos surface = new BlockPos(776,100,776);
        level.setBlock(surface,Blocks.SAND.defaultBlockState(),2);
        check(!level.canSeeSky(FLORA.above()),"cave fixture sees sky");
        var field = ForgeAtmospherePrototype.class.getDeclaredField("grid"); field.setAccessible(true);
        var grid = (AtmosphereGrid<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>>)field.get(prototype);
        var key = new AtmosphereGrid.CellKey<>(level.dimension(),194,0,194);
        int before = grid.get(key) == null ? 0 : grid.get(key).amount();
        int dustBefore = prototype.materialState(level,AtmosphereMaterial.DUST,surface.above()).orElseThrow().amount();
        int caveDust = prototype.materialState(level,AtmosphereMaterial.DUST,FLORA.above()).orElseThrow().amount();
        int chunks = level.getChunkSource().getLoadedChunksCount();
        var random = new FloraRandom(level.getHeight());
        long started = System.nanoTime();
        FloraProducers.sample(level,chunk,prototype,random);
        results.put("single_producer_call_nanoseconds_not_a_benchmark",System.nanoTime()-started);
        check(random.ints == 98,"producer exceeded 32*3 position draws plus two surface draws");
        check(grid.get(key).amount() == before+2,"bounded duplicate samples did not emit one underground Vapor source");
        check(prototype.materialState(level,AtmosphereMaterial.DUST,surface.above()).orElseThrow().amount() == dustBefore+1,"desert surface dust missing");
        check(prototype.materialState(level,AtmosphereMaterial.DUST,FLORA.above()).orElseThrow().amount() == caveDust,"ambient cave dust emitted");
        check(chunks == level.getChunkSource().getLoadedChunksCount(),"producer loaded chunks");
        results.put("underground_vapor_surface_only_dust_bounded_no_load",true);
    }

    private static void loadNeighborhood(ServerLevel level, BlockPos center) {
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) level.getChunk((center.getX()>>4)+dx,(center.getZ()>>4)+dz);
    }
    private static void clearRoom(ServerLevel level) {
        for(BlockPos p : BlockPos.betweenClosed(BASE.offset(-6,0,-6),BASE.offset(6,16,6))) level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
        for(BlockPos p : BlockPos.betweenClosed(BASE.offset(-6,-1,-6),BASE.offset(6,-1,6))) level.setBlock(p,Blocks.MYCELIUM.defaultBlockState(),2);
    }
    private static int amount(ServerLevel level, BlockPos pos) { return prototype.materialState(level,AtmosphereMaterial.MOLD,pos).orElseThrow().amount(); }
    private static void seed(ServerLevel level, BlockPos pos, int amount) {
        int old = amount(level,pos);
        if(old>0) check(prototype.consumeMaterial(level,AtmosphereMaterial.MOLD,pos,old),"clear Mold failed");
        check(prototype.emitMaterial(level,AtmosphereMaterial.MOLD,pos,amount),"seed Mold failed");
        check(amount(level,pos)==amount,"seed amount mismatch");
    }
    private static void check(boolean pass,String message) { if(!pass) throw new IllegalStateException(message); }
    private static void finish(net.minecraft.server.MinecraftServer server, Throwable failure) {
        finished=true; results.put("passed",failure==null);
        if(failure!=null) { results.put("failure",failure.toString()); failure.printStackTrace(); }
        try { Files.writeString(Path.of("world-acceptance-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results)); }
        catch(Exception e) { throw new RuntimeException(e); }
        System.out.println("WORLD_ACCEPTANCE_RESULT "+results);
        server.halt(false);
    }
    private static class TargetRandom extends LegacyRandomSource {
        private final int[] target; private int draws;
        TargetRandom(int x,int y,int z) { super(0); target=new int[]{x,y,z}; }
        @Override public int nextInt(int bound) { return bound==4 && draws<3 ? target[draws++] : 0; }
        @Override public double nextDouble() { return 0; }
        @Override public boolean nextBoolean() { return true; }
    }
    private static final class FloraRandom extends LegacyRandomSource {
        final int height; int ints;
        FloraRandom(int height) { super(0); this.height=height; }
        @Override public int nextInt(int bound) { ints++; return bound==height ? 64 : 8; }
        @Override public double nextDouble() { return 0; }
    }
}
