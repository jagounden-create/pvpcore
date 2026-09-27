package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Keeps particle spam off players' screens. The server sends these particles itself, so leaving them out of the
 * packets is the only way to remove them: dark damage hearts, crit and sweep particles, and the big explosion
 * emitter with its flying debris (replaced by one small puff; wind charge gusts are left alone).
 * <p>
 * This works on the server's own network classes, which are not part of the plugin API. Everything is looked up by
 * reflection once; if anything is not where it is expected, or a packet ever fails to filter, the packet goes out
 * unchanged and the filter switches itself off. It can never stop a packet from being sent by failing.
 */
public final class ParticleModule extends Module {
   static final String HANDLER = "pvpcore_particles";
   private static final int MAX_FAILURES = 20;
   private final AtomicInteger failures = new AtomicInteger();
   private final Filter filter = new Filter();
   private Packets packets;
   private volatile boolean hearts;
   private volatile boolean hits;
   private volatile boolean explosions;
   private volatile boolean broken;

   public ParticleModule(PvPCore plugin) {
      super(plugin);
   }

   public boolean available() {
      return this.packets != null && !this.broken;
   }

   @Override
   public void start() {
      try {
         this.packets = new Packets();
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         this.packets = null;
         this.plugin.getLogger().fine("Particle filtering is not available: " + e);
         return;
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.inject(player);
      }
   }

   @Override
   public void apply() {
      this.hearts = this.on(Feature.NO_DAMAGE_HEARTS);
      this.hits = this.on(Feature.NO_HIT_PARTICLES);
      this.explosions = this.on(Feature.NO_EXPLOSION_PARTICLES);
   }

