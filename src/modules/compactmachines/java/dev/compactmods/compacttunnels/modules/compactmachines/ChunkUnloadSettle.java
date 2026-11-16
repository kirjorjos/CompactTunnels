package dev.compactmods.compacttunnels.modules.compactmachines;

import com.mojang.logging.LogUtils;
import dev.compactmods.compacttunnels.common.TunnelConstants;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = TunnelConstants.MOD_ID)
public final class ChunkUnloadSettle {
  private static final Logger LOGGER = LogUtils.getLogger();

  private static final long LIFT_INTERVAL_MS = 25L;
  private static final long LIFT_HOLD_MS = 2L;
  private static final int MAX_LIFTED = 4096;
  private static final long IDLE_EXIT_NANOS = TimeUnit.SECONDS.toNanos(10);

  private static volatile boolean watching;

  private ChunkUnloadSettle() {}

  @SubscribeEvent
  public static void onServerStopping(ServerStoppingEvent event) {
    final List<Queue<?>> queues = new ArrayList<>();

    for (ServerLevel level : event.getServer().getAllLevels()) {
      try {
        final Queue<?> queue = settle(level);
        if (queue != null) {
          queues.add(queue);
        }
      } catch (Throwable t) {
        LOGGER.warn("Could not prepare chunk unloading for {}", level.dimension().location(), t);
      }
    }

    if (!queues.isEmpty()) {
      startWatcher(queues);
    }
  }

  @Nullable private static Queue<?> settle(ServerLevel level) throws ReflectiveOperationException {
    final ServerChunkCache chunks = level.getChunkSource();

    chunks.removeTicketsOnClosing();

    final Object chunkMap = chunks.chunkMap;
    final Object unloadQueue = field(chunkMap, "unloadQueue");
    if (!(unloadQueue instanceof Queue<?> queue)) {
      return null;
    }

    final int queued = queue.size();
    final int pending = clear(field(chunkMap, "pendingUnloads"));
    final int dropping = clear(field(chunkMap, "toDrop"));
    queue.clear();

    if (queued > 0 || pending > 0 || dropping > 0) {
      LOGGER.info(
          "Prepared chunk unloading for {} before shutdown (queue {}, pending {}, toDrop {})",
          level.dimension().location(),
          queued,
          pending,
          dropping);
    }

    return queue;
  }

  private static void startWatcher(List<Queue<?>> queues) {
    if (watching) {
      return;
    }

    watching = true;
    final Thread watcher = new Thread(() -> watch(queues), "compacttunnels-unload-watcher");
    watcher.setDaemon(true);
    watcher.start();
  }

  private static void watch(List<Queue<?>> queues) {
    long idleSince = 0;

    while (true) {
      try {
        Thread.sleep(LIFT_INTERVAL_MS);
      } catch (InterruptedException e) {
        return;
      }

      boolean lifted = false;
      for (Queue<?> queue : queues) {
        lifted |= lift(queue);
      }

      if (lifted) {
        idleSince = 0;
      } else if (idleSince == 0) {
        idleSince = System.nanoTime();
      } else if (System.nanoTime() - idleSince > IDLE_EXIT_NANOS) {
        watching = false;
        return;
      }
    }
  }

  private static boolean lift(Queue<?> queue) {
    final List<Runnable> held = new ArrayList<>();
    Object item = queue.poll();

    while (item != null && held.size() < MAX_LIFTED) {
      if (item instanceof Runnable runnable) {
        held.add(runnable);
      }

      item = queue.poll();
    }

    if (held.isEmpty()) {
      return false;
    }

    try {
      Thread.sleep(LIFT_HOLD_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    for (Runnable runnable : held) {
      add(queue, runnable);
    }

    return true;
  }

  @SuppressWarnings("unchecked")
  private static void add(Queue<?> queue, Runnable runnable) {
    ((Queue<Runnable>) queue).add(runnable);
  }

  private static int clear(@Nullable Object holder) {
    if (holder instanceof Collection<?> collection) {
      final int size = collection.size();
      collection.clear();
      return size;
    }

    if (holder instanceof Map<?, ?> map) {
      final int size = map.size();
      map.clear();
      return size;
    }

    if (holder == null) {
      return -1;
    }

    try {
      final Object size = holder.getClass().getMethod("size").invoke(holder);
      holder.getClass().getMethod("clear").invoke(holder);
      return size instanceof Number number ? number.intValue() : -1;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return -1;
    }
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
