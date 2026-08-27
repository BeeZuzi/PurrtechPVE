package eu.purrtech.purrtechPVE.mythicmobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mythic-Dist is declared {@code compileOnly} in build.gradle.kts, so it is
 * genuinely absent from this test's runtime classpath too - the exact same
 * situation an incompatible/mismatched "MythicMobs" build puts a real server
 * in (a plugin literally named MythicMobs can be enabled, satisfying {@code
 * isPluginEnabled}, while its classes don't match this API at all). This
 * reproduces the {@code NoClassDefFoundError} seen in production and pins
 * down that {@link MythicMobsBridge#probe()} is the thing that surfaces it -
 * {@code PurrtechPVE.onEnable} wraps exactly this call in {@code catch
 * (Throwable)} so the mismatch degrades to "no MythicMobs integration"
 * instead of crashing every damage event (that part needs a live server to
 * exercise, but {@code catch (Throwable)} catching a {@link
 * NoClassDefFoundError} - a {@link LinkageError} - is guaranteed by the JLS,
 * not something that needs its own test).
 */
class MythicMobsBridgeTest {

    @Test
    void probeThrowsWhenMythicMobsApiIsAbsentAtRuntime() {
        MythicMobsBridge bridge = new MythicMobsBridge();
        assertThrows(NoClassDefFoundError.class, bridge::probe);
    }

    @Test
    void isMythicMobThrowsTheSameWayWhenApiIsAbsent() {
        MythicMobsBridge bridge = new MythicMobsBridge();
        assertThrows(NoClassDefFoundError.class, () -> bridge.isMythicMob(null));
    }

    @Test
    void mythicMobInternalNameThrowsTheSameWayWhenApiIsAbsent() {
        MythicMobsBridge bridge = new MythicMobsBridge();
        assertThrows(NoClassDefFoundError.class, () -> bridge.mythicMobInternalName(null));
    }
}
