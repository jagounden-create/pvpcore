package com.pvpcore.module;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * Re-sends a few pieces of a player's synced state (the "is using an item" flags, health, golden hearts, effect
 * swirls) to the players watching them. Movement flags (sprinting, sneaking, gliding) are never part of it: the
 * player's own game owns those, and an echo arriving late could cost them a sprint reset. That is all a viewer's game needs to redraw a stuck shield or stale golden
 * hearts, and unlike re-showing the whole player it can't flicker or make a hit miss.
 * <p>
 * The server's network classes are not plugin API, so everything is looked up by reflection once. When anything is
 * missing, {@link #create()} returns null and callers fall back to re-showing the player through the API.
 */
final class Resync {
   enum Part {
      /** For viewers: shield raised or not (the using-item flags). */
      BLOCKING,
      /** For viewers: the using-item flags (a finished apple), health, golden hearts and potion swirls. */
      HEARTS,
      /** For the player themselves: health and golden hearts only. */
      OWN_HEARTS
   }

   private final Method getEntityData;
   private final Method getId;
   private final Method get;
   private final Method create;
   private final Constructor<?> packet;
   private final List<Object> blocking;
   private final List<Object> hearts;
   private final List<Object> ownHearts;
   private Method getHandle;
   private Field connection;
   private Method send;

   private Resync() throws ReflectiveOperationException {
      Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
      Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity");
      Class<?> human = Class.forName("net.minecraft.world.entity.player.Player");
      Class<?> data = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
      Class<?> accessor = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
      Class<?> value = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
      Class<?> packetType = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
      this.getEntityData = entity.getMethod("getEntityData");
      this.getId = entity.getMethod("getId");
      this.get = data.getMethod("get", accessor);
      this.create = value.getMethod("create", accessor, Object.class);
      this.packet = packetType.getConstructor(int.class, List.class);
      Object livingFlags = accessor(living, "DATA_LIVING_ENTITY_FLAGS");
      Object health = accessor(living, "DATA_HEALTH_ID");
      Object absorption = accessor(human, "DATA_PLAYER_ABSORPTION_ID");
      this.blocking = List.of(livingFlags);
      this.ownHearts = List.of(health, absorption);
      List<Object> hearts = new ArrayList<>(List.of(livingFlags, health, absorption));
      for (String optional : new String[]{"DATA_EFFECT_PARTICLES", "DATA_EFFECT_AMBIENCE_ID"}) {
         try {
            hearts.add(accessor(living, optional));
         } catch (ReflectiveOperationException ignored) {
            // not on this version; the rest still helps
         }
      }

      this.hearts = List.copyOf(hearts);
   }

   /** The re-sender, or null when this server's internals aren't the expected ones. */
   static Resync create() {
      try {
         return new Resync();
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return null;
      }
   }

   private static Object accessor(Class<?> owner, String name) throws ReflectiveOperationException {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      Object accessor = field.get(null);
      if (accessor == null) {
         throw new NoSuchFieldException(owner.getName() + "." + name + " is null");
      }

      return accessor;
   }

   /**
    * Sends the current value of {@code part} for {@code target} to every viewer.
    *
    * @return false when sending failed; the caller should fall back
    */
   boolean send(Player target, Collection<Player> viewers, Part part) {
      if (viewers.isEmpty()) {
         return true;
      }

      try {
         Object handle = this.handle(target);
         Object data = this.getEntityData.invoke(handle);
         List<Object> values = new ArrayList<>();
         List<Object> accessors = switch (part) {
            case BLOCKING -> this.blocking;
            case HEARTS -> this.hearts;
            case OWN_HEARTS -> this.ownHearts;
         };
         for (Object accessor : accessors) {
            values.add(this.create.invoke(null, accessor, this.get.invoke(data, accessor)));
         }

         Object message = this.packet.newInstance(this.getId.invoke(handle), values);
         for (Player viewer : viewers) {
            Object listener = this.connection(this.handle(viewer));
            if (listener != null) {
               this.sender(listener, message).invoke(listener, message);
            }
         }

         return true;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return false;
      }
   }

   private Object handle(Player player) throws ReflectiveOperationException {
      if (this.getHandle == null) {
         this.getHandle = player.getClass().getMethod("getHandle");
      }

      return this.getHandle.invoke(player);
   }

   private Object connection(Object serverPlayer) throws ReflectiveOperationException {
      if (this.connection == null) {
         Field field = null;
         for (Class<?> type = serverPlayer.getClass(); type != null && field == null; type = type.getSuperclass()) {
            try {
               field = type.getDeclaredField("connection");
            } catch (NoSuchFieldException ignored) {
               // keep looking up the hierarchy
            }
         }

         if (field == null) {
            throw new NoSuchFieldException("connection");
         }

         field.setAccessible(true);
         this.connection = field;
      }

      return this.connection.get(serverPlayer);
   }

   private Method sender(Object listener, Object message) throws NoSuchMethodException {
      if (this.send == null) {
         for (Method method : listener.getClass().getMethods()) {
            if (method.getName().equals("send") && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(message)) {
               this.send = method;
               break;
            }
         }

         if (this.send == null) {
            throw new NoSuchMethodException("send(Packet)");
         }
      }

      return this.send;
   }
}
