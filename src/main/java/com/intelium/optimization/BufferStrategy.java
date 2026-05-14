package com.intelium.optimization;

import com.intelium.IntelGpuGeneration;
import org.lwjgl.opengl.GL44;

public final class BufferStrategy {

    public static final int PERSISTENT_FLAGS =
            GL44.GL_MAP_PERSISTENT_BIT |
            GL44.GL_MAP_COHERENT_BIT  |
            GL44.GL_MAP_WRITE_BIT;

    private BufferStrategy() {}

    public static boolean usePersistent(IntelGpuGeneration gen) {
        return gen.supported;
    }
}
