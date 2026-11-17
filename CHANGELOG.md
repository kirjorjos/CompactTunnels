# Changelog

All notable changes to Compact Tunnels are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
`mod_version` in `gradle.properties`.

## [Unreleased] — 0.1.0

First working tunnel: one block, placed into a room wall, that wires the item fluid and energy handlers of the far side of a compact machine's face to the room behind it.

### Added

**The tunnel block and item**

- `compacttunnels:capability_tunnel`, added to Compact Machines' main creative tab. It is shaped
  like the room's protective wall and replaces that wall block when placed, rather than standing in
  the room next to it.
- Placement only applies to a room wall in the compact dimension; on anything else the item behaves
  as an ordinary block item.
- Each tunnel claims one of the machine's six faces (its *side*) when placed. The first free side in
  the order north, east, south, west, up, down is taken; if all six are claimed, placement is
  cancelled with a message and nothing is consumed. Placement tries the side the item already
  carries first, then continues through the order.
- The item carries its side as a data component, and its tooltip reports that side (or "first free
  side") plus the one-per-side, six-per-machine limit.

**What a wire tunnel exposes**

- Item, fluid and energy handlers: `Capabilities.ItemHandler.BLOCK`,
  `Capabilities.FluidHandler.BLOCK` and `Capabilities.EnergyStorage.BLOCK`, sided queries only (an
  unsided query gets nothing). Energy is handed out bare like the others, so a cable's own
  throughput rule still applies.
- The room end returns the handler of the block on the inside of the wall it points at; the machine
  face returns the handler of the block inside the room on that side. The far handler is handed out
  bare and re-resolved per query, never cached.
- The index is keyed by room code, so either end can find the other without the far block entity
  being loaded. Claims are rebuilt from the tunnel block entities when their chunk loads.
- Reconnections cross the wire in both directions: a block placed or broken next to the machine's
  outside face invalidates the room end, and a change inside the room next to the tunnel
  invalidates the machine face. Each end is invalidated *and* announced as a neighbour change,
  because a pipe or cable redraws its connection shape from the neighbour update rather than from a
  dropped cache entry — so the end that was already there reconnects instead of staying visibly
  unconnected until it is replaced.
- A far side that is not loaded answers nothing instead of loading it, and no tunnel code forces a
  chunk to load or generate.

**Interaction**

- Ctrl + mouse wheel cycles the claimed side while aimed at a tunnel, server-authoritative, wrapping
  in the side order and skipping sides that are already taken. The scroll is cancelled so the hotbar
  does not move. Sides are swapped on the block, and capabilities are invalidated on both ends.
- Shift + left-click with an empty main hand picks the tunnel up: the solid wall is restored in the
  same position, the claim is freed, and the tunnel item (keeping its side) goes to the inventory or
  drops.
- Plain left-click is cancelled so the tunnel is not mined by accident.
- Middle-click picks a blank tunnel; ctrl + middle-click picks one carrying its side. In survival
  the pick rewrites the tunnel item already being picked rather than creating an item.
- Every placement, rotation and pick-up reports the side and the post-action count, e.g.
  `Rotated to East (2/6 used)` and `East freed (1/6 used)`.

**Shutdown diagnostics**

- `ChunkUnloadSettle` runs on the server-stopping event, drops the level's closing tickets, and logs
  the chunk map's unload queue, pending unloads, `toDrop`, loaded holders, holders still holding
  generation refs, and whether any tickets remain. It reads and reports only. Clearing those
  containers does not settle the shutdown loop: a chunk holder leaves the map only through `toDrop`,
  so emptying it strands every loaded holder instead and keeps `ChunkMap.hasWork()` true for good.
  This does not make the shutdown finish (see below).

### Known issues

- **Quitting a world can hang when a tunnel is placed.** The client sits on "Saving worlds" forever
  and has to be force-closed.
  - Reproduced by placing a tunnel in a room wall with the machine reachable from outside, then
    leaving the world within a few seconds of joining.
  - Cause: vanilla's `MinecraftServer.stopServer` flush loop refuses to end while
    `ChunkMap.hasWork()` is true. The compact dimension still has chunk generation in flight when the
    flush begins — `toDrop` holds hundreds of holders, more than a thousand holders have outstanding
    generation refs, and the room's origin chunk (the one holding the tunnel) sits at eleven of them.
    A queued unload whose chunk is not ready re-queues itself, and the drain loop only exits when the
    queue yields nothing, so the server thread never leaves it. That same thread is the only one that
    can finish the generation the queue is waiting for, and the flush has just removed the tickets
    that would drive it, so the state is stationary rather than slow.
  - A tunnel is what makes it reachable: without one, the room is not left in that state when the
    player quits.
  - Current status: the loop never exits. A thread dump of a hung run shows the server thread parked
    inside `MinecraftServer.stopServer` at `waitUntilNextTick` — still in the flush loop, with no
    exception and no crash — while the heap still held 1221 `ChunkHolder`s. That is exactly the 1221
    positions the earlier cleanup cleared out of `toDrop`, which is the only path that removes a
    holder from the map, so `hasWork()` (which includes `!updatingChunkMap.isEmpty()`) can never
    become false again. A mixin fix is accepted; it is not implemented yet. See
    `.notes/tunnel-addon-decisions.md`.
