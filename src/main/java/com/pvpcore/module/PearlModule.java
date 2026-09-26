package com.pvpcore.module;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.BoundingBox;

public final class PearlModule extends Module {
   private static final double MAX_SHIFT_SQUARED = 25.0;
   private static final int HIT_TTL_TICKS = 2;
   private final Map<UUID, Hit> hits = new ConcurrentHashMap<>();
   /** Tick of each player's last pearl landing: the landing damage follows in the same tick. */
   private final Map<UUID, Integer> landed = new ConcurrentHashMap<>();

   public PearlModule(PvPCore plugin) {
      super(plugin);
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

   private record Hit(int tick, UUID world, double x, double y, double z, PearlLanding.Face face) {
   }
}
