package dev.compactmods.compacttunnels.modules.compactmachines;

import com.mojang.logging.LogUtils;
import dev.compactmods.compacttunnels.common.TunnelConstants;
import dev.compactmods.machines.api.CompactMachines;
import dev.compactmods.machines.api.room.RoomInstance;
import dev.compactmods.machines.machine.Machines;
import dev.compactmods.machines.machine.block.BoundCompactMachineBlockEntity;
import dev.compactmods.machines.machine.block.CompactMachineBlock;
import dev.compactmods.machines.machine.block.UnboundCompactMachineEntity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.impl.WailaCommonRegistration;
import snownee.jade.impl.ui.ElementHelper;

@WailaPlugin
public final class TunnelJadePlugin implements IWailaPlugin {
  private static final Logger LOGGER = LogUtils.getLogger();

  private static final Set<ResourceLocation> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

  public TunnelJadePlugin() {}

  @Override
  public void register(IWailaCommonRegistration registration) {
    registration.registerBlockDataProvider(TunnelData.INSTANCE, CapabilityTunnelBlock.class);
    registration.registerBlockDataProvider(TunnelData.INSTANCE, CompactMachineBlock.class);
  }

  @Override
  public void registerClient(IWailaClientRegistration registration) {
    registration.registerBlockComponent(TunnelLine.INSTANCE, CapabilityTunnelBlock.class);
    registration.registerBlockComponent(TunnelLine.INSTANCE, CompactMachineBlock.class);
  }

  private static void reportFailure(ResourceLocation uid, RuntimeException failure) {
    if (REPORTED_FAILURES.add(uid)) {
      LOGGER.warn(
          "Jade provider {} failed on a tunnel's far side; further failures from it are not logged",
          uid,
          failure);
    }
  }

  public static final class TunnelData implements IServerDataProvider<BlockAccessor> {
    static final TunnelData INSTANCE = new TunnelData();

    private static final String KEY = TunnelConstants.MOD_ID;
    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(TunnelConstants.MOD_ID, "tunnel");

    private static final String VIEW_TUNNEL = "tunnel";
    private static final String VIEW_MACHINE = "machine";

    private static final int NO_CONNECTION = 0;
    private static final int UNLOADED = 1;
    private static final int CONNECTED = 2;

    private static final int OTHER_EMPTY = 0;
    private static final int OTHER_MACHINE = 1;
    private static final int OTHER_BLOCK = 2;

    private TunnelData() {}

    @Override
    public ResourceLocation getUid() {
      return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
      if (!(accessor.getLevel() instanceof ServerLevel level)) {
        return;
      }

      final BlockEntity blockEntity = accessor.getBlockEntity();
      final CompoundTag own = new CompoundTag();

      final Player player = accessor.getPlayer();
      final boolean showDetails = accessor.showDetails();

      if (blockEntity instanceof CapabilityTunnelBlockEntity tunnel) {
        own.putString("view", VIEW_TUNNEL);
        if (!fromTunnel(level, tunnel, own, player, showDetails)) {
          return;
        }
      } else if (blockEntity instanceof BoundCompactMachineBlockEntity machine) {
        own.putString("view", VIEW_MACHINE);
        if (!fromMachine(level, machine, accessor.getSide(), own, player, showDetails)) {
          return;
        }
      } else {
        return;
      }

      data.put(KEY, own);
    }

    private static boolean fromTunnel(
        ServerLevel level,
        CapabilityTunnelBlockEntity tunnel,
        CompoundTag own,
        @Nullable Player player,
        boolean showDetails) {
      final String roomCode =
          RoomMachineIndex.roomCodeAt(level, tunnel.getBlockPos(), tunnel.farSide());
      if (roomCode == null) {
        return false;
      }

      final Direction side = tunnel.side();
      own.putString("face", side.getSerializedName());
      count(own, level.getServer(), roomCode);

      final GlobalPos machine = RoomMachineIndex.findMachine(level.getServer(), roomCode);
      if (machine == null) {
        own.putInt("state", NO_CONNECTION);
        return true;
      }

      final ServerLevel machineLevel = level.getServer().getLevel(machine.dimension());
      if (machineLevel == null || !machineLevel.isLoaded(machine.pos())) {
        own.putInt("state", UNLOADED);
        return true;
      }

      own.putInt("state", CONNECTED);
      own.putString("side", side.getSerializedName());
      other(
          own, machineLevel, machine.pos().relative(side), side.getOpposite(), player, showDetails);
      return true;
    }

