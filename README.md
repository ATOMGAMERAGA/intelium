<p align="center">
  <img src="assets/branding/logo-256.png" alt="Intelium" width="160" height="160">
</p>

# Intelium

**Intelium** is a Fabric mod that optimizes Sodium's rendering backend for
Intel GPUs. It is the Intel counterpart to Nvidium (which targets NVIDIA
GeForce cards) and stays out of the way on systems where it does not apply.

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

| Area | Optimization | Status |
|---|---|---|
| Chunk build threading | Tunes the number of chunk build worker threads against the GPU generation and the shared CPU/iGPU TDP. Hooks `ChunkBuilder.getThreadCount()` and only overrides when Sodium's own thread setting is on "auto". | **Active** |
| Occlusion culling | Picks a generation-aware visibility tightening factor each frame on weaker iGPUs. | Advisory |
| Draw-call submission | Picks a generation-aware indirect-draw batch size. | Advisory |
| Buffer strategy | Selects persistent-mapped-buffer flags (`GL_ARB_buffer_storage`) for capable generations. | Advisory |

**Active** features change Sodium's behaviour directly. **Advisory** features
compute generation-aware parameters that are exposed and unit-tested but do not
yet override Sodium's internal GL path — they are safe no-ops at runtime and are
guarded so they can never crash the game or conflict with other Sodium add-ons.
See [`KNOWN_LIMITATIONS`](#known-limitations).

Intelium auto-disables on NVIDIA, AMD, or unknown GPUs, and on Intel parts
older than HD Graphics 520 (Gen 9 / Skylake, 2015). On those systems Sodium
runs unmodified. Every hook is wrapped so a failure inside Intelium degrades to
"Sodium default" instead of crashing.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.18.3+**
- Fabric API
- **Sodium 0.8.x** (`>=0.8.0 <0.9.0`)
- Java 21
- An Intel GPU (HD 520 / Gen 9 Skylake or newer)

## Supported Intel generations

| Generation | Examples |
|---|---|
| Gen 9 Skylake | HD Graphics 520, 530 |
| Gen 9.5 Kaby/Coffee Lake | UHD Graphics 620, 630 |
| Gen 11 Ice Lake | Iris Plus G7 |
| Xe-LP / Gen 12 Tiger Lake | Iris Xe |
| Xe-HPG Arc Alchemist | Arc A310 - A770 |
| Xe2 Lunar / Battlemage | Arc B-series |

## Configuration

Settings are exposed inside Sodium's Video Settings screen under an
**Intelium** page (Sodium 0.8 Config API). Persisted to
`config/intelium.json`.

| Option | Default | Notes |
|---|---|---|
| Enable Intelium | `true` | Master switch |
| Draw Call Batching | `true` | Biggest win on Gen 9-12 |
| Persistent Mapped Buffers | `true` | Requires `GL_ARB_buffer_storage` |
| Aggressive Occlusion Culling | `true` | Trades CPU work for fewer vertices |
| Chunk Build Workers | `auto` | `-1` = generation-aware default |

## Building

```bash
./gradlew build
```

The mod jar lands in `build/libs/intelium-<version>.jar`.

## Known limitations

- Only the **chunk build worker tuning** changes Sodium's runtime behaviour
  today. Draw-call batching, persistent buffers and aggressive occlusion are
  generation-aware *advisors*: the parameters are computed and unit-tested, but
  wiring them into Sodium's internal GL path safely requires in-game profiling
  on real Intel hardware and is tracked as future work. They are deliberately
  inert at runtime so they cannot crash or break compatibility.
- Expect the biggest wins from worker tuning on low-end iGPUs (Gen 9 / 9.5 / 11)
  where Sodium's automatic thread count is conservative. On Arc discrete cards
  the change is small.

## License

LGPL-3.0-only. See `LICENSE`.
