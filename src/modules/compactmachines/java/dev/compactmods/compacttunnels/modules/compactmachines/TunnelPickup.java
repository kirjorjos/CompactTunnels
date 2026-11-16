package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.compacttunnels.common.TunnelConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = TunnelConstants.MOD_ID)
public final class TunnelPickup {
  private TunnelPickup() {}

  @SubscribeEvent
  public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
    if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
      return;
    }

    final Level level = event.getLevel();
    final BlockPos pos = event.getPos();
    final BlockState state = level.getBlockState(pos);
    if (!state.is(CompactMachinesTunnels.CAPABILITY_TUNNEL.get())) {
      return;
    }

    final Player player = event.getEntity();
    if (!player.isShiftKeyDown()) {
      event.setCanceled(true);
      return;
    }

    if (!player.getMainHandItem().isEmpty()) {
      return;
    }

    event.setCanceled(true);

    if (level instanceof ServerLevel serverLevel) {
      pickUp(
          serverLevel,
          pos,
          player,
          state.getValue(CapabilityTunnelBlock.SIDE),
          state.getValue(CapabilityTunnelBlock.FACING));
    }
  }

  private static void pickUp(
      ServerLevel level, BlockPos pos, Player player, Direction side, Direction facing) {
    final String roomCode = RoomMachineIndex.roomCodeAt(level, pos, facing);

    level.removeBlock(pos, false);

    CapabilityTunnelBlock.restoreShellWall(level, pos, facing);

    final ItemStack picked = new ItemStack(CompactMachinesTunnels.CAPABILITY_TUNNEL_ITEM.get());
    picked.set(CompactMachinesTunnels.TUNNEL_SIDE.get(), side);

    if (!player.getInventory().add(picked)) {
      Block.popResource(level, pos, picked);
    }

    if (player instanceof ServerPlayer serverPlayer && roomCode != null) {
      Tell.freed(level, serverPlayer, side, roomCode);
    }
  }
}
