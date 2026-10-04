package com.pvpcore.module;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Ender pearls: smooth landings, anti-glitch, lifetime, landing damage and cooldown.
 * <ul>
 *    <li>Smooth pearls put you exactly where the pearl hit, shifted just enough to fit.</li>
 *    <li>Anti-glitch checks every pearl landing against real block shapes: a landing inside a block is moved to the
 *    nearest open spot on your side, or the pearl is cancelled (and given back). Pearls never cross worlds.</li>
 *    <li>Lifetime removes pearls older than a limit, so stasis chambers can't hold a teleport for later.</li>
 * </ul>
 */
public final class PearlModule extends Module {
   private static final double MAX_SHIFT_SQUARED = 25.0;
   private static final int HIT_TTL_TICKS = 2;
   private final Map<UUID, Hit> hits = new ConcurrentHashMap<>();
   /** Tick of each player's last pearl landing: the landing damage follows in the same tick. */
   private final Map<UUID, Integer> landed = new ConcurrentHashMap<>();
   /** Pearls in the world, for the lifetime limit. */
   private final Map<UUID, EnderPearl> pearls = new ConcurrentHashMap<>();
   private BukkitTask sweep;

   public PearlModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      this.sweep = Bukkit.getScheduler().runTaskTimer(this.plugin, this::sweepPearls, 20L, 20L);
   }

   @Override
   public void apply() {
      if (!this.on(Feature.PEARL_LIFETIME)) {
         this.pearls.clear();
      }
   }

   @Override
   public void stop() {
      if (this.sweep != null) {
         this.sweep.cancel();
      }

      this.pearls.clear();
   }

   // ------------------------------------------------------------------ lifetime

   private int lifetimeTicks() {
      return (int)Math.round(this.settings().value(Feature.PEARL_LIFETIME) * 20.0);
   }

   private void sweepPearls() {
      if (this.pearls.isEmpty() || !this.on(Feature.PEARL_LIFETIME)) {
         return;
      }

      int max = this.lifetimeTicks();
      Iterator<EnderPearl> iterator = this.pearls.values().iterator();
      while (iterator.hasNext()) {
         EnderPearl pearl = iterator.next();
         if (!pearl.isValid()) {
            iterator.remove();
         } else if (pearl.getTicksLived() > max) {
            pearl.remove();
            iterator.remove();
         }
      }
   }

   /** Pearls thrown now, and pearls that come back with a chunk or a player who logged out with them in flight. */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onPearlAdded(EntityAddToWorldEvent event) {
      if (event.getEntity() instanceof EnderPearl pearl && this.on(Feature.PEARL_LIFETIME)) {
         this.pearls.put(pearl.getUniqueId(), pearl);
      }
   }

   /** A pearl past its lifetime that lands anyway (between sweeps) teleports nobody. */
   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onOldPearl(ProjectileHitEvent event) {
      if (event.getEntity() instanceof EnderPearl pearl && this.on(Feature.PEARL_LIFETIME) && pearl.getTicksLived() > this.lifetimeTicks()) {
         event.setCancelled(true);
         pearl.remove();
         this.pearls.remove(pearl.getUniqueId());
      }
   }

   // ------------------------------------------------------------------ anti-glitch

   /** After smooth landing and other plugins have had their say: the landing must be somewhere a player fits. */
   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPearlGlitch(PlayerTeleportEvent event) {
      if (event.getCause() != TeleportCause.ENDER_PEARL || !this.on(Feature.PEARL_ANTI_GLITCH)) {
         return;
      }

      Player player = event.getPlayer();
      Location from = event.getFrom();
      Location to = event.getTo();
      World world = to == null ? null : to.getWorld();
      if (world == null || from.getWorld() != world) {
         this.blocked(event, player);
         return;
      }

      BoundingBox box = player.getBoundingBox();
      double width = box.getWidthX();
      double height = box.getHeight();
      double half = width / 2.0;
      if (!world.hasCollisionsIn(new BoundingBox(to.getX() - half, to.getY(), to.getZ() - half, to.getX() + half, to.getY() + height, to.getZ() + half))) {
         return;
      }

      double[] spot = PearlLanding.nearestClear(
         to.getX(), to.getY(), to.getZ(), from.getX(), from.getY(), from.getZ(), width, height,
         (minX, minY, minZ, maxX, maxY, maxZ) -> world.hasCollisionsIn(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)),
         (x1, y1, z1, x2, y2, z2) -> blockedAt(world, x1, y1, z1, x2, y2, z2)
      );
      if (spot == null || !world.getWorldBorder().isInside(new Location(world, spot[0], spot[1], spot[2]))) {
         this.blocked(event, player);
      } else {
         event.setTo(new Location(world, spot[0], spot[1], spot[2], to.getYaw(), to.getPitch()));
      }
   }

   private static double blockedAt(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
      Vector path = new Vector(x2 - x1, y2 - y1, z2 - z1);
      double length = path.length();
      if (length < 1.0E-6) {
         return -1.0;
      }

      RayTraceResult hit = world.rayTraceBlocks(new Location(world, x1, y1, z1), path.multiply(1.0 / length), length, FluidCollisionMode.NEVER, true);
      return hit == null ? -1.0 : hit.getHitPosition().distance(new Vector(x1, y1, z1));
   }

   /** The pearl would have glitched: no teleport, and the pearl back in the player's inventory. */
   private void blocked(PlayerTeleportEvent event, Player player) {
      event.setCancelled(true);
      if (this.settings().tuning().pearlRefund() && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) {
         for (ItemStack left : player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL)).values()) {
            Item dropped = player.getWorld().dropItem(player.getLocation(), left);
            dropped.setPickupDelay(0);
         }
      }

      this.plugin.actionBar(player, "pearl-blocked");
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPearlHit(ProjectileHitEvent event) {
      if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player) {
         if (this.on(Feature.SMOOTH_PEARLS)) {
            Location at = pearl.getLocation();
            PearlLanding.Face face = event.getHitBlock() != null ? PearlLanding.Face.of(event.getHitBlockFace()) : PearlLanding.Face.NONE;
            this.hits.put(player.getUniqueId(), new Hit(Bukkit.getCurrentTick(), at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), face));
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
   public void onPearlTeleport(PlayerTeleportEvent event) {
      if (event.getCause() == TeleportCause.ENDER_PEARL) {
         Player player = event.getPlayer();
         Hit hit = this.hits.remove(player.getUniqueId());
         if (hit != null && this.on(Feature.SMOOTH_PEARLS) && Bukkit.getCurrentTick() - hit.tick() <= HIT_TTL_TICKS) {
            Location to = event.getTo();
            World world = to.getWorld();
            if (world != null && world.getUID().equals(hit.world())) {
               BoundingBox box = player.getBoundingBox();
               double[] feet = PearlLanding.find(
                  hit.x(),
                  hit.y(),
                  hit.z(),
                  hit.face(),
                  box.getWidthX(),
                  box.getHeight(),
                  (minX, minY, minZ, maxX, maxY, maxZ) -> world.hasCollisionsIn(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ))
               );
               if (feet != null) {
                  Location landing = new Location(world, feet[0], feet[1], feet[2], to.getYaw(), to.getPitch());
                  if (world.getWorldBorder().isInside(landing) && !(landing.distanceSquared(to) > MAX_SHIFT_SQUARED)) {
                     event.setTo(landing);
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPearlLanded(PlayerTeleportEvent event) {
      if (event.getCause() == TeleportCause.ENDER_PEARL) {
         this.landed.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
      }
   }

   /**
    * Pearl landing damage is its own damage type from 1.21.2 on, and plain fall damage before that; both arrive in the
    * same tick as the pearl teleport, so the teleport is what identifies it.
    */
   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPearlDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player && this.on(Feature.NO_PEARL_DAMAGE)) {
         Integer tick = this.landed.get(player.getUniqueId());
         if (tick != null && Bukkit.getCurrentTick() - tick <= 1 && (event.getCause() == DamageCause.FALL || pearlDamageType(event))) {
            event.setCancelled(true);
         }
      }
   }

   private static boolean pearlDamageType(EntityDamageEvent event) {
      try {
         return "ender_pearl".equals(event.getDamageSource().getDamageType().getKey().getKey());
      } catch (RuntimeException | LinkageError e) {
         return false;
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onThrow(PlayerLaunchProjectileEvent event) {
      if (event.getProjectile() instanceof EnderPearl && this.on(Feature.PEARL_COOLDOWN)) {
         Player player = event.getPlayer();
         int ticks = this.settings().ticks(Feature.PEARL_COOLDOWN);
         // Vanilla puts its own 20 tick cooldown on after this event, so ours goes on a tick later.
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
               player.setCooldown(Material.ENDER_PEARL, Math.max(0, ticks - 1));
            }
         });
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.hits.remove(event.getPlayer().getUniqueId());
      this.landed.remove(event.getPlayer().getUniqueId());
   }

   int trackedPearls() {
      return this.pearls.size();
   }

   private record Hit(int tick, UUID world, double x, double y, double z, PearlLanding.Face face) {
   }
}
