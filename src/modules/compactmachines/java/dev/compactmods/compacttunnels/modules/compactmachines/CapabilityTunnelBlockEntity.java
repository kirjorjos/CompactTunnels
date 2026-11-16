package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.machines.api.room.RoomInstance;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class CapabilityTunnelBlockEntity extends BlockEntity {

  @Nullable private String roomCode;

  public CapabilityTunnelBlockEntity(BlockPos pos, BlockState state) {
    super(CompactMachinesTunnels.CAPABILITY_TUNNEL_ENTITY.get(), pos, state);
  }

  public Direction farSide() {
    return getBlockState().getValue(CapabilityTunnelBlock.FACING);
  }

  public Direction side() {
    return getBlockState().getValue(CapabilityTunnelBlock.SIDE);
  }

  @Nullable public IItemHandler farSideItemHandler() {
    if (!(level instanceof ServerLevel)) {
      return null;
    }

    return ResolutionGuard.resolve(
        GlobalPos.of(level.dimension(), worldPosition), this::resolveAcrossWall);
  }

  @Nullable private IItemHandler resolveAcrossWall() {
    if (!(level instanceof ServerLevel roomLevel)) {
      return null;
    }

    final String code = roomCode(roomLevel);
    if (code == null) {
      return null;
    }

    final GlobalPos machinePos = RoomMachineIndex.findMachine(roomLevel.getServer(), code);
    if (machinePos == null) {
      return null;
    }

    final ServerLevel machineLevel = roomLevel.getServer().getLevel(machinePos.dimension());
    if (machineLevel == null) {
      return null;
    }

    final Direction claimedSide = side();
    TunnelClaims.claimIfFree(code, claimedSide, GlobalPos.of(level.dimension(), worldPosition));

    final BlockPos outside = machinePos.pos().relative(claimedSide);

    if (!machineLevel.isLoaded(outside)) {
      return null;
    }

    return machineLevel.getCapability(
        Capabilities.ItemHandler.BLOCK, outside, claimedSide.getOpposite());
  }

  @Nullable private String roomCode(ServerLevel roomLevel) {
    if (roomCode != null) {
      return roomCode;
    }

    final RoomInstance room = RoomMachineIndex.roomAt(roomLevel, worldPosition);
    if (room == null || !RoomMachineIndex.onWall(room, worldPosition, farSide())) {
      return null;
    }

    this.roomCode = room.code();
    return this.roomCode;
  }
}