    private static boolean fromMachine(
        ServerLevel level,
        BoundCompactMachineBlockEntity machine,
        Direction face,
        CompoundTag own,
        @Nullable Player player,
        boolean showDetails) {
      final String roomCode = machine.connectedRoom();
      if (roomCode == null) {
        return false;
      }

      own.putString("face", face.getSerializedName());
      count(own, level.getServer(), roomCode);

      final GlobalPos tunnelPos = TunnelClaims.tunnelFor(roomCode, face);
      final ServerLevel roomLevel =
          tunnelPos == null ? null : level.getServer().getLevel(tunnelPos.dimension());

      if (tunnelPos == null || roomLevel == null || !roomLevel.isLoaded(tunnelPos.pos())) {
        own.putInt("state", roomLoaded(level.getServer(), roomCode) ? NO_CONNECTION : UNLOADED);
        return true;
      }

      final BlockState tunnelState = roomLevel.getBlockState(tunnelPos.pos());
      if (!(tunnelState.getBlock() instanceof CapabilityTunnelBlock)) {
        own.putInt("state", NO_CONNECTION);
        return true;
      }

      final Direction inward = tunnelState.getValue(CapabilityTunnelBlock.FACING).getOpposite();
      final BlockPos inside = tunnelPos.pos().relative(inward);

      own.putInt("state", CONNECTED);
      own.putString("side", tunnelState.getValue(CapabilityTunnelBlock.SIDE).getSerializedName());
      other(
          own,
          roomLevel,
          inside,
          tunnelState.getValue(CapabilityTunnelBlock.FACING),
          player,
          showDetails);
      return true;
    }

    private static void count(CompoundTag own, MinecraftServer server, String roomCode) {
      own.putInt("used", TunnelClaims.used(server, roomCode));
      own.putInt("limit", TunnelClaims.limit());
    }

    private static void other(
        CompoundTag own,
        ServerLevel level,
        BlockPos pos,
        Direction face,
        @Nullable Player player,
        boolean showDetails) {
      final BlockState state = level.getBlockState(pos);
      if (state.isAir()) {
        own.putInt("other", OTHER_EMPTY);
        return;
      }

      final BlockEntity blockEntity = level.getBlockEntity(pos);
      if (isMachine(blockEntity)) {
        own.putInt("other", OTHER_MACHINE);
        customName(blockEntity).ifPresent(name -> own.putString("other_name", name.getString()));
        return;
      }

      own.putInt("other", OTHER_BLOCK);
      own.put("other_state", NbtUtils.writeBlockState(state));
      own.putLong("other_pos", pos.asLong());
      own.putString("other_dim", level.dimension().location().toString());
      own.putString("other_face", face.getSerializedName());
      if (blockEntity != null) {
        own.put("other_be", blockEntity.saveWithFullMetadata(level.registryAccess()));
      }
      fallbackName(own, state, blockEntity);
      own.put("other_data", farData(level, pos, face, player, showDetails, state, blockEntity));
    }

    private static void fallbackName(
        CompoundTag own, BlockState state, @Nullable BlockEntity blockEntity) {
      final Item item = state.getBlock().asItem();
      if (item == Items.AIR) {
        own.putString("other_block", state.getBlock().getDescriptionId());
      } else {
        own.putString("other_item", BuiltInRegistries.ITEM.getKey(item).toString());
      }
      customName(blockEntity).ifPresent(name -> own.putString("other_name", name.getString()));
    }

    private static CompoundTag farData(
        ServerLevel level,
        BlockPos pos,
        Direction face,
        @Nullable Player player,
        boolean showDetails,
        BlockState state,
        @Nullable BlockEntity blockEntity) {
      final CompoundTag data = new CompoundTag();

      data.putInt("x", pos.getX());
      data.putInt("y", pos.getY());
      data.putInt("z", pos.getZ());

      final BlockAccessor far =
          new BlockAccessorImpl.Builder()
              .level(level)
              .player(player)
              .serverData(data)
              .serverConnected(true)
              .showDetails(showDetails)
              .hit(new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false))
              .blockState(state)
              .blockEntity(() -> blockEntity)
              .build();

