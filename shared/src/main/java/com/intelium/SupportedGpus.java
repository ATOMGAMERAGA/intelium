package com.intelium;

import java.util.List;

/**
 * Curated catalogue of the Intel GPU families Intelium supports, ordered oldest
 * to newest. This is the single source of truth shared by the README and the
 * in-game "Supported GPUs" screen so they never drift apart.
 *
 * <p>The support cutoff is Gen 9 "Skylake" (HD Graphics 520 and its
 * generation-mates). Broadwell and older are intentionally absent - they are
 * recognized by the detector but reported unsupported.
 */
public final class SupportedGpus {

    private SupportedGpus() {}

    /** One supported Intel GPU family. */
    public record Family(String generation, String architecture, String years, String examples) {}

    /** Supported families, oldest release first. */
    public static final List<Family> SUPPORTED = List.of(
            new Family(
                    "Gen 9 Skylake", "Gen 9", "2015",
                    "HD Graphics 510, 515, 520, 530 · Iris Graphics 540, 550 · Iris Pro 580"),
            new Family(
                    "Gen 9.5 Kaby / Coffee / Comet Lake", "Gen 9.5", "2016–2020",
                    "HD / UHD Graphics 610, 620, 630"),
            new Family(
                    "Gen 11 Ice Lake", "Gen 11", "2019",
                    "Iris Plus Graphics G4, G7"),
            new Family(
                    "Xe-LP", "Gen 12", "2020–2023",
                    "Iris Xe Graphics (Tiger / Alder Lake) · UHD Graphics 710, 730, 750, 770 "
                            + "· Iris Xe MAX (DG1)"),
            new Family(
                    "Arc Alchemist", "Xe-HPG", "2022",
                    "Arc A310, A380, A580, A750, A770 · mobile A350M–A770M · Pro A30M–A60"),
            new Family(
                    "Core Ultra integrated Arc", "Xe-LPG / Xe2-LPG", "2023–2024",
                    "Intel Arc Graphics (Meteor Lake, Lunar Lake)"),
            new Family(
                    "Arc Battlemage", "Xe2-HPG", "2024–2026",
                    "Arc B570, B580, B770")
    );
}
