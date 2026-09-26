package com.pvpcore.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Tells Bedrock (Geyser) players apart. Uses Floodgate's or Geyser's API when either is installed on this server,
 * found by reflection so neither is needed to build or run. Without them, Floodgate's UUID format is used: Floodgate
 * gives unlinked Bedrock players a UUID whose first half is all zeros.
 */
public final class Bedrock {
   private static final Map<UUID, Boolean> CACHE = new ConcurrentHashMap<>();
   private static volatile Lookup floodgate;
   private static volatile Lookup geyser;
   private static volatile boolean resolved;

   private Bedrock() {
   }

   public static boolean isBedrock(Player player) {
      return CACHE.computeIfAbsent(player.getUniqueId(), Bedrock::lookup);
   }

   public static void forget(UUID id) {
      CACHE.remove(id);
   }

   public static void reset() {
      CACHE.clear();
      resolved = false;
   }

   /** Which source answers the question on this server, for the status screen. */
   public static String source() {
      resolve();
      if (floodgate != null) {
         return "Floodgate";
      }

      return geyser != null ? "Geyser" : "UUID format";
   }

   private static boolean lookup(UUID id) {
      resolve();
      Boolean answer = ask(floodgate, id);
      if (answer == null) {
         answer = ask(geyser, id);
      }

      return answer != null ? answer : id.getMostSignificantBits() == 0L;
   }

   private static Boolean ask(Lookup lookup, UUID id) {
      if (lookup == null) {
         return null;
      }

      try {
         Object instance = lookup.instance().invoke(null);
         return instance == null ? null : (Boolean)lookup.check().invoke(instance, id);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return null;
      }
   }

   private static void resolve() {
      if (resolved) {
         return;
      }

      floodgate = find("org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer");
      geyser = find("org.geysermc.geyser.api.GeyserApi", "api", "isBedrockPlayer");
      resolved = true;
   }

   private static Lookup find(String className, String instanceMethod, String checkMethod) {
      try {
         Class<?> api = Class.forName(className);
         return new Lookup(api.getMethod(instanceMethod), api.getMethod(checkMethod, UUID.class));
      } catch (ReflectiveOperationException | LinkageError e) {
         return null;
      }
   }

   private record Lookup(Method instance, Method check) {
   }
}