      try {
        final List<IServerDataProvider<BlockAccessor>> providers =
            WailaCommonRegistration.instance().getBlockNBTProviders(state.getBlock(), blockEntity);
        for (IServerDataProvider<BlockAccessor> provider : providers) {
          if (isOurs(provider.getUid())) {
            continue;
          }

          try {
            if (!provider.shouldRequestData(far)) {
              continue;
            }

            provider.appendServerData(data, far);
          } catch (RuntimeException failure) {
            reportFailure(provider.getUid(), failure);
          }
        }

      } catch (RuntimeException failure) {
        LOGGER.warn("Could not read Jade data for a tunnel's far side", failure);
      }

      return far.getServerData();
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
      return IWailaConfig.get().getPlugin().get(TunnelLine.UID);
    }

    private static boolean isOurs(ResourceLocation uid) {
      return uid.equals(TunnelData.UID) || uid.equals(TunnelLine.UID);
    }

    private static Optional<Component> customName(@Nullable BlockEntity blockEntity) {
      if (blockEntity instanceof BoundCompactMachineBlockEntity machine) {
        return machine.getCustomName();
      }

      if (blockEntity instanceof UnboundCompactMachineEntity machine) {
        return Optional.ofNullable(machine.customName());
      }

      if (blockEntity == null) {
        return Optional.empty();
      }

      return Optional.ofNullable(blockEntity.components().get(DataComponents.CUSTOM_NAME));
    }

    private static boolean isMachine(@Nullable BlockEntity blockEntity) {
      return blockEntity != null
          && (blockEntity.getType() == Machines.BlockEntities.MACHINE.get()
              || blockEntity.getType() == Machines.BlockEntities.UNBOUND_MACHINE.get());
    }

