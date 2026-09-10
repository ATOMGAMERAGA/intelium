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
| Render Budget Engine | Three optimizations of Intelium's own, inside the render path rather than on top of a vanilla setting. **Smart Entity Culling** skips entities that land on fewer pixels than a threshold — measured from the entity's real size against your resolution and FOV, so a dropped item stops drawing while a zombie remains visible much farther away. **Block Entity Budget** caps individually drawn chests, signs and banners; **Particle Burst Limiter** cuts the tail of extreme particle bursts. OpenGL receives a modest draw-call-aware adjustment, and all three can tighten further under FPS pressure. Players, the camera entity, projectiles, named entities, glowing/outlined entities, vehicles with their riders, and anything within 12 blocks are never culled. |
| Chunk build threading | Overrides Sodium's chunk-build worker count with a generation-, profile- and backend-aware value. On **Gen 9 / Gen 9.5 parts with four or fewer logical processors** — an HD 520 is two physical cores with SMT, so the logical count overstates the machine — OpenGL uses **1 worker for Max FPS and at most 2 otherwise**, because a third mesh thread only makes chunk workers and the render thread fight over the same two cores. Elsewhere it scales with your CPU and reserves render-thread headroom; Vulkan keeps room for Blaze3D submission and Sodium's asynchronous culling worker. Manual overrides remain exact. |
| Fast chunk loading | Overrides Sodium's chunk **defer mode** — how many frames a finished mesh waits before it is uploaded. Chunk uploads run on the render thread, so forcing them buys faster chunk appearance and pays in frame time. **Fast** therefore follows your profile rather than always forcing one-frame: Max FPS defers conservatively, Smooth uses one-frame, Balanced switches between them under measured pressure with hysteresis. **Turbo** remains zero-frame and fully user-directed. Self-disables cleanly if Sodium moves the setting. |
| Live render tweaks | Opt-in caps on vanilla settings that cost real per-frame GPU/CPU time on weak iGPUs: entity render distance, particles, entity shadows, biome blending, clouds, graphics mode, smooth lighting, VSync and render distance. Each captures your original value and restores it when turned off — the captured originals are persisted, so the restore works even across a game restart. |
| Optimization profile | **Max FPS / Balanced / Smooth** — shifts the chunk-worker trade-off toward peak frame rate or toward steady frame times while walking and turning. |
| Adaptive performance | **Adaptive Render Distance** holds a user-set FPS target by stepping the render distance down when FPS stays low and back up when there is headroom (hysteresis + hold timers, never below half your setting); when FPS collapses far below the target it reacts ~4× faster (halved hold, two chunks per step). **Background FPS Limit** caps the frame rate while the window is unfocused and restores your limit the instant focus returns. **Menu FPS Limit** does the same while a menu is open — frames nobody needs at full rate. |
| Frame-time measurement | On 26.2, frame intervals are measured at a verified per-frame hook rather than read off the game's trailing one-second FPS average, which cannot represent a hitch at all. Produces median, p95, p99, **1% low** and **0.1% low**, exportable to JSON/CSV from the settings page. The hot path is a subtract and two array stores into a preallocated ring buffer — no allocation, and percentiles only computed when something asks. |
| Honest capabilities | Every hook is verified against the running game by **full method descriptor**, not just method name. Eight features (GPU detection, worker tuning, defer tuning, entity culling, block-entity budget, particle limiter, menu detection, frame boundary) fail independently: one missing hook disables one feature, greys out that option with the reason in its tooltip, and leaves the rest working. |
| GPU detection | On 26.2, reads Blaze3D's selected graphics device directly, so the GPU is identified without unsafe OpenGL calls and hybrid-GPU laptops are judged by the device Minecraft actually uses. A device that reports no backend name resolves to OpenGL, because that is 26.2's default; a renderer that names itself something unrecognised stays Unknown and keeps the conservative policy. On 1.21.11 it uses the current OpenGL context. Handles Windows drivers, Linux/Mesa, PCI vendor ID `8086`, and VirGL guests that expose the host renderer. |
| Honest gating | Disables itself cleanly on NVIDIA / AMD / unrecognized / too-old GPUs. A Mixin config plugin checks each hook's Sodium target at load time, so any compatible Sodium version works and incompatible internals self-disable instead of crashing. |

