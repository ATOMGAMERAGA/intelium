<p align="center">
  <img src="assets/branding/logo-256.png" alt="Intelium" width="160" height="160">
</p>

# Intelium

**Intelium** is a Fabric mod that tunes Sodium for Intel GPUs and gives you
clear, in-game visibility into whether your GPU is supported. It applies a
generation-aware chunk-build worker count and otherwise stays out of Sodium's
way. On NVIDIA, AMD, unrecognized, or too-old Intel GPUs it disables itself
cleanly and Sodium runs unmodified.

## Branding assets

Universal logo (square, transparent PNG, sRGB) lives under
`assets/branding/`:

| File | Size | Use case |
|---|---|---|
| `logo-512.png` | 512x512 | Modrinth / CurseForge cover, web |
| `logo-256.png` | 256x256 | README, docs, medium previews |
| `logo-128.png` | 128x128 | Source master |
| `logo-64.png`  | 64x64   | Favicon, small UI |

The in-game / Fabric loader icon is generated from the same source at 32x32
in `src/main/resources/assets/intelium/icon.png`.

## What it does

| Area | What Intelium does |
|---|---|
| Chunk build threading | Overrides Sodium's chunk-build worker count with a generation-aware value (lower on weak iGPUs, higher on Iris Xe / Arc). Honors a manual override. |
| GPU detection | Identifies the exact Intel generation from the GL renderer string on both Windows drivers and Linux/Mesa, and reports support status in-game and in the log. |
| Honest gating | Disables itself cleanly on NVIDIA / AMD / unrecognized / too-old GPUs. A Mixin config plugin checks each hook's Sodium target at load time, so any compatible Sodium version works and incompatible internals self-disable instead of crashing. |

> **Why no draw-call batching / persistent buffers / occlusion culling?**
> Sodium 0.8 already issues batched `glMultiDrawElementsIndirect` draws, manages
> chunk geometry in a GPU memory arena, and performs occlusion culling.
> Re-implementing those on top of Sodium is redundant and risks regressions or
> crashes, so Intelium does not ship placebo switches for them.

Intelium auto-disables on NVIDIA, AMD, unrecognized, or unknown GPUs, and on
Intel parts older than HD Graphics 520 (Gen 9 / Skylake, 2015). When disabled,
its options are greyed out and the reason is shown both in Sodium's video
settings and on the in-game **Supported GPUs** screen.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.18.3+**
- Fabric API
- **Sodium 0.8.0 or newer** — any version compatible with your Minecraft. Intelium
  does not cap the Sodium version: if a newer Sodium changes the internals a hook
  relies on, that hook self-disables cleanly (no crash) and everything else keeps
  working.
- Java 21
- An Intel GPU (HD 520 / Gen 9 Skylake or newer)

## Supported Intel generations

The support cutoff is **Gen 9 "Skylake" (HD Graphics 520 and its
generation-mates)**. Everything from Skylake onward is supported; Broadwell
(2014) and older are recognized but reported unsupported. The same list is
shown in-game via the **Supported GPUs** button on Intelium's settings page,
ordered oldest to newest.

| Generation | Architecture | Years | Examples |
|---|---|---|---|
| Gen 9 Skylake | Gen 9 | 2015 | HD Graphics 510, 515, 520, 530 · Iris Graphics 540, 550 · Iris Pro 580 |
| Gen 9.5 Kaby / Coffee / Comet Lake | Gen 9.5 | 2016–2020 | HD / UHD Graphics 610, 620, 630 |
| Gen 11 Ice Lake | Gen 11 | 2019 | Iris Plus Graphics G4, G7 |
| Xe-LP | Gen 12 | 2020–2023 | Iris Xe Graphics (Tiger / Alder Lake) · UHD Graphics 710, 730, 750, 770 · Iris Xe MAX (DG1) |
| Arc Alchemist | Xe-HPG | 2022 | Arc A310, A380, A580, A750, A770 · mobile A350M–A770M · Pro A30M–A60 |
| Core Ultra integrated Arc | Xe-LPG / Xe2-LPG | 2023–2024 | Intel Arc Graphics (Meteor Lake, Lunar Lake) |
| Arc Battlemage | Xe2-HPG | 2024–2026 | Arc B570, B580, B770 |

### Not supported (Broadwell and older)

Recognized but disabled — Sodium runs unmodified: original Intel HD Graphics
(Ironlake/Westmere), HD Graphics 2000/3000 (Sandy Bridge), HD 2500/4000 (Ivy
Bridge), HD 4200–5000 / Iris 5100 / Iris Pro 5200 (Haswell), and HD
5300/5500/6000 / Iris 6100 / Iris Pro 6200 (Broadwell).

## Configuration

Settings are exposed inside Sodium's Video Settings screen under an
**Intelium** page (Sodium 0.8 Config API). Persisted to
`config/intelium.json`.

| Option | Default | Notes |
|---|---|---|
| Enable Intelium | `true` | Master switch. Greyed out when the GPU is unsupported. |
| Chunk Build Workers | `Auto` | `0` / Auto = generation-aware default; `1–16` overrides Sodium's worker count directly. |
| Supported GPUs | button | Opens an in-game list of supported generations and your detected GPU's status. |

When the detected GPU (or Sodium build) is unsupported, every interactive
option is greyed out and the reason is shown in the option tooltips and on the
Supported GPUs screen.

## Building

```bash
./gradlew build
```

The mod jar lands in `build/libs/intelium-<version>.jar`.

## License

GPL-3.0-only. See `LICENSE`.