    private static boolean roomLoaded(MinecraftServer server, String roomCode) {
      final RoomInstance room = CompactMachines.room(server, roomCode).orElse(null);
      if (room == null || room.level() == null) {
        return false;
      }

      final ServerLevel level = room.level();
      return room.boundaries().innerChunkPositions().anyMatch(pos -> level.hasChunk(pos.x, pos.z));
    }
  }

  public static final class TunnelLine implements IBlockComponentProvider {
    static final TunnelLine INSTANCE = new TunnelLine();

    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(TunnelConstants.MOD_ID, "tunnel_line");

    private static final String VIEW_MACHINE = "machine";

    private TunnelLine() {}

    @Override
    public ResourceLocation getUid() {
      return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
      final CompoundTag data = accessor.getServerData();
      if (!data.contains(TunnelConstants.MOD_ID)) {
        return;
      }

      final CompoundTag own = data.getCompound(TunnelConstants.MOD_ID);
      final String face = own.getString("face");
      if (face.isEmpty()) {
        return;
      }

      final int state = own.getInt("state");
      tooltip.add(faceLine(own, face, state == TunnelData.CONNECTED));

      final boolean machineView = VIEW_MACHINE.equals(own.getString("view"));

      switch (state) {
        case 0 ->
            tooltip.add(
                Component.translatable(
                    machineView
                        ? "compacttunnels.jade.disconnected"
                        : "compacttunnels.jade.no_machine"));
        case 1 -> tooltip.add(Component.translatable("compacttunnels.jade.unloaded"));
        default -> connected(tooltip, own, accessor, config);
      }
    }

    private static Component faceLine(CompoundTag own, String face, boolean connected) {
      return connected
          ? Component.translatable(
              "compacttunnels.jade.current_face_used",
              side(face),
              own.getInt("used"),
              own.getInt("limit"))
          : Component.translatable("compacttunnels.jade.current_face", side(face));
    }

    private static void connected(
        ITooltip tooltip, CompoundTag own, BlockAccessor accessor, IPluginConfig config) {
      switch (own.getInt("other")) {
        case 0 -> tooltip.add(Component.translatable("compacttunnels.jade.connected_empty"));
        case 1 -> tooltip.add(machineLine(own));
        default -> farTooltip(tooltip, own, accessor, config);
      }
    }

    private static Component machineLine(CompoundTag own) {
      final String name = own.getString("other_name");
      return name.isEmpty()
          ? Component.translatable("compacttunnels.jade.connected_machine")
          : Component.translatable("compacttunnels.jade.connected_machine_named", name);
    }

    private static void farTooltip(
        ITooltip tooltip, CompoundTag own, BlockAccessor accessor, IPluginConfig config) {
      if (!TunnelClientConfig.ghostBlock()) {
        nameOnly(tooltip, own);
        return;
      }

      final BlockState state = blockState(own.getCompound("other_state"));
      if (state == null) {
        nameOnly(tooltip, own);
        return;
      }

      final Minecraft client = Minecraft.getInstance();
      final Level level = accessor.getLevel();
      final BlockPos pos = BlockPos.of(own.getLong("other_pos"));
      final Direction face = direction(own.getString("other_face"));
      final BlockEntity farEntity =
          farEntity(level, pos, state, own.getString("other_dim"), own.getCompound("other_be"));

      final BlockAccessor far =
          new BlockAccessorImpl.Builder()
              .level(level)
              .player(client.player)
              .serverData(own.getCompound("other_data"))
              .serverConnected(true)
              .showDetails(accessor.showDetails())
              .hit(new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false))
              .blockState(state)
              .blockEntity(() -> farEntity)
              .build();

      final ITooltip embedded = ElementHelper.INSTANCE.tooltip();
      farLines(embedded, far, state, config);

      if (embedded.isEmpty()) {
        nameOnly(tooltip, own);
        return;
      }

      final IBoxElement box = ElementHelper.INSTANCE.box(embedded, BoxStyle.getNestedBox().clone());
      final ItemStack icon = new ItemStack(state.getBlock().asItem());
      if (!icon.isEmpty()) {
        box.setIcon(ElementHelper.INSTANCE.item(icon));
      }
      tooltip.add(box);
    }

    private static void farLines(
        ITooltip tooltip, BlockAccessor far, BlockState state, IPluginConfig config) {
      for (IComponentProvider<BlockAccessor> provider :
          WailaClientRegistration.instance()
              .getBlockProviders(state.getBlock(), candidate -> isEnabled(candidate, config))) {
        final ResourceLocation uid = provider.getUid();
        if (isOurs(uid) || uid.equals(JadeIds.CORE_BLOCK_FACE)) {
          continue;
        }

        try {
          provider.appendTooltip(tooltip, far, config);
        } catch (RuntimeException failure) {
          reportFailure(uid, failure);
        }
      }
    }

    @Nullable private static BlockEntity farEntity(
        Level level, BlockPos pos, BlockState state, String dimension, CompoundTag tag) {
      if (dimension.equals(level.dimension().location().toString()) && level.isLoaded(pos)) {
        final BlockEntity loaded = level.getBlockEntity(pos);
        if (loaded != null) {
          return loaded;
        }
      }

      if (tag.isEmpty()) {
        return null;
      }

      try {
        return BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
      } catch (RuntimeException failure) {
        return null;
      }
    }

    @Nullable private static BlockState blockState(CompoundTag tag) {
      if (tag.isEmpty()) {
        return null;
      }

      try {
        final BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
        return state.isAir() ? null : state;
      } catch (RuntimeException failure) {
        return null;
      }
    }

    private static Direction direction(String name) {
      final Direction direction = name.isEmpty() ? null : Direction.byName(name);
      return direction == null ? Direction.NORTH : direction;
    }

    private static boolean isOurs(ResourceLocation uid) {
      return uid.equals(TunnelLine.UID) || uid.equals(TunnelData.UID);
    }

    private static boolean isEnabled(IComponentProvider<?> provider, IPluginConfig config) {
      if (!config.get(provider)) {
        return false;
      }

      return IWailaConfig.get().getGeneral().getEnableAccessibilityPlugin()
          || !JadeIds.isAccess(provider.getUid());
    }

    private static void nameOnly(ITooltip tooltip, CompoundTag own) {
      final String customName = own.getString("other_name");
      if (!customName.isEmpty()) {
        tooltip.add(Component.literal(customName));
        return;
      }

      final Item item = item(own.getString("other_item"));
      if (item instanceof BlockItem blockItem) {
        tooltip.add(blockItem.getBlock().getName());
        return;
      }

      tooltip.add(Component.translatable(own.getString("other_block")));
    }

    private static Item item(String id) {
      final ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
      return key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
    }

    private static Component side(String side) {
      return Component.translatable("compacttunnels.side." + side);
    }
  }
}
