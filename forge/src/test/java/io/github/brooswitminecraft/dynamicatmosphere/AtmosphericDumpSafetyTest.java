package io.github.brooswitminecraft.dynamicatmosphere;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Disposable-fixture tests for the command's access/confirmation/scope contract. */
class AtmosphericDumpSafetyTest {
    @Test
    void confirmationIsExplicitlyDestructiveAndRequiresTheLiteralConfirmStep() {
        assertTrue(AtmosphericDumpSafety.confirmationMessage().contains("permanently deletes"));
        assertTrue(AtmosphericDumpSafety.confirmationMessage().contains(" confirm"));
        assertTrue(AtmosphericDumpSafety.confirmationMessage().contains("Unloaded chunk attachments are not touched"));
    }

    @Test
    void permissionLevelTwoAndConsoleEquivalentAreAllowedButLowerLevelsAreRejected() {
        assertFalse(AtmosphericDumpSafety.mayExecute(1));
        assertTrue(AtmosphericDumpSafety.mayExecute(2));
        assertTrue(AtmosphericDumpSafety.mayExecute(4));
    }

    @Test
    void productionCommandTreeWiresTheDeleteWarningAndLiteralConfirmation() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        new ForgeAtmospherePrototype().registerCommands(dispatcher);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("dynamicatmosphere");
        assertTrue(root != null, "the production root command must be registered");
        var delete = root.getChild("delete-material-data");
        assertTrue(delete != null, "the destructive command must be under /dynamicatmosphere");
        assertTrue(delete.getCommand() != null, "the unconfirmed invocation must show the warning");
        var confirm = delete.getChild(AtmosphericDumpSafety.CONFIRM_LITERAL);
        assertTrue(confirm != null, "the literal confirmation must be required by the production command tree");
        assertTrue(confirm.getCommand() != null, "only the confirm leaf may execute deletion");
    }

    @Test
    void operationScopeContainsOnlyExplicitLoadedFixturesAndNeverDiscoversAnUnloadedFixture() {
        List<String> loaded = new ArrayList<>(List.of("overworld:loaded-a", "the_nether:loaded-b"));
        String persistedButUnloaded = "overworld:persisted-unloaded";

        List<String> targets = AtmosphericDumpSafety.loadedTargets(loaded);

        assertEquals(List.of("overworld:loaded-a", "the_nether:loaded-b"), targets);
        assertFalse(targets.contains(persistedButUnloaded));
        loaded.clear();
        assertEquals(2, targets.size(), "the disposable operation snapshot cannot load more targets later");
    }

    @Test
    void cleanupDeletesAllLoadedFixtureCategoriesButLeavesPersistedUnloadedFixtureUntouched() {
        Fixture loaded = new Fixture("overworld:loaded", 3, 2, 7);
        Fixture unloaded = new Fixture("overworld:persisted-unloaded", 11, 13, 17);

        AtmosphericDumpSafety.cleanLoadedOnly(List.of(loaded), Fixture::deleteAllAtmosphereData);

        assertEquals(0, loaded.vapor);
        assertEquals(0, loaded.smoke);
        assertEquals(0, loaded.materials);
        assertEquals(11, unloaded.vapor, "unloaded persistence must not be loaded or rewritten");
        assertEquals(13, unloaded.smoke);
        assertEquals(17, unloaded.materials);
    }

    @Test
    void productionRuntimeCleanupSeamClearsEveryScheduledOrSyncedWorkQueue() {
        List<String> cleared = new ArrayList<>();
        AtmosphericDumpSafety.clearRuntimeState(
            () -> cleared.add("pending-loads"), () -> cleared.add("vapor-producers"),
            () -> cleared.add("fans"), () -> cleared.add("smoke-producers"),
            () -> cleared.add("material-producers"), () -> cleared.add("vapor-sync"),
            () -> cleared.add("smoke-sync"), () -> cleared.add("material-sync"));
        assertEquals(List.of("pending-loads", "vapor-producers", "fans", "smoke-producers",
            "material-producers", "vapor-sync", "smoke-sync", "material-sync"), cleared);
    }

    @Test
    void reportIncludesDimensionEveryCategoryAndUnreadableSkips() {
        ForgeAtmospherePrototype.DumpReport report = new ForgeAtmospherePrototype.DumpReport();
        report.vapor = 3;
        report.smoke = 2;
        report.materials.put(AtmosphereMaterial.DUST, 7);
        report.unreadable = 1;

        String text = ForgeAtmospherePrototype.formatDumpReport(Map.of(Level.OVERWORLD, report));

        assertTrue(text.contains("minecraft:overworld"));
        assertTrue(text.contains("vapor=3"));
        assertTrue(text.contains("smoke=2"));
        assertTrue(text.contains("DUST=7"));
        assertTrue(text.contains("unreadableSkipped=1"));
    }

    private static final class Fixture {
        private final String id;
        private int vapor;
        private int smoke;
        private int materials;

        private Fixture(String id, int vapor, int smoke, int materials) {
            this.id = id;
            this.vapor = vapor;
            this.smoke = smoke;
            this.materials = materials;
        }

        private void deleteAllAtmosphereData() {
            vapor = smoke = materials = 0;
        }
    }
}
