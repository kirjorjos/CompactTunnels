package dev.compactmods.compacttunnels.modules.compactmachines;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class TunnelNetwork {
  private static final double MAX_ROTATE_DISTANCE_SQR = 64.0;

  private TunnelNetwork() {}

  public static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToServer(
            CycleTunnelSidePayload.TYPE,
            CycleTunnelSidePayload.STREAM_CODEC,
            TunnelNetwork::handle);
  }

  private static void handle(CycleTunnelSidePayload payload, IPayloadContext context) {
    if (!(context.player() instanceof ServerPlayer player)) {
      return;
    }

    context.enqueueWork(() -> cycle(player, payload.pos(), payload.forward()));
  }

  private static void cycle(ServerPlayer player, BlockPos pos, boolean forward) {
    final ServerLevel level = player.serverLevel();
    if (!level.isLoaded(pos) || player.blockPosition().distSqr(pos) > MAX_ROTATE_DISTANCE_SQR) {
      return;
    }

    final BlockState state = level.getBlockState(pos);
    if (!state.is(CompactMachinesTunnels.CAPABILITY_TUNNEL.get())) {
      return;
    }

    final Direction current = state.getValue(CapabilityTunnelBlock.SIDE);
    final String roomCode =
        RoomMachineIndex.roomCodeAt(level, pos, state.getValue(CapabilityTunnelBlock.FACING));

    Direction next = current;
    if (roomCode != null) {
      next =
          TunnelClaims.cycle(
              level.getServer(), roomCode, GlobalPos.of(level.dimension(), pos), current, forward);

      if (next != current) {
        level.setBlock(pos, state.setValue(CapabilityTunnelBlock.SIDE, next), Block.UPDATE_ALL);
        level.invalidateCapabilities(pos);
        invalidateMachine(level, RoomMachineIndex.findMachine(level.getServer(), roomCode));
      }
    }
    final int used = roomCode == null ? 0 : TunnelClaims.used(level.getServer(), roomCode);

    player.displayClientMessage(
        Component.translatableWithFallback(
            "compacttunnels.tunnel.rotated",
            "Rotated to %1$s (%2$s/%3$s used)",
            CapabilityTunnelItem.sideName(next),
            used,
            TunnelClaims.limit()),
        true);
  }

  private static void invalidateMachine(ServerLevel level, @Nullable GlobalPos machine) {
    if (machine == null) {
      return;
    }

    final ServerLevel machineLevel = level.getServer().getLevel(machine.dimension());
    if (machineLevel != null && machineLevel.isLoaded(machine.pos())) {
      machineLevel.invalidateCapabilities(machine.pos());
    }
  }
}
