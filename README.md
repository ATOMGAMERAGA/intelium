<p align="center">
  <img src="assets/branding/logo-256.png" alt="Intelium" width="160" height="160">
</p>

# Intelium

**Intelium** is a Fabric mod that tunes Sodium for Intel GPUs and gives you
clear, in-game visibility into whether your GPU is supported. It applies a
generation- and profile-aware chunk-build worker count, plus a set of opt-in
live render tweaks that cut per-frame GPU/CPU cost while you walk and look
around. On NVIDIA, AMD, unrecognized, or too-old Intel GPUs it disables itself
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
| Chunk build threading | Overrides Sodium's chunk-build worker count with a generation- and profile-aware value. It scales with your CPU and reserves headroom for the render thread, so chunks keep up while you move (no hitch when new chunks enter view) without starving the frame. Honors a manual override. |
| Fast chunk loading | Overrides Sodium's chunk **defer mode** — which ships at the slowest setting (`Always`) — so freshly meshed chunks become visible much sooner, and boosts build throughput. **Fast** = one-frame delay (recommended), **Turbo** = zero-frame (fastest). Self-disables cleanly if a Sodium build moves the setting. |
| Live render tweaks | Opt-in caps on vanilla settings that cost real per-frame GPU/CPU time on weak iGPUs: entity render distance, particles, entity shadows, and biome blending. Each captures your original value and restores it when turned off. |
| Optimization profile | **Max FPS / Balanced / Smooth** — shifts the chunk-worker trade-off toward peak frame rate or toward steady frame times while walking and turning. |
| Stutter visibility | The overlay shows the **1% low** and **minimum** FPS over the last few seconds, so you can see hitches, not just the headline average. |
| GPU detection | Identifies the exact Intel generation from the GL renderer string on both Windows drivers and Linux/Mesa, and reports support status in-game and in the log. |
| Honest gating | Disables itself cleanly on NVIDIA / AMD / unrecognized / too-old GPUs. A Mixin config plugin checks each hook's Sodium target at load time, so any compatible Sodium version works and incompatible internals self-disable instead of crashing. |

> **Why these levers and not draw-call batching / persistent buffers?**
> Sodium 0.8 already issues batched `glMultiDrawElementsIndirect` draws, manages
> chunk geometry in a GPU memory arena, and performs occlusion culling.
> Re-implementing those is redundant and risks regressions, so Intelium does not
> ship placebo switches. Instead it pulls the levers Sodium leaves to the player
> — entity distance, particles, shadows, biome blend, worker count — and wires
> each to a real, reversible effect.

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

Settings are split across two pages: **General** (core + render tweaks) and
**Overlay & Test**.

**General → Core**

| Option | Default | Notes |
|---|---|---|
| Enable Intelium | `true` | Master switch. Greyed out when the GPU is unsupported. |
| Optimization Profile | `Balanced` | **Max FPS** favors peak frame rate (fewer workers); **Smooth** favors stable frame times while moving (more workers); **Balanced** is the middle. |
| Chunk Build Workers | `Auto` | `0` / Auto = generation- and profile-aware default; `1–16` overrides Sodium's worker count directly. |
| Fast Chunk Loading | `Fast` | **Off** leaves Sodium's defer mode; **Fast** = one-frame deferral (chunks appear much sooner, minimal cost); **Turbo** = zero-frame (fastest, may cost some smoothness). Also boosts build throughput. |

**General → Render Tweaks** (applied live to vanilla settings; your originals are restored when turned off)

| Option | Default | Notes |
|---|---|---|
| Live Render Tweaks | `true` | Master switch for the four levers below. |
| Max Entity Distance | `80%` | Caps how far entities render (50–100%; `Full` = untouched). Lower culls distant mobs/items — a real FPS win in crowded scenes. |
| Limit Particles | `true` | Caps particles to *Decreased* (never overrides a stricter setting). |
| Disable Entity Shadows | `false` | Turns off the round shadows under entities. |
| Fast Biome Blend | `false` | Forces biome blend to 0. Biome blending runs on the chunk-build thread, so this makes meshing much cheaper and cuts hitches when chunks stream in. |

**Overlay & Test**

| Option | Default | Notes |
|---|---|---|
| FPS Test Overlay | `false` | Toggles the movable on-screen FPS panel. |
| Compact Overlay | `false` | Show only the title + FPS (+ lows) lines. |
| Show 1% Low / Min | `true` | Adds a stutter line: 1% low and minimum FPS over the last ~10s. |
| Edit Overlay / Benchmark | button | Opens edit mode (drag to reposition) and runs the A/B benchmark. |
| Supported GPUs | button | Opens an in-game list of supported generations and your detected GPU's status. |

Changes apply live: toggling Intelium, changing the profile/worker count, or
flipping a render tweak takes effect immediately (chunks rebuild where needed),
so you see the effect without restarting. When the detected GPU (or Sodium
build) is unsupported, every interactive option is greyed out and the reason is
shown in the option tooltips and on the Supported GPUs screen.

### FPS test overlay

Enable **FPS Test Overlay** to show a movable panel with your live FPS. Open
**Edit Overlay / Benchmark** to:

- **Drag** the panel anywhere on screen (position saves automatically).
- **Run A/B Benchmark** — Intelium measures average FPS with its effect *on*,
  then toggles it *off* (restoring vanilla settings and rebuilding chunks between
  windows) and measures again, and reports both figures plus the gain. This is a
  real, measured comparison — not an estimate. Each phase uses a long warmup so
  the chunk-rebuild spike passes *before* the measurement window opens, which
  keeps the ON phase from being unfairly penalised. The gain reflects the active
  render tweaks and worker count; on a fully-built static scene with no render
  tweaks enabled the delta can legitimately be near zero.

## Building

```bash
./gradlew build
```

The mod jar lands in `build/libs/intelium-<version>.jar`.

## License

GPL-3.0-only. See `LICENSE`.
