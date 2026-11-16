package dev.compactmods.compacttunnels.modules.compactmachines;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

final class MachineBindings extends SavedData {
  private static final String FILE_ID = "compacttunnels_machine_bindings";

  private final Map<String, GlobalPos> byRoom = new HashMap<>();

  static MachineBindings get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(MachineBindings::new, MachineBindings::load), FILE_ID);
  }

  @Nullable GlobalPos get(String roomCode) {
    return byRoom.get(roomCode);
  }

  void put(String roomCode, GlobalPos machine) {
    final GlobalPos previous = byRoom.put(roomCode, machine);
    if (!machine.equals(previous)) {
      setDirty();
    }
  }

  private static MachineBindings load(CompoundTag tag, HolderLookup.Provider registries) {
    final MachineBindings data = new MachineBindings();
    for (String roomCode : tag.getAllKeys()) {
      GlobalPos.CODEC
          .parse(NbtOps.INSTANCE, tag.get(roomCode))
          .result()
          .ifPresent(machine -> data.byRoom.put(roomCode, machine));
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    byRoom.forEach(
        (roomCode, machine) ->
            GlobalPos.CODEC
                .encodeStart(NbtOps.INSTANCE, machine)
                .result()
                .ifPresent(encoded -> tag.put(roomCode, encoded)));
    return tag;
  }
}
