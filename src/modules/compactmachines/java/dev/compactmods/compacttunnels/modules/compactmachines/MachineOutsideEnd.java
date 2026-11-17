package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

final class MachineOutsideEnd {
  @FunctionalInterface
  private interface FarLookup<T> {
    @Nullable T find(ServerLevel level, BlockPos pos, Direction side);
  }

  private MachineOutsideEnd() {}

  @Nullable static IItemHandler itemHandler(
      BoundCompactMachineBlockEntity machine, @Nullable Direction side) {
    return handler(
        machine,
        side,
        (farLevel, pos, querySide) ->
            farLevel.getCapability(Capabilities.ItemHandler.BLOCK, pos, querySide));
  }

  @Nullable static IFluidHandler fluidHandler(
      BoundCompactMachineBlockEntity machine, @Nullable Direction side) {
    return handler(
        machine,
        side,
        (farLevel, pos, querySide) ->
            farLevel.getCapability(Capabilities.FluidHandler.BLOCK, pos, querySide));
  }

  @Nullable static IEnergyStorage energyHandler(
      BoundCompactMachineBlockEntity machine, @Nullable Direction side) {
    return handler(
        machine,
        side,
        (farLevel, pos, querySide) ->
            farLevel.getCapability(Capabilities.EnergyStorage.BLOCK, pos, querySide));
  }

  @Nullable private static <T> T handler(
      BoundCompactMachineBlockEntity machine, @Nullable Direction side, FarLookup<T> lookup) {
    if (side == null || !(machine.getLevel() instanceof ServerLevel machineLevel)) {
      return null;
    }

    final String roomCode = machine.connectedRoom();
    if (roomCode == null) {
      return null;
    }

    final GlobalPos machinePos = GlobalPos.of(machineLevel.dimension(), machine.getBlockPos());
    return ResolutionGuard.resolve(
        machinePos, () -> resolve(machineLevel, machinePos, roomCode, side, lookup));
  }

  @Nullable private static <T> T resolve(
      ServerLevel machineLevel,
      GlobalPos machinePos,
      String roomCode,
      Direction side,
      FarLookup<T> lookup) {
    final GlobalPos tunnelPos = TunnelClaims.tunnelFor(roomCode, side);
    if (tunnelPos == null) {
      return null;
    }

    TunnelClaims.bindOutside(
        GlobalPos.of(machineLevel.dimension(), machinePos.pos().relative(side)), tunnelPos);

    final ServerLevel roomLevel = machineLevel.getServer().getLevel(tunnelPos.dimension());
    if (roomLevel == null) {
      return null;
    }

    MachineBindings.get(machineLevel.getServer()).put(roomCode, machinePos);

    if (!roomLevel.isLoaded(tunnelPos.pos())) {
      return null;
    }

    final BlockState tunnelState = roomLevel.getBlockState(tunnelPos.pos());
    if (!tunnelState.is(CompactMachinesTunnels.CAPABILITY_TUNNEL.get())) {
      TunnelClaims.release(tunnelPos);
      return null;
    }

    final Direction inward = tunnelState.getValue(CapabilityTunnelBlock.FACING).getOpposite();
    final BlockPos inside = tunnelPos.pos().relative(inward);

    if (!roomLevel.isLoaded(inside)) {
      return null;
    }

    return lookup.find(roomLevel, inside, inward.getOpposite());
  }
}
