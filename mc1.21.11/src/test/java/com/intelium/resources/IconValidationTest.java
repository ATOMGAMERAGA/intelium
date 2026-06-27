package com.intelium.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Icon PNG validation")
class IconValidationTest {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    @DisplayName("Main icon file exists")
    void mainIconExists() {
        assertTrue(Files.exists(TestPaths.iconMain()));
    }

    @Test
    @DisplayName("GUI icon file exists")
    void guiIconExists() {
        assertTrue(Files.exists(TestPaths.iconGui()));
    }

    @Test
    @DisplayName("Main icon has PNG signature")
    void mainIconIsPng() throws IOException {
        assertHasPngSignature(TestPaths.iconMain());
    }

    @Test
    @DisplayName("GUI icon has PNG signature")
    void guiIconIsPng() throws IOException {
        assertHasPngSignature(TestPaths.iconGui());
    }

    @Test
    @DisplayName("Main icon is 32x32")
    void mainIcon32x32() throws IOException {
        assertPngDimensions(TestPaths.iconMain(), 32, 32);
    }

    @Test
    @DisplayName("GUI icon is 32x32")
    void guiIcon32x32() throws IOException {
        assertPngDimensions(TestPaths.iconGui(), 32, 32);
    }

    @Test
    @DisplayName("Main icon is non-zero size")
    void mainIconNonEmpty() throws IOException {
        assertTrue(Files.size(TestPaths.iconMain()) > 0);
    }

    @Test
    @DisplayName("GUI icon is non-zero size")
    void guiIconNonEmpty() throws IOException {
        assertTrue(Files.size(TestPaths.iconGui()) > 0);
    }

    @Test
    @DisplayName("Main icon is below 64 KB (reasonable for embedded icon)")
    void mainIconSmall() throws IOException {
        assertTrue(Files.size(TestPaths.iconMain()) < 64 * 1024);
    }

    @Test
    @DisplayName("GUI icon is below 64 KB")
    void guiIconSmall() throws IOException {
        assertTrue(Files.size(TestPaths.iconGui()) < 64 * 1024);
    }

    private static void assertHasPngSignature(Path p) throws IOException {
        byte[] data = Files.readAllBytes(p);
        assertTrue(data.length >= 8, p + " is too small");
        for (int i = 0; i < 8; i++) {
            assertEquals(PNG_SIGNATURE[i], data[i],
                    p + " byte " + i + " mismatch");
        }
    }

    private static void assertPngDimensions(Path p, int w, int h) throws IOException {
        byte[] data = Files.readAllBytes(p);
        // PNG width and height live in big-endian at offset 16..23
        int width = ((data[16] & 0xff) << 24)
                | ((data[17] & 0xff) << 16)
                | ((data[18] & 0xff) << 8)
                | (data[19] & 0xff);
        int height = ((data[20] & 0xff) << 24)
                | ((data[21] & 0xff) << 16)
                | ((data[22] & 0xff) << 8)
                | (data[23] & 0xff);
        assertEquals(w, width, p + " width");
        assertEquals(h, height, p + " height");
    }
}
