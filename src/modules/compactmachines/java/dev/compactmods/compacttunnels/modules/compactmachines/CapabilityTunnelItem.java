package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.room.block.ProtectedWallBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class CapabilityTunnelItem extends BlockItem {
  public CapabilityTunnelItem(Block block, Item.Properties properties) {
    super(block, properties);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    if (!isRoomWall(context.getLevel(), context.getClickedPos())) {
      return super.useOn(context);
    }

    if (context.getLevel().isClientSide()) {
      return InteractionResult.SUCCESS;
    }

    return placeInWall(context);
  }

  private InteractionResult placeInWall(UseOnContext context) {
    if (!(context.getLevel() instanceof ServerLevel level)
        || !(getBlock() instanceof CapabilityTunnelBlock block)) {
      return InteractionResult.PASS;
    }

    final Player player = context.getPlayer();
    if (player == null) {
      return InteractionResult.PASS;
    }

    final BlockPos pos = context.getClickedPos();
    final Direction facing =
        RoomMachineIndex.wallFacing(level, pos, context.getClickedFace().getOpposite());
    if (facing == null) {
      return InteractionResult.PASS;
    }

    final ItemStack stack = context.getItemInHand();
    final BlockState state = block.wallState(level, pos, facing, stack);
    if (state == null) {
      if (player instanceof ServerPlayer serverPlayer) {
        CapabilityTunnelBlock.refuse(serverPlayer, level, pos);
      }

      return InteractionResult.SUCCESS;
    }

    if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
      return InteractionResult.FAIL;
    }

    block.setPlacedBy(level, pos, state, player, stack);
    level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));

    final SoundType sound = state.getSoundType();
    level.playSound(
        null,
        pos,
        sound.getPlaceSound(),
        SoundSource.BLOCKS,
        (sound.getVolume() + 1.0F) / 2.0F,
        sound.getPitch() * 0.8F);

    if (!player.getAbilities().instabuild) {
      stack.shrink(1);
    }

    if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.awardStat(Stats.ITEM_USED.get(this));
      serverPlayer.containerMenu.broadcastChanges();
    }

    return InteractionResult.SUCCESS;
  }

  private static boolean isRoomWall(Level level, BlockPos pos) {
    return CompactDimension.isLevelCompact(level)
        && level.getBlockState(pos).getBlock() instanceof ProtectedWallBlock;
  }

  @Override
  public void appendHoverText(
      ItemStack stack,
      Item.TooltipContext context,
      List<Component> tooltipComponents,
      TooltipFlag tooltipFlag) {
    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

    final Direction carried = stack.get(CompactMachinesTunnels.TUNNEL_SIDE.get());
    tooltipComponents.add(
        (carried == null
                ? Component.translatable("item.compacttunnels.capability_tunnel.first_free")
                : Component.translatable(
                    "item.compacttunnels.capability_tunnel.side", sideName(carried)))
            .withStyle(ChatFormatting.GRAY));

    tooltipComponents.add(
        Component.translatable("item.compacttunnels.capability_tunnel.limit")
            .withStyle(ChatFormatting.DARK_GRAY));
  }

  static Component sideName(Direction side) {
    final String name = side.getSerializedName();
    return Component.translatableWithFallback(
        "compacttunnels.side." + name, Character.toUpperCase(name.charAt(0)) + name.substring(1));
  }
}
