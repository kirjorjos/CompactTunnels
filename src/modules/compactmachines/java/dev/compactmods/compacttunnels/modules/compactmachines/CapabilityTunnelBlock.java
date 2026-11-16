package dev.compactmods.compacttunnels.modules.compactmachines;

import com.mojang.serialization.MapCodec;
import dev.compactmods.machines.api.room.RoomInstance;
import dev.compactmods.machines.room.Rooms;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class CapabilityTunnelBlock extends Block implements EntityBlock {
  public static final MapCodec<CapabilityTunnelBlock> CODEC =
      simpleCodec(CapabilityTunnelBlock::new);

  public static final DirectionProperty FACING = BlockStateProperties.FACING;

  public static final DirectionProperty SIDE = DirectionProperty.create("side");

  public CapabilityTunnelBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SIDE, Direction.NORTH));
  }

  public static Properties tunnelProperties() {
    return Properties.of()
        .mapColor(MapColor.STONE)
        .sound(SoundType.METAL)
        .strength(2.0F)
        .lightLevel(state -> 15);
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(FACING, SIDE);
  }

  @Override
  public ItemStack getCloneItemStack(
      BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
    final ItemStack picked = super.getCloneItemStack(state, target, level, pos, player);
    if (level.isClientSide() && TunnelSideCopy.held()) {
      picked.set(CompactMachinesTunnels.TUNNEL_SIDE.get(), state.getValue(SIDE));
    }

    return picked;
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    final Direction clicked = context.getClickedFace().getOpposite();
    final BlockState fallback = defaultBlockState().setValue(FACING, clicked);

    if (!(context.getLevel() instanceof ServerLevel level)) {
      return fallback;
    }

    final BlockPos pos = context.getClickedPos();

    final Direction facing = RoomMachineIndex.wallFacing(level, pos, clicked);
    if (facing == null) {
      return fallback;
    }

    final BlockState state = wallState(level, pos, facing, context.getItemInHand());
    if (state == null) {
      refusePlacement(context, level, pos);
      return null;
    }

    return state;
  }

  @Nullable BlockState wallState(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack) {
    final BlockState state = defaultBlockState().setValue(FACING, facing);

    final String roomCode = RoomMachineIndex.roomCodeAt(level, pos, facing);
    if (roomCode == null) {
      return state;
    }

    final Direction carried = stack.get(CompactMachinesTunnels.TUNNEL_SIDE.get());
    final Direction side = TunnelClaims.firstFree(level.getServer(), roomCode, carried);
    if (side == null) {
      return null;
    }

    return state.setValue(SIDE, side);
  }

  @Override
  public void setPlacedBy(
      Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);

    if (!(level instanceof ServerLevel serverLevel)) {
      return;
    }

    final String roomCode = RoomMachineIndex.roomCodeAt(serverLevel, pos, state.getValue(FACING));
    if (roomCode == null) {
      return;
    }

    final Direction side = state.getValue(SIDE);
    TunnelClaims.claim(roomCode, side, GlobalPos.of(level.dimension(), pos));
    invalidateMachine(serverLevel, RoomMachineIndex.findMachine(level.getServer(), roomCode));

    if (placer instanceof ServerPlayer player) {
      Tell.placed(serverLevel, player, side, roomCode);
    }
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hitResult) {
    if (player.isShiftKeyDown()) {
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    return ItemInteractionResult.SUCCESS;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new CapabilityTunnelBlockEntity(pos, state);
  }

  @Override
  public void neighborChanged(
      BlockState state,
      Level level,
      BlockPos pos,
      Block neighborBlock,
      BlockPos neighborPos,
      boolean movedByPiston) {
    super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

    invalidate(level, pos);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    super.onRemove(state, level, pos, newState, movedByPiston);

    if (state.is(newState.getBlock()) || !(level instanceof ServerLevel serverLevel)) {
      return;
    }

    final Direction facing = state.getValue(FACING);

    invalidate(level, pos);
    TunnelClaims.release(GlobalPos.of(level.dimension(), pos));
    invalidateMachine(serverLevel, RoomMachineIndex.machineAt(serverLevel, pos, facing));
    scheduleWallRestore(serverLevel, pos, facing);
  }

  private static void scheduleWallRestore(ServerLevel level, BlockPos pos, Direction facing) {
    level
        .getServer()
        .tell(
            new TickTask(
                level.getServer().getTickCount() + 1, () -> restoreShellWall(level, pos, facing)));
  }

  static void restoreShellWall(ServerLevel level, BlockPos pos, Direction facing) {
    if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
      return;
    }

    final RoomInstance room = RoomMachineIndex.roomAt(level, pos);
    if (room == null
        || !RoomMachineIndex.onWall(room, pos, facing)
        || !RoomMachineIndex.inShell(room, pos)) {
      return;
    }

    level.setBlock(pos, Rooms.Blocks.SOLID_WALL.get().defaultBlockState(), Block.UPDATE_ALL);
  }

  private static void refusePlacement(BlockPlaceContext context, ServerLevel level, BlockPos pos) {
    if (context.getPlayer() instanceof ServerPlayer player) {
      refuse(player, level, pos);
    }
  }

  static void refuse(ServerPlayer player, ServerLevel level, BlockPos pos) {
    player.displayClientMessage(
        Component.translatableWithFallback(
            "compacttunnels.tunnel.no_free_sides",
            "All six sides of this machine already have a tunnel"),
        true);

    player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
  }

  private static void invalidate(Level level, BlockPos pos) {
    if (level instanceof ServerLevel serverLevel) {
      serverLevel.invalidateCapabilities(pos);
    }
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
