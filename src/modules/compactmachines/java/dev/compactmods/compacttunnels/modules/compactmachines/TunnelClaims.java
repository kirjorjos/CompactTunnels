package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.compacttunnels.common.TunnelConstants;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = TunnelConstants.MOD_ID)
public final class TunnelClaims {
  private static final List<Direction> ORDER =
      List.of(
          Direction.NORTH,
          Direction.EAST,
          Direction.SOUTH,
          Direction.WEST,
          Direction.UP,
          Direction.DOWN);

  private static final Map<String, Map<Direction, GlobalPos>> BY_ROOM = new HashMap<>();
  private static final Map<GlobalPos, Claim> BY_TUNNEL = new HashMap<>();

  private static final Map<GlobalPos, GlobalPos> BY_OUTSIDE = new HashMap<>();

  private static boolean notifying;

  private TunnelClaims() {}

  private record Claim(String roomCode, Direction side) {}

  static int limit() {
    return ORDER.size();
  }

  static int used(MinecraftServer server, String roomCode) {
    final Map<Direction, GlobalPos> claimed = BY_ROOM.get(roomCode);
    if (claimed == null) {
      return 0;
    }

    int used = 0;
    for (Map.Entry<Direction, GlobalPos> entry : claimed.entrySet()) {
      if (stillClaimed(server, entry.getValue(), roomCode, entry.getKey())) {
        used++;
      }
    }

    return used;
  }

  @Nullable static Direction firstFree(MinecraftServer server, String roomCode) {
    return firstFree(server, roomCode, null);
  }

  @Nullable static Direction firstFree(
      MinecraftServer server, String roomCode, @Nullable Direction preferred) {
    return firstFree(server, roomCode, preferred, 1);
  }

  @Nullable private static Direction firstFree(
      MinecraftServer server, String roomCode, @Nullable Direction preferred, int step) {
    final Map<Direction, GlobalPos> claimed = BY_ROOM.get(roomCode);
    final int start = preferred == null ? 0 : ORDER.indexOf(preferred);

    for (int offset = 0; offset < ORDER.size(); offset++) {
      final Direction side = ORDER.get(Math.floorMod(start + offset * step, ORDER.size()));
      final GlobalPos claimer = claimed == null ? null : claimed.get(side);
      if (claimer == null) {
        return side;
      }

      if (stillClaimed(server, claimer, roomCode, side)) {
        continue;
      }

      release(claimer);
      return side;
    }

    return null;
  }

  static Direction cycle(
      MinecraftServer server,
      String roomCode,
      GlobalPos tunnel,
      Direction current,
      boolean forward) {
    release(tunnel);

    final int step = forward ? 1 : -1;
    final int next = Math.floorMod(ORDER.indexOf(current) + step, ORDER.size());
    final Direction chosen = firstFree(server, roomCode, ORDER.get(next), step);
    if (chosen == null) {
      claim(roomCode, current, tunnel);
      return current;
    }

    claim(roomCode, chosen, tunnel);
    return chosen;
  }

  @Nullable static GlobalPos tunnelFor(String roomCode, Direction side) {
    final Map<Direction, GlobalPos> claimed = BY_ROOM.get(roomCode);
    return claimed == null ? null : claimed.get(side);
  }

  static void bindOutside(GlobalPos outside, GlobalPos tunnel) {
    BY_OUTSIDE.put(outside, tunnel);
  }

  @Nullable static GlobalPos tunnelForOutside(GlobalPos outside) {
    return BY_OUTSIDE.get(outside);
  }

  static void claim(String roomCode, Direction side, GlobalPos tunnel) {
    release(tunnel);
    BY_ROOM.computeIfAbsent(roomCode, ignored -> new EnumMap<>(Direction.class)).put(side, tunnel);
    BY_TUNNEL.put(tunnel, new Claim(roomCode, side));
  }

  static void claimIfFree(String roomCode, Direction side, GlobalPos tunnel) {
    final Map<Direction, GlobalPos> claimed = BY_ROOM.get(roomCode);
    final GlobalPos claimer = claimed == null ? null : claimed.get(side);
    if (claimer == null || claimer.equals(tunnel)) {
      claim(roomCode, side, tunnel);
    }
  }

  static void release(GlobalPos tunnel) {
    BY_OUTSIDE.values().removeIf(tunnel::equals);

    final Claim claim = BY_TUNNEL.remove(tunnel);
    if (claim == null) {
      return;
    }

    final Map<Direction, GlobalPos> claimed = BY_ROOM.get(claim.roomCode());
    if (claimed == null) {
      return;
    }

    if (tunnel.equals(claimed.get(claim.side()))) {
      claimed.remove(claim.side());
    }

    if (claimed.isEmpty()) {
      BY_ROOM.remove(claim.roomCode());
    }
  }

  static void index(ServerLevel level, CapabilityTunnelBlockEntity tunnel) {
    final GlobalPos tunnelPos = GlobalPos.of(level.dimension(), tunnel.getBlockPos());
    final String roomCode =
        RoomMachineIndex.roomCodeAt(level, tunnel.getBlockPos(), tunnel.farSide());
    if (roomCode != null) {
      claimIfFree(roomCode, tunnel.side(), tunnelPos);
    }
  }

  private static boolean stillClaimed(
      MinecraftServer server, GlobalPos tunnelPos, String roomCode, Direction side) {
    final ServerLevel level = server.getLevel(tunnelPos.dimension());
    if (level == null || !level.isLoaded(tunnelPos.pos())) {
      return false;
    }

    if (!(level.getBlockEntity(tunnelPos.pos()) instanceof CapabilityTunnelBlockEntity tunnel)) {
      return false;
    }

    return tunnel.side() == side
        && roomCode.equals(RoomMachineIndex.roomCodeAt(level, tunnelPos.pos(), tunnel.farSide()));
  }

  @SubscribeEvent
  public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
    if (notifying || !(event.getLevel() instanceof ServerLevel level)) {
      return;
    }

    final GlobalPos tunnel = tunnelForOutside(GlobalPos.of(level.dimension(), event.getPos()));
    if (tunnel == null) {
      return;
    }

    final ServerLevel tunnelLevel = level.getServer().getLevel(tunnel.dimension());
    if (tunnelLevel == null || !tunnelLevel.isLoaded(tunnel.pos())) {
      return;
    }

    notifying = true;
    try {
      tunnelLevel.invalidateCapabilities(tunnel.pos());

      tunnelLevel.updateNeighborsAt(
          tunnel.pos(), tunnelLevel.getBlockState(tunnel.pos()).getBlock());
    } finally {
      notifying = false;
    }
  }

  @SubscribeEvent
  public static void onServerStopping(ServerStoppingEvent event) {
    BY_ROOM.clear();
    BY_TUNNEL.clear();
    BY_OUTSIDE.clear();
  }
}