   @Override
   public void stop() {
      if (this.packets == null) {
         return;
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         Channel channel = this.channel(player);
         if (channel != null) {
            channel.eventLoop().execute(() -> {
               ChannelPipeline pipeline = channel.pipeline();
               if (pipeline.get(HANDLER) != null) {
                  pipeline.remove(HANDLER);
               }
            });
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      if (this.packets != null && !this.broken) {
         this.inject(event.getPlayer());
      }
   }

   private void inject(Player player) {
      Channel channel = this.channel(player);
      if (channel == null) {
         return;
      }

      channel.eventLoop().execute(() -> {
         try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) == null && pipeline.get("packet_handler") != null) {
               pipeline.addBefore("packet_handler", HANDLER, this.filter);
            }
         } catch (RuntimeException e) {
            this.plugin.getLogger().log(Level.FINE, "Could not add the particle filter for " + player.getName(), e);
         }
      });
   }

   private Channel channel(Player player) {
      try {
         return this.packets.channel(player);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return null;
      }
   }

   /** What to send instead of this packet: the packet itself, a lighter copy, or null to send nothing. */
   Object filter(Object packet) throws ReflectiveOperationException {
      Packets packets = this.packets;
      Class<?> type = packet.getClass();
      if (type == packets.particles) {
         if (!this.hearts && !this.hits) {
            return packet;
         }

         String id = packets.particleId(packets.particleOf.invoke(packet));
         if (this.hearts && "minecraft:damage_indicator".equals(id)) {
            return null;
         }

         return this.hits && hitParticle(id) ? null : packet;
      }

      if (type == packets.animate) {
         if (this.hits) {
            int action = (Integer)packets.animateAction.invoke(packet);
            if (action == CRITICAL_HIT || action == MAGIC_CRITICAL_HIT) {
               return null;
            }
         }

         return packet;
      }

      if (type == packets.explode && this.explosions && packets.explodeConstructor != null) {
         return packets.lighter(packet);
      }

      return packet;
   }

   static final int CRITICAL_HIT = 4;
   static final int MAGIC_CRITICAL_HIT = 5;

   static boolean hitParticle(String id) {
      return "minecraft:crit".equals(id) || "minecraft:enchanted_hit".equals(id) || "minecraft:sweep_attack".equals(id);
   }

   @ChannelHandler.Sharable
   private final class Filter extends ChannelOutboundHandlerAdapter {
      @Override
      public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
         Object out = message;
         if (!ParticleModule.this.broken && ParticleModule.this.packets != null) {
            try {
               out = ParticleModule.this.filter(message);
            } catch (Throwable e) {
               out = message;
               if (ParticleModule.this.failures.incrementAndGet() >= MAX_FAILURES) {
                  ParticleModule.this.broken = true;
                  ParticleModule.this.plugin.getLogger().log(Level.WARNING, "Particle filtering kept failing on this server and has switched itself off.", e);
               }
            }
         }

         if (out == null) {
            promise.trySuccess();
            return;
         }

         super.write(context, out, promise);
      }
   }

   /** The server classes and members the filter needs, looked up once. */
   static final class Packets {
      final Class<?> particles;
      final Method particleOf;
      final Method particleType;
      final Object particleRegistry;
      final Method registryKey;
      final Class<?> animate;
      final Method animateAction;
      final Class<?> explode;
      final Class<?> particleOptions;
      final Object smallExplosion;
      final Constructor<?> explodeConstructor;
      final RecordComponent[] explodeComponents;
      private final Map<Object, String> ids = new ConcurrentHashMap<>();
      private final Map<String, Field> fields = new ConcurrentHashMap<>();
      private Method getHandle;

      Packets() throws ReflectiveOperationException {
         this.particles = Class.forName("net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket");
         this.particleOf = this.particles.getMethod("getParticle");
         this.particleOptions = Class.forName("net.minecraft.core.particles.ParticleOptions");
         this.particleType = this.particleOptions.getMethod("getType");
         this.particleRegistry = Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("PARTICLE_TYPE").get(null);
         this.registryKey = findKeyMethod(this.particleRegistry.getClass());
         this.animate = Class.forName("net.minecraft.network.protocol.game.ClientboundAnimatePacket");
         this.animateAction = this.animate.getMethod("getAction");
         this.explode = Class.forName("net.minecraft.network.protocol.game.ClientboundExplodePacket");
         this.smallExplosion = Class.forName("net.minecraft.core.particles.ParticleTypes").getField("EXPLOSION").get(null);
         Constructor<?> constructor = null;
         RecordComponent[] components = null;
         if (this.explode.isRecord()) {
            components = this.explode.getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
               types[i] = components[i].getType();
               components[i].getAccessor().setAccessible(true);
            }

            constructor = this.explode.getDeclaredConstructor(types);
            constructor.setAccessible(true);
         }

         this.explodeConstructor = constructor;
         this.explodeComponents = components;
         // Fail now, not on the first packet, if the ids can't be read.
         if (this.particleId(this.smallExplosion) == null) {
            throw new IllegalStateException("particle ids unreadable");
         }
      }

      private static Method findKeyMethod(Class<?> registry) throws NoSuchMethodException {
         for (Method method : registry.getMethods()) {
            if (method.getName().equals("getKey") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class) {
               return method;
            }
         }

         throw new NoSuchMethodException(registry.getName() + ".getKey(Object)");
      }

      String particleId(Object options) throws ReflectiveOperationException {
         if (options == null) {
            return null;
         }

         Object type = this.particleType.invoke(options);
         String cached = this.ids.get(type);
         if (cached != null) {
            return cached;
         }

         Object key = this.registryKey.invoke(this.particleRegistry, type);
         String id = key == null ? "" : key.toString();
         this.ids.put(type, id);
         return id;
      }

      /** The same explosion with one small puff instead of the big emitter, and no block debris particles. */
      Object lighter(Object packet) throws ReflectiveOperationException {
         Object[] values = new Object[this.explodeComponents.length];
         boolean big = false;
         for (int i = 0; i < values.length; i++) {
            values[i] = this.explodeComponents[i].getAccessor().invoke(packet);
            if (this.particleOptions.isInstance(values[i]) && "minecraft:explosion_emitter".equals(this.particleId(values[i]))) {
               values[i] = this.smallExplosion;
               big = true;
            }
         }

         if (!big) {
            return packet;
         }

         for (int i = 0; i < values.length; i++) {
            Class<?> type = this.explodeComponents[i].getType();
            if (type.getSimpleName().equals("WeightedList")) {
               Object empty = emptyOf(type);
               if (empty != null) {
                  values[i] = empty;
               }
            }
         }

         return this.explodeConstructor.newInstance(values);
      }

      private static Object emptyOf(Class<?> type) {
         try {
            Method of = type.getMethod("of");
            return Modifier.isStatic(of.getModifiers()) && type.isAssignableFrom(of.getReturnType()) ? of.invoke(null) : null;
         } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
         }
      }

      Channel channel(Player player) throws ReflectiveOperationException {
         if (this.getHandle == null) {
            this.getHandle = player.getClass().getMethod("getHandle");
         }

         Object serverPlayer = this.getHandle.invoke(player);
         Object listener = this.field(serverPlayer.getClass(), "connection").get(serverPlayer);
         Object connection = this.field(listener.getClass(), "connection").get(listener);
         for (Class<?> type = connection.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
               if (Channel.class.isAssignableFrom(field.getType())) {
                  field.setAccessible(true);
                  return (Channel)field.get(connection);
               }
            }
         }

         return null;
      }

      private Field field(Class<?> owner, String name) throws NoSuchFieldException {
         String cacheKey = owner.getName() + "#" + name;
         Field cached = this.fields.get(cacheKey);
         if (cached != null) {
            return cached;
         }

         for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
               Field field = type.getDeclaredField(name);
               field.setAccessible(true);
               this.fields.put(cacheKey, field);
               return field;
            } catch (NoSuchFieldException ignored) {
               // keep looking up the hierarchy
            }
         }

         throw new NoSuchFieldException(owner.getName() + "." + name);
      }
   }
}