> **Why Intelium does not manage Sodium's own performance options.**
> A "one-click Max FPS preset" for Sodium's Block Face Culling, Fog Occlusion,
> Entity Culling, Animate Only Visible Textures, Hidden Fluid Culling and
> Block Transparency sounds useful, so it was checked against the resolved
> Sodium 0.9.1 jar. Every one of them already ships at its optimal value:
> the five performance flags default to `true`, `hiddenFluidCulling` defaults to
> `true`, `quadSplittingMode` defaults to `SAFE`, and `chunkBuildDeferMode`
> defaults to `ALWAYS`. Forcing them would be a no-op for almost everyone, and
> for the few who changed one deliberately it would silently overwrite their
> choice. So Intelium does not ship that switch.
>
> **Why these levers and not draw-call batching / persistent buffers?**
> Sodium already owns backend-native batching, chunk-geometry memory, and
> occlusion culling on its supported OpenGL/Vulkan paths.
> Re-implementing those is redundant and risks regressions, so Intelium does not
> ship placebo switches. Instead it pulls the levers Sodium leaves to the player
> — entity distance, particles, shadows, biome blend, worker count — and wires
> each to a real, reversible effect.

Intelium auto-disables on NVIDIA, AMD, unrecognized, or unknown GPUs, and on
Intel parts older than HD Graphics 520 (Gen 9 / Skylake, 2015). When disabled,
its options are greyed out and the reason is shown both in Sodium's video
settings and on the in-game **Supported GPUs** screen.

## Requirements

Intelium ships as **two jars**, one per Minecraft line. Each GitHub release
contains both; pick the one matching your Minecraft version.

| Jar | Minecraft | Java | Renderer | Sodium |
|---|---|---|---|---|
| `Intelium-v1.3.4-1.21.11.jar` | 1.21.11 | 21 | OpenGL | 0.8.13 build target |
| `Intelium-v1.3.4-26.2.jar` | 26.2 | 25 | **OpenGL** (default) · Vulkan (experimental) | 0.9.1 build target |

> **On 26.2, OpenGL is the default renderer** and Vulkan is an experimental
> opt-in you have to turn on. Intelium tunes OpenGL first: that is what almost
> everyone is actually running, and it is where an Intel iGPU's render thread
> and driver contend for the same cores. Vulkan support is intact and untouched
> by the OpenGL-specific policies.
>
> The 26.x jar declares `>=26.2 <26.3`. It compiles directly against 26.2 class
> and method names and 26.1 has not been runtime-tested, so it does not claim
> 26.1 support it cannot back.

- Fabric Loader **0.19.0+** (release builds use 0.19.3)
- Fabric API
- **Sodium** — any version compatible with your Minecraft. Intelium does not cap
  the Sodium version: if a newer Sodium changes the internals a hook relies on,
  that hook self-disables cleanly (no crash) and everything else keeps working.
- An Intel GPU (HD 520 / Gen 9 Skylake or newer)

Intelium bundles nothing: no Sodium, Fabric, Iris or third-party client files are
inside the jar, and a build test enforces that. It uses no private or guessed
client APIs and probes for no invented mod ids, so the same jar runs on stock
Fabric 26.2 and under third-party Fabric-based clients alike.

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
| Gen 11 Ice Lake | Gen 11 | 2019 | UHD Graphics G1 · Iris Plus Graphics G4, G7 |
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
| Optimization Profile | `Balanced` | **Max FPS** favors peak frame rate (fewer workers, even when Fast Chunk Loading is active); **Smooth** favors stable frame times while moving (more workers); **Balanced** is the middle. |
| Chunk Build Workers | `Auto` | `0` / Auto = generation-, profile-, CPU- and backend-aware default. On Gen 9 / Gen 9.5 with four or fewer logical processors, OpenGL uses 1 worker for Max FPS and at most 2 otherwise. On larger CPUs, OpenGL Max FPS/Balanced reserve two logical processors for game/driver/upload work and Vulkan reserves equivalent submission/culling headroom. `1–16` remains an exact override. |
| Fast Chunk Loading | `Fast` | **Off** restores your own Sodium defer mode. **Fast** follows your profile (see above) rather than always forcing one-frame. **Turbo** = zero-frame (fastest appearance, roughest pacing) and is never adaptively slowed, on any backend under any profile. |

