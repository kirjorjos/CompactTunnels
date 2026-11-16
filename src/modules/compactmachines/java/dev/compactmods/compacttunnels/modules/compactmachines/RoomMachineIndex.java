package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.compacttunnels.common.TunnelConstants;
import dev.compactmods.machines.api.CompactMachines;
import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.api.room.RoomInstance;
import dev.compactmods.machines.machine.Machines;
import dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = TunnelConstants.MOD_ID)
public final class RoomMachineIndex {
  private static final Map<String, GlobalPos> MACHINES_BY_ROOM = new HashMap<>();

  private static final Set<GlobalPos> UNBOUND = new HashSet<>();

  private static final int RETRY_INTERVAL = 20;
  private static int ticks;

  private RoomMachineIndex() {}

  @SubscribeEvent
  public static void onChunkLoad(ChunkEvent.Load event) {
    if (event.getLevel() instanceof ServerLevel level) {
      for (BlockPos pos : event.getChunk().getBlockEntitiesPos()) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CapabilityTunnelBlockEntity tunnel) {
          TunnelClaims.index(level, tunnel);
        } else {
          remember(level, blockEntity);
        }
      }
    }
  }

  @SubscribeEvent
  public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }

    final BlockPos pos = event.getPos();
    level.getServer().execute(() -> remember(level, pos));
  }

  @SubscribeEvent
  public static void onServerTick(ServerTickEvent.Post event) {
    if (++ticks < RETRY_INTERVAL) {
      return;
    }

    ticks = 0;
    refreshUnbound(event.getServer());
  }

  @SubscribeEvent
  public static void onServerStopping(ServerStoppingEvent event) {
    MACHINES_BY_ROOM.clear();
    UNBOUND.clear();
  }

  @Nullable static RoomInstance roomAt(ServerLevel level, BlockPos pos) {
    if (!CompactDimension.isLevelCompact(level)) {
      return null;
    }

    final var chunkManager = CompactMachines.chunkManager();
    if (chunkManager == null) {
      return null;
    }

    return chunkManager
        .findRoomByChunk(new ChunkPos(pos))
        .flatMap(code -> CompactMachines.room(level.getServer(), code))
        .orElse(null);
  }

  static boolean onWall(RoomInstance room, BlockPos pos, Direction facing) {
    final AABB outer = room.boundaries().outerBounds();
    final AABB inner = room.boundaries().innerBounds();
    final Vec3 center = Vec3.atCenterOf(pos);
    if (!outer.contains(center)) {
      return false;
    }

    if (!inner.contains(Vec3.atCenterOf(pos.relative(facing.getOpposite())))) {
      return false;
    }

    return !inner.contains(center) || !inner.contains(Vec3.atCenterOf(pos.relative(facing)));
  }

  static boolean inShell(RoomInstance room, BlockPos pos) {
    final Vec3 center = Vec3.atCenterOf(pos);
    return room.boundaries().outerBounds().contains(center)
        && !room.boundaries().innerBounds().contains(center);
  }

  @Nullable static Direction wallFacing(ServerLevel level, BlockPos pos, Direction preferred) {
    final RoomInstance room = roomAt(level, pos);
    if (room == null) {
      return null;
    }

    if (onWall(room, pos, preferred)) {
      return preferred;
    }

    for (Direction candidate : Direction.values()) {
      if (onWall(room, pos, candidate)) {
        return candidate;
      }
    }

    return null;
  }

  @Nullable static String roomCodeAt(ServerLevel level, BlockPos pos, Direction facing) {
    final RoomInstance room = roomAt(level, pos);
    if (room == null || !onWall(room, pos, facing)) {
      return null;
    }

    return room.code();
  }

  @Nullable static GlobalPos machineAt(ServerLevel level, BlockPos pos, Direction facing) {
    final String roomCode = roomCodeAt(level, pos, facing);
    return roomCode == null ? null : findMachine(level.getServer(), roomCode);
  }

  @Nullable static GlobalPos findMachine(MinecraftServer server, String roomCode) {
    refreshUnbound(server);

    final GlobalPos machine =
        MACHINES_BY_ROOM.computeIfAbsent(roomCode, code -> MachineBindings.get(server).get(code));
    if (machine == null) {
      return null;
    }

    final ServerLevel level = server.getLevel(machine.dimension());
    if (level == null) {
      MACHINES_BY_ROOM.remove(roomCode);
      return null;
    }

    if (level.isLoaded(machine.pos()) && !boundTo(server, machine, roomCode)) {
      MACHINES_BY_ROOM.remove(roomCode);
      return null;
    }

    return machine;
  }

  static boolean boundTo(MinecraftServer server, GlobalPos machinePos, String roomCode) {
    final ServerLevel level = server.getLevel(machinePos.dimension());
    if (level == null || !level.isLoaded(machinePos.pos())) {
      return false;
    }

    return roomCode.equals(roomCode(level.getBlockEntity(machinePos.pos())));
  }

  private static void refreshUnbound(MinecraftServer server) {
    if (UNBOUND.isEmpty()) {
      return;
    }

    for (Iterator<GlobalPos> it = UNBOUND.iterator(); it.hasNext(); ) {
      final GlobalPos pos = it.next();
      final ServerLevel level = server.getLevel(pos.dimension());
      if (level == null) {
        it.remove();
        continue;
      }

      if (!level.isLoaded(pos.pos())) {
        continue;
      }

      final BlockEntity blockEntity = level.getBlockEntity(pos.pos());
      if (!isMachine(blockEntity)) {
        it.remove();
        continue;
      }

      final String roomCode = roomCode(blockEntity);
      if (roomCode != null) {
        it.remove();
        bind(server, roomCode, pos);
      }
    }
  }

  private static void remember(ServerLevel level, BlockPos pos) {
    if (level.isLoaded(pos)) {
      remember(level, level.getBlockEntity(pos));
    }
  }

  private static void remember(ServerLevel level, @Nullable BlockEntity blockEntity) {
    if (!isMachine(blockEntity)) {
      return;
    }

    final GlobalPos pos = GlobalPos.of(level.dimension(), blockEntity.getBlockPos());
    final String roomCode = roomCode(blockEntity);
    if (roomCode == null) {
      UNBOUND.add(pos);
      return;
    }

    UNBOUND.remove(pos);
    bind(level.getServer(), roomCode, pos);
  }

  private static void bind(MinecraftServer server, String roomCode, GlobalPos machine) {
    MACHINES_BY_ROOM.put(roomCode, machine);
    MachineBindings.get(server).put(roomCode, machine);
  }

  private static boolean isMachine(@Nullable BlockEntity blockEntity) {
    return blockEntity != null
        && (blockEntity.getType() == Machines.BlockEntities.MACHINE.get()
            || blockEntity.getType() == Machines.BlockEntities.UNBOUND_MACHINE.get());
  }

  @Nullable private static String roomCode(@Nullable BlockEntity blockEntity) {
    return blockEntity instanceof BoundCompactMachineBlockEntity machine
        ? machine.connectedRoom()
        : null;
  }
}
