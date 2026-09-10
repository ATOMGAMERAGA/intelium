package com.intelium;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects the graphics device selected by Minecraft 26.x and publishes the
 * shared {@link IntelGpuClassifier} decision.
 *
 * <p>Minecraft 26.2 can run through Vulkan, where no OpenGL context exists.
 * Blaze3D already exposes the selected device through {@code DeviceInfo}; that
 * is the authoritative source because it describes the GPU Minecraft is
 * actually rendering on (important on hybrid-GPU laptops). Access is reflective
 * so this one 26.x jar remains fail-soft on 26.1 builds whose device API may
 * differ. OpenGL's vendor/renderer strings remain a safe fallback for 26.1.
 *
 * <p>Detection retries while the device is still starting, then fails closed:
 * Intelium never guesses an Intel profile and never touches OpenGL without a
 * current context.
 */
public final class IntelGpuDetector {

    private static final AtomicBoolean DETECTED = new AtomicBoolean(false);
    private static final int MAX_ATTEMPTS = 100;

    /** Only touched by render-thread call sites. */
    private static int attempts;

    private IntelGpuDetector() {}

    /** Runs until a trustworthy device identity is available, then latches. */
    public static void detectOnce() {
        if (DETECTED.get()) return;
        if (!Intelium.SODIUM_OK) {
            DETECTED.set(true);
            return;
        }

        DeviceSnapshot device = readBlaze3dDevice();
        if (!device.hasIdentity() && glContextCurrent()) {
            device = new DeviceSnapshot(
                    safeGlString(GL11.GL_VENDOR),
                    safeGlString(GL11.GL_RENDERER),
                    "OpenGL",
                    "");
        }

        if (!device.hasIdentity()) {
            if (++attempts < MAX_ATTEMPTS) return;
            if (!DETECTED.compareAndSet(false, true)) return;
            Intelium.DETECTED_RENDERER = "";
            Intelium.DETECTED_BACKEND = RenderBackend.UNKNOWN;
            Intelium.DETECTED_GENERATION = IntelGpuGeneration.UNKNOWN;
            Intelium.IS_COMPATIBLE = false;
            Intelium.DISABLED_REASON_KEY = "intelium.disabled.device_unavailable";
            Capabilities.set(Capability.GPU_DETECTION, false,
                    "Blaze3D exposed no usable graphics device identity");
            Intelium.LOGGER.info("Intelium status: Blaze3D did not expose a usable graphics "
                    + "device identity - staying inactive instead of guessing.");
            return;
        }

        if (!DETECTED.compareAndSet(false, true)) return;
        IntelGpuClassifier.Result result =
                IntelGpuClassifier.decide(device.vendor(), device.name());
        // 26.1 and 26.2 both default to OpenGL, with Vulkan an opt-in that
        // names itself. A device that reports no backend name at all is
        // therefore running OpenGL, and treating that as UNKNOWN would throw
        // away the whole OpenGL tuning path on exactly the machines this mod
        // exists for. A name that is present but unrecognised still resolves to
        // UNKNOWN - see RenderBackend.resolveDefaultOpenGl.
        RenderBackend backend = RenderBackend.resolveDefaultOpenGl(device.backend());

        Intelium.DETECTED_RENDERER = device.name();
        Intelium.DETECTED_BACKEND = backend;
        Intelium.DETECTED_GENERATION = result.generation;
        Intelium.IS_COMPATIBLE = result.compatible;
        Intelium.DISABLED_REASON_KEY = result.reasonKey;
        Capabilities.set(Capability.GPU_DETECTION, true, null);

        Intelium.LOGGER.info(
                "Intelium status: vendor='{}' device='{}' backend='{}' driver='{}' "
                        + "detected={} active={}{}",
                device.vendor(), device.name(), device.backend(), device.driver(),
                result.generation.display, result.compatible,
                result.reasonKey == null ? "" : " (reason=" + result.reasonKey + ")");
    }

    /**
     * Reads DeviceInfo without creating a hard binary dependency on its 26.2
     * shape. Every named method is optional; failure simply allows a later tick
     * or the OpenGL fallback to try again.
     */
    private static DeviceSnapshot readBlaze3dDevice() {
        try {
            Class<?> renderSystem = Class.forName(
                    "com.mojang.blaze3d.systems.RenderSystem", false,
                    IntelGpuDetector.class.getClassLoader());
            Method getDevice = renderSystem.getMethod("getDevice");
            Object device = getDevice.invoke(null);
            if (device == null) return DeviceSnapshot.EMPTY;

            Class<?> deviceType = getDevice.getReturnType();
            Method getDeviceInfo = deviceType.getMethod("getDeviceInfo");
            Object info = getDeviceInfo.invoke(device);
            if (info == null) return DeviceSnapshot.EMPTY;

            Class<?> infoType = getDeviceInfo.getReturnType();
            return new DeviceSnapshot(
                    invokeString(infoType, info, "vendorName"),
                    invokeString(infoType, info, "name"),
                    invokeString(infoType, info, "backendName"),
                    invokeString(infoType, info, "driverInfo"));
        } catch (Throwable ignored) {
            return DeviceSnapshot.EMPTY;
        }
    }

    private static String invokeString(Class<?> owner, Object target, String methodName) {
        try {
            Object value = owner.getMethod(methodName).invoke(target);
            return value == null ? "" : value.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean glContextCurrent() {
        try {
            GL.getCapabilities();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String safeGlString(int name) {
        try {
            String value = GL11.glGetString(name);
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private record DeviceSnapshot(String vendor, String name, String backend, String driver) {
        private static final DeviceSnapshot EMPTY = new DeviceSnapshot("", "", "", "");

        private DeviceSnapshot {
            vendor = normalize(vendor);
            name = normalize(name);
            backend = normalize(backend);
            driver = normalize(driver);
        }

        private boolean hasIdentity() {
            return !vendor.isEmpty() || !name.isEmpty();
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