**General → Render Tweaks** (applied live to vanilla settings; your originals are restored when turned off — even across a restart)

| Option | Default | Notes |
|---|---|---|
| Live Render Tweaks | `true` | Master switch for all the levers below (including the GPU Savers group). |
| Max Entity Distance | `80%` | Caps how far entities render (50–100%; `Full` = untouched). Lower culls distant mobs/items — a real FPS win in crowded scenes. |
| Limit Particles | `true` | Caps particles to *Decreased* (never overrides a stricter setting). |
| Disable Entity Shadows | `false` | Turns off the round shadows under entities. |
| Fast Biome Blend | `false` | Forces biome blend to 0. Biome blending runs on the chunk-build thread, so this makes meshing much cheaper and cuts hitches when chunks stream in. |

**General → GPU Savers** (all off by default because they change how the game looks or behaves)

| Option | Default | Notes |
|---|---|---|
| Clouds | `Don't Touch` | **Fast** caps volumetric ("Fancy") clouds to flat ones; **Off** removes them. Clouds are a translucent layer redrawn every frame — pure overdraw on an iGPU. |
| Fast Graphics | `false` | Forces the vanilla Graphics setting to *Fast*. One of the biggest single FPS levers on weak iGPUs. |
| Disable Smooth Lighting | `false` | Turns off ambient occlusion — cheaper chunk meshing and slightly cheaper frames. |
| Force VSync Off | `false` | Uncaps FPS from the display refresh rate while Intelium is active. |
| Max Render Distance | `No Cap` | Caps render distance (in chunks), downward only. Fewer chunk sections to build, upload and draw every frame. |
| Max Simulation Distance | `No Cap` | Caps simulation distance, downward only. Fewer ticked chunks = real CPU savings in singleplayer; on power-shared iGPUs a cooler CPU means faster frames. |

