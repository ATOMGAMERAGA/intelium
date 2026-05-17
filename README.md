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

| Area | Optimization |
|---|---|
| Draw-call submission | Coalesces small `glDrawElementsIndirect` calls into batched MDI submissions to reduce Intel driver overhead |
| Chunk build threading | Tunes the number of chunk build worker threads against the shared CPU/iGPU TDP |
| Buffer strategy | Uses persistent mapped buffers (`GL_ARB_buffer_storage`) instead of orphan-and-respec |
| Occlusion culling | Tightens visibility tests on weaker iGPUs to feed the GPU fewer, larger batches |

Intelium auto-disables on NVIDIA, AMD, or unknown GPUs, and on Intel parts
older than HD Graphics 520 (Gen 9 / Skylake, 2015). On those systems Sodium
runs unmodified.

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

## License

LGPL-3.0-only. See `LICENSE`.
