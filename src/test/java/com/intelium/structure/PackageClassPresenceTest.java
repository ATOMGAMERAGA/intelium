package com.intelium.structure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Compiled classes exist with expected fully-qualified names")
class PackageClassPresenceTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "com.intelium.Intelium",
            "com.intelium.IntelGpuDetector",
            "com.intelium.IntelGpuGeneration",
            "com.intelium.config.InteliumConfig",
            "com.intelium.config.InteliumConfigIO",
            "com.intelium.config.InteliumConfigEntryPoint",
            "com.intelium.optimization.ChunkBuilderTuner",
            "com.intelium.optimization.DrawCallBatcher",
            "com.intelium.optimization.BufferStrategy",
            "com.intelium.optimization.OcclusionTuner"
    })
    @DisplayName("Class is loadable")
    void classLoads(String fqcn) {
        assertDoesNotThrow(() -> Class.forName(fqcn),
                "Class.forName failed for " + fqcn);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.intelium.mixin.MixinRenderSectionManager",
            "com.intelium.mixin.MixinSodiumWorldRenderer",
            "com.intelium.mixin.MixinChunkBuilder",
            "com.intelium.mixin.MixinDefaultChunkRenderer"
    })
    @DisplayName("Mixin class is loadable (initialize=false to skip target resolution)")
    void mixinLoads(String fqcn) {
        // Use initialize=false so loading does not trigger the mixin target class lookup,
        // which depends on Sodium's internals. Class.forName(name, false, loader) just
        // resolves the byte code, which is enough to prove our source compiles cleanly.
        assertDoesNotThrow(() -> Class.forName(fqcn, false,
                        PackageClassPresenceTest.class.getClassLoader()),
                "Class.forName failed for " + fqcn);
    }

    @Test
    @DisplayName("Intelium class is concrete (not abstract)")
    void inteliumNotAbstract() throws ClassNotFoundException {
        Class<?> c = Class.forName("com.intelium.Intelium");
        assertFalse(java.lang.reflect.Modifier.isAbstract(c.getModifiers()));
    }

    @Test
    @DisplayName("IntelGpuGeneration is enum")
    void generationIsEnum() throws ClassNotFoundException {
        Class<?> c = Class.forName("com.intelium.IntelGpuGeneration");
        assertTrue(c.isEnum());
    }

    @Test
    @DisplayName("All helper classes in optimization package are final")
    void helpersAreFinal() throws ClassNotFoundException {
        for (String n : new String[]{
                "com.intelium.optimization.ChunkBuilderTuner",
                "com.intelium.optimization.DrawCallBatcher",
                "com.intelium.optimization.BufferStrategy",
                "com.intelium.optimization.OcclusionTuner"
        }) {
            Class<?> c = Class.forName(n);
            assertTrue(java.lang.reflect.Modifier.isFinal(c.getModifiers()), n + " should be final");
        }
    }

    @Test
    @DisplayName("All mixin classes are abstract")
    void mixinsAreAbstract() throws ClassNotFoundException {
        ClassLoader loader = PackageClassPresenceTest.class.getClassLoader();
        for (String n : new String[]{
                "com.intelium.mixin.MixinRenderSectionManager",
                "com.intelium.mixin.MixinSodiumWorldRenderer",
                "com.intelium.mixin.MixinChunkBuilder",
                "com.intelium.mixin.MixinDefaultChunkRenderer"
        }) {
            Class<?> c = Class.forName(n, false, loader);
            assertTrue(java.lang.reflect.Modifier.isAbstract(c.getModifiers()),
                    n + " should be abstract (mixin convention)");
        }
    }

    @Test
    @DisplayName("InteliumConfig is plain POJO (not final, has fields)")
    void configIsPojo() throws ClassNotFoundException {
        Class<?> c = Class.forName("com.intelium.config.InteliumConfig");
        assertFalse(java.lang.reflect.Modifier.isAbstract(c.getModifiers()));
        assertTrue(c.getDeclaredFields().length >= 6);
    }
}
