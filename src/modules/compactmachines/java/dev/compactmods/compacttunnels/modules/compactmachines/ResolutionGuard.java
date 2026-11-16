package dev.compactmods.compacttunnels.modules.compactmachines;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;

final class ResolutionGuard {
  private static final ThreadLocal<Set<GlobalPos>> RESOLVING =
      ThreadLocal.withInitial(HashSet::new);

  private ResolutionGuard() {}

  @Nullable static <T> T resolve(GlobalPos here, Supplier<T> resolution) {
    final Set<GlobalPos> resolving = RESOLVING.get();
    if (!resolving.add(here)) {
      return null;
    }

    try {
      return resolution.get();
    } finally {
      resolving.remove(here);
      if (resolving.isEmpty()) {
        RESOLVING.remove();
      }
    }
  }
}
