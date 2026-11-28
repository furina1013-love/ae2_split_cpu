# AE2 Split CPU

**One crafting CPU, many jobs at once.**

Applied Energistics 2 crafting CPUs are strictly one-job-per-CPU: to craft two things in parallel you have to build a second CPU multiblock. AE2 Split CPU removes that limitation. Each time a job is submitted, the CPU splits off a **virtual sub-CPU** that appears in the AE2 CPU terminal, takes over that job, and runs it with its own share of the CPU's bytes and co-processors. When the job finishes, everything is merged back.

## What it does

* **Split on submit** — the moment a crafting job is submitted (Crafting Terminal, Wireless Terminal, Interface auto-crafting, or any AE2 addon that calls `submitJob`), the CPU spawns a sub-CPU sized to exactly that job's byte requirement.
* **Temporary reservation** — the bytes and co-processor threads the sub-CPU needs are deducted from the master CPU for the duration of the job, then restored. The master CPU keeps showing only what is actually still free.
* **Visible in the CPU terminal** — sub-CPUs are real entries in the CPU selection list (`Split #1`, `Split #2`, …), so you can see which one is working on what.
* **Configurable parallelism** — how co-processor threads are shared between sub-CPUs is a config option (see below).
* **Split Component block** — a crafting-unit block with 0 bytes and 0 threads. Placing one inside a CPU multiblock marks that CPU as "allowed to split" (configurable whether it is mandatory). It works in any AE2-compatible CPU multiblock, and it does not change the CPU's size.
* **Configurable cap** — at most N sub-CPUs per master CPU, not counting the master itself.
* **Persistence** — reservations survive world reload; a restart does not leak or duplicate bytes.
* **Split Component indicator lights** — a formed Split Component shows four softly pulsing dots on its faces, so you can tell at a glance whether it is actually part of a valid, powered CPU.
* **Loop Core block** — add one to a CPU and AE2 can plan self-feeding recipes such as `a + b -> 2a`. The planner treats the recipe by its net effect (`b -> a`), borrows a single seed item that is returned at the end of the job, and delivers exactly the requested amount. Only direct single-pattern loops are supported, and at least one seed must already be in the network when ordering.

## Compatibility

* **JEI** — optional, no hard dependency.
* **Other AE2 addons** — any addon whose CPU path goes through AE2's normal `CraftingCPUCluster.submitJob` works unchanged. Sub-CPUs are ordinary `CraftingCPUCluster` instances registered in the grid's crafting service, so they are picked up by AE2's own CPU list, monitor, and selection logic.
* **Mod config menus** — settings use the loader's standard config system, so they appear in the usual in-game config screens.

## Configuration

`config/ae2_split_cpu-common.toml`

| Key | Default | Description |
| --- | --- | --- |
| `enableSplitting` | `true` | Master switch. Off = vanilla AE2 behaviour. |
| `requireSplitComponent` | `true` | Require at least one Split Component block inside the CPU. |
| `maxSplits` | `3` | Maximum sub-CPUs per master CPU (0–64). |
| `parallelScheme` | `EQUAL` | How threads are shared: `EQUAL` (even share), `PROPORTIONAL` (by job bytes), `ALL` (everything to the newest job), `FIXED`. |
| `fixedParallel` | `1` | Threads per sub-CPU when the scheme is `FIXED`. |
| `minParallel` | `1` | Lower bound for any scheme. |

**Parallel schemes**

* `EQUAL` — ceil(remaining threads ÷ remaining split slots). Good default: every concurrent job gets a comparable number of co-processors.
* `PROPORTIONAL` — floor(remaining threads × job bytes ÷ remaining bytes). Big jobs get more threads, small jobs get few.
* `ALL` — the newest sub-CPU takes every remaining thread.
* `FIXED` — every sub-CPU gets exactly `fixedParallel`.

The total handed out never exceeds what the master CPU actually has, and a job is never split onto a sub-CPU with zero threads.

## Versions

| Minecraft | Loader | AE2 | File |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.x | 19.2.x | `ae2-split-cpu-1.2.4-neoforge-1.21.1.jar` |
| 1.20.1 | Forge 47.x | 15.4.x | `ae2-split-cpu-1.2.4-forge-1.20.1.jar` |

## Known behaviour

* The CPU monitor tracks the master CPU; sub-CPU output goes to the machine that requested the job, as usual.
* Breaking the master CPU cancels its sub-CPU jobs and returns the reserved resources.
* Sub-CPUs are transient and never touch the world; they are dropped on world shutdown and rebuilt from the master CPU's saved data on load.

## License

MIT. Bug reports and pull requests: https://github.com/furina1013-love/ae2_split_cpu

