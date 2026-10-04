package com.pvpcore.module;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import com.pvpcore.Feature;
import com.pvpcore.Integrations;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import io.papermc.paper.event.entity.EntityKnockbackEvent.Cause;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Knockback distance and vertical knockback. Only registered when Paper's knockback event with causes exists.
 */
public final class KnockbackModule extends Module {
   static final double VANILLA_LIFT = 0.4;
   static final double STANDING = 0.1;
   static final double GROUND_REACH = 1.0;
   static final double GRAVITY = 0.08;
   static final double DRAG = 0.98;
   static final int MAX_TICKS = 8;
   private static final double[][] FEET_OFFSETS = new double[][]{{0.0, 0.0}, {0.29, 0.29}, {0.29, -0.29}, {-0.29, 0.29}, {-0.29, -0.29}};
   private static final Vector DOWN = new Vector(0, -1, 0);
   private final Map<UUID, double[]> motion = new ConcurrentHashMap<>();

   public KnockbackModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void apply() {
      if (!this.on(Feature.VERTICAL_KNOCKBACK)) {
         this.motion.clear();
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      if (this.on(Feature.VERTICAL_KNOCKBACK)) {
         double from = event.getFrom().getY();
         double to = event.getTo().getY();
         // Head-only turns come through here too; they say nothing about falling speed.
         if (from == to && event.getFrom().getX() == event.getTo().getX() && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
         }

         double[] seen = this.motion.get(event.getPlayer().getUniqueId());
         if (seen == null) {
            this.motion.put(event.getPlayer().getUniqueId(), new double[]{to, to - from});
         } else {
            seen[0] = to;
            seen[1] = to - from;
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.motion.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onKnockback(EntityKnockbackByEntityEvent event) {
      Cause cause = event.getCause();
      if (cause == Cause.SWEEP_ATTACK && this.on(Feature.NO_SWEEP) && event.getEntity() instanceof Player && event.getHitBy() instanceof Player) {
         event.setCancelled(true);
         return;
      }

      boolean lift = this.on(Feature.VERTICAL_KNOCKBACK) && !Integrations.knockbackSync();
      boolean push = this.on(Feature.KNOCKBACK_DISTANCE);
      if (lift || push) {
         if (cause == Cause.DAMAGE || cause == Cause.ENTITY_ATTACK || cause == Cause.SWEEP_ATTACK) {
            if (event.getEntity() instanceof Player victim && event.getHitBy() instanceof Player) {
               if (!victim.isFlying() && !victim.isGliding() && !victim.isInWater() && !victim.isInsideVehicle() && !victim.isRiptiding()) {
                  double strength = event.getKnockbackStrength();
                  if (!(strength <= 0.0)) {
                     Vector before = victim.getVelocity();
                     if (push) {
                        double factor = this.settings().value(Feature.KNOCKBACK_DISTANCE);
                        Vector knockback = event.getKnockback();
                        event.setKnockback(
                           knockback.setX((before.getX() + knockback.getX()) * factor - before.getX())
                              .setZ((before.getZ() + knockback.getZ()) * factor - before.getZ())
                        );
                     }

                     if (lift) {
                        if (victim.isOnGround() || this.nearGround(victim)) {
                           double cap = this.settings().value(Feature.VERTICAL_KNOCKBACK);
                           Vector knockback = event.getKnockback();
                           double wanted = liftFor(before.getY(), strength, cap);
                           if (!(wanted - before.getY() <= knockback.getY() + 1.0E-6)) {
                              event.setKnockback(knockback.setY(wanted - before.getY()));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   /** Vanilla gives min(0.4, vy / 2 + strength) on the ground; this scales that curve so its ceiling is {@code cap}. */
   static double liftFor(double currentVy, double strength, double cap) {
      return Math.min(cap, (currentVy / 2.0 + strength) * (cap / VANILLA_LIFT));
   }

   private boolean nearGround(Player victim) {
      Location feet = victim.getLocation();
      double ground = groundDistance(feet, GROUND_REACH);
      if (ground <= GROUND_REACH) {
         return true;
      }

      double[] seen = this.motion.get(victim.getUniqueId());
      double vy = seen != null && !(Math.abs(seen[0] - feet.getY()) > 0.001) ? seen[1] : 0.0;
      int ticks = Math.min(MAX_TICKS, 1 + (int)Math.ceil(Math.max(0, victim.getPing()) / 50.0));
      return landsWithin(ground, vy, ticks);
   }

   static boolean landsWithin(double ground, double vy, int ticks) {
      if (ground <= STANDING) {
         return true;
      }

      if (Double.isInfinite(ground)) {
         return false;
      }

      double drop = 0.0;

      for (int tick = 0; tick < ticks; tick++) {
         vy = (vy - GRAVITY) * DRAG;
         drop -= vy;
         if (drop >= ground - 0.001) {
            return true;
         }
      }

      return false;
   }

   /** Distance from the feet down to the ground, checked under the middle and the four corners. */
   private static double groundDistance(Location feet, double enough) {
      World world = feet.getWorld();
      if (world == null) {
         return Double.POSITIVE_INFINITY;
      }

      double best = Double.POSITIVE_INFINITY;

      for (double[] offset : FEET_OFFSETS) {
         Location start = feet.clone().add(offset[0], 0.05, offset[1]);
         RayTraceResult hit = world.rayTraceBlocks(start, DOWN, 3.05, FluidCollisionMode.NEVER, true);
         if (hit != null) {
            best = Math.min(best, start.getY() - hit.getHitPosition().getY() - 0.05);
            if (best <= enough) {
               break; // close enough to count as on the ground; the other corners can't change that
            }
         }
      }

      return Math.max(0.0, best);
   }
}
