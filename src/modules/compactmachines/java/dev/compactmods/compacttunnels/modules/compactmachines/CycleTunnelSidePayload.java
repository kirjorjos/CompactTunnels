package dev.compactmods.compacttunnels.modules.compactmachines;

import dev.compactmods.compacttunnels.common.TunnelConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CycleTunnelSidePayload(BlockPos pos, boolean forward) implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<CycleTunnelSidePayload> TYPE =
      new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath(TunnelConstants.MOD_ID, "cycle_side"));

  public static final StreamCodec<ByteBuf, CycleTunnelSidePayload> STREAM_CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          CycleTunnelSidePayload::pos,
          ByteBufCodecs.BOOL,
          CycleTunnelSidePayload::forward,
          CycleTunnelSidePayload::new);

  @Override
  public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