**General → Render Budget Engine** (Intelium's own culling systems; each is `Off` / `Light` / `Balanced` / `Aggressive`)

| Option | Default | Notes |
|---|---|---|
| Render Budget Engine | `true` | Master switch for the three budgets below. Turning it off stands them all down instantly. |
| Smart Entity Culling | `Balanced` | Skips drawing entities too small on screen to make out. Base thresholds are Light 6px, Balanced 12px, Aggressive 24px; OpenGL applies a 1.10× draw-call adjustment. The calculation scales with resolution, FOV and real entity size. Never culled at any distance: players, the camera entity, projectiles (arrows, tridents, thrown potions), named entities and holograms, glowing/outlined entities, and vehicles together with their riders — plus anything within 12 blocks. |
| Block Entity Budget | `Balanced` | Base per-frame ceilings are Light 512, Balanced 256, Aggressive 128; OpenGL uses 75% of each base ceiling (384/192/96) before adaptive pressure. An ordinary scene has a few dozen and never reaches it. Block entities within 12 blocks are drawn unconditionally, so the ceiling can never make a nearby chest vanish while a distant one draws. |
| Particle Burst Limiter | `Balanced` | Base per-tick ceilings are Light 1024, Balanced 512, Aggressive 256; OpenGL uses 87.5% (896/448/224) before adaptive pressure. Ordinary play spawns a handful per tick; explosions spawn thousands. |
| Adaptive Budgets | `true` | Lets the three tighten further when FPS falls short of the Adaptive FPS Target, and relax the moment it recovers. At target it changes nothing. |

**General → Adaptive Performance** (off by default)

| Option | Default | Notes |
|---|---|---|
| Adaptive Render Distance | `false` | Steps render distance down one chunk at a time when FPS stays below the target, back up with sustained headroom. Hysteresis + hold timers prevent oscillation; never below half your own distance; restored when turned off. When FPS falls far below the target (under ~60%) it reacts faster: halved hold window, two chunks per step. |
| Adaptive FPS Target | `60 FPS` | Target used by adaptive distance/budgets and OpenGL Fast chunk pacing. Distance steps down below ~92% and back up above ~115%; chunk pacing has its own 85%/97% hysteresis windows. |
| Background FPS Limit | `Off` | Caps FPS while the window is unfocused; your own limit is restored the instant focus returns. Yields automatically to Dynamic FPS / FPS Reducer. |
| Menu FPS Limit | `Off` | Caps FPS while a menu (pause screen, inventory, settings) is open — menus redraw the whole frame at full rate for nothing. Your own limit is restored the instant the menu closes. Yields automatically to Dynamic FPS / FPS Reducer, and the adaptive controller ignores FPS readings while the cap is active. |

**Overlay & Test**

This page and its movable benchmark overlay are available in the 1.21.11 jar.
The 26.2 jar keeps the performance controls inside Sodium's settings page but
does not port the legacy immediate-mode overlay to 26.x's retained-mode GUI.

Instead, the 26.2 jar has a **Diagnostics → Export Frame Report** button. It
writes the last few seconds of measured frame times to
`config/intelium-reports/` as JSON and appends a row to a CSV, so two builds can
be compared by arithmetic rather than memory:

| Field | Meaning |
|---|---|
| `average_fps` | Mean frame rate over the window |
| `median_frametime_ms` | The typical frame |
| `p95_frametime_ms` / `p99_frametime_ms` | The slow tail — where stutter lives |
| `one_percent_low_fps` / `point_one_percent_low_fps` | Standard 1% / 0.1% lows |
| `samples` / `discarded` | Frames measured, and frames rejected as stalls |
| `warm` | Whether the window is long enough to trust |
| `cap_active` | Whether VSync or an FPS limit was pacing these frames |
| `backend`, `gpu`, `profile`, `chunk_mode`, `workers`, `capabilities` | What produced the numbers |

Unfocused frames, menu-capped frames and frames with no world loaded are
excluded and restart the warm-up. VSync and FPS caps are flagged rather than
discarded — a frame *at* the cap proves nothing about headroom, but one *past*
it is still a real spike.

| Option | Default | Notes |
|---|---|---|
| FPS Test Overlay | `false` | Toggles the movable on-screen FPS panel. |
| Compact Overlay | `false` | Show only the title + FPS (+ lows) lines. |
| Show 1% Low / Min | `true` | Adds a stutter line: 1% low and minimum FPS over the last ~10s. |
| Show Frame Time | `false` | Adds a line with current + average frame time in ms — makes small hitches visible that an FPS number rounds away. |
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

The repo is split by Minecraft line, with shared, version-agnostic logic in
`shared/`:

- `mc1.21.11/` — the 1.21.11 build (Yarn mappings, Loom, Java 21).
- `mc26/` — the 26.2 build (official Mojang mappings, the non-remapping
  `net.fabricmc.fabric-loom` plugin, Java 25).
- `shared/` — pure logic (GPU/backend classifier, config, optimization math, HUD math,
  mod-compat) compiled into both, with its unit tests.

```bash
# 1.21.11 jar (needs JDK 21)
./mc1.21.11/gradlew -p mc1.21.11 build

# 26.2 jar (needs JDK 25)
./mc26/gradlew -p mc26 build
```

Each jar lands in `<target>/build/libs/intelium-<version>.jar`. CI builds both
and attaches them to a single GitHub release.

## Author & links

Made by **ATOMLAND Studios**. Download page:
<https://modrinth.com/mod/intelium-mod>

## License

GPL-3.0-only. See `LICENSE`.
