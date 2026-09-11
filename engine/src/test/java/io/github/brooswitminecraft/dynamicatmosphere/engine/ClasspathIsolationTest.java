package io.github.brooswitminecraft.dynamicatmosphere.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Proves, rather than assumes, that :engine's test classpath carries no
 * Minecraft or NeoForge classes. This complements (does not replace) the
 * {@code verifyNoMinecraft} Gradle task in build.gradle: that task checks
 * the *resolved dependency graph*, this test checks what the JVM can
 * actually {@code Class.forName} at runtime — a belt-and-suspenders pair,
 * since a classpath entry could in principle be added without being a
 * resolved Gradle dependency (e.g. a stray jar).
 *
 * <p>Both class names below were verified as real, currently-shipping
 * classes — not guesses — by reading neoforged/MDK's example mod for
 * Minecraft 1.21.1 / NeoForge (the {@code archive/1.21-mdg} branch's
 * {@code ExampleMod.java}, fetched 2026-09-11): it imports and uses
 * {@code net.minecraft.world.level.block.Blocks} (a vanilla class) and is
 * itself annotated with {@code net.neoforged.fml.common.Mod} (the NeoForge
 * mod-loader annotation). A typo'd class name here would make this test
 * pass for the wrong reason forever, which is exactly what verifying
 * against a real source file rules out.
 */
class ClasspathIsolationTest {

    private static final String A_REAL_MINECRAFT_CLASS = "net.minecraft.world.level.block.Blocks";

    private static final String A_REAL_NEOFORGE_CLASS = "net.neoforged.fml.common.Mod";

    @Test
    void minecraftIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(A_REAL_MINECRAFT_CLASS));
    }

    @Test
    void neoForgeIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(A_REAL_NEOFORGE_CLASS));
    }
}
