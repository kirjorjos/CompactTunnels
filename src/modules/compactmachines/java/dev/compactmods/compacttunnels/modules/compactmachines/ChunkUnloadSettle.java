package dev.compactmods.compacttunnels.modules.compactmachines;

import com.mojang.logging.LogUtils;
import dev.compactmods.compacttunnels.common.TunnelConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = TunnelConstants.MOD_ID)
public final class ChunkUnloadSettle {
  private static final Logger LOGGER = LogUtils.getLogger();

  private ChunkUnloadSettle() {}

  @SubscribeEvent
  public static void onServerStopping(ServerStoppingEvent event) {
    if (FMLEnvironment.production) {
      return;
    }

    for (ServerLevel level : event.getServer().getAllLevels()) {
      try {
        report(level);
      } catch (Throwable t) {
        LOGGER.warn("Could not read chunk state for {}", level.dimension().location(), t);
      }
    }
  }

  private static void report(ServerLevel level) throws ReflectiveOperationException {
    final ServerChunkCache chunks = level.getChunkSource();

    chunks.removeTicketsOnClosing();

    final Object chunkMap = chunks.chunkMap;
    final Object holders = field(chunkMap, "updatingChunkMap");

    LOGGER.info(
        "Chunk state for {} before shutdown: queue {}, pending {}, toDrop {}, loaded {},"
            + " generating {}, tickets {}",
        level.dimension().location(),
        size(field(chunkMap, "unloadQueue")),
        size(field(chunkMap, "pendingUnloads")),
        size(field(chunkMap, "toDrop")),
        size(holders),
        generating(holders),
        tickets(field(chunkMap, "distanceManager")));
  }

  private static int size(@Nullable Object container) {
    if (container instanceof Collection<?> collection) {
      return collection.size();
    }

    return container instanceof Map<?, ?> map ? map.size() : -1;
  }

  private static int generating(@Nullable Object holders) {
    if (!(holders instanceof Map<?, ?> map)) {
      return -1;
    }

    int generating = 0;
    for (Object holder : map.values()) {
      if (invoke(holder, "getGenerationRefCount") instanceof Number refs && refs.intValue() != 0) {
        generating++;
      }
    }

    return generating;
  }

  private static boolean tickets(@Nullable Object distanceManager) {
    return invoke(distanceManager, "hasTickets") instanceof Boolean hasTickets && hasTickets;
  }

  @Nullable private static Object invoke(@Nullable Object target, String name) {
    if (target == null) {
      return null;
    }

    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        final Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
      } catch (NoSuchMethodException e) {
      } catch (ReflectiveOperationException | RuntimeException e) {
        return null;
      }
    }

    return null;
  }

  private static Object field(Object target, String name) throws ReflectiveOperationException {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
      } catch (NoSuchFieldException e) {
      }
    }

    throw new NoSuchFieldException(name);
  }
}
