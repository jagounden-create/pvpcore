package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import java.util.ArrayDeque;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitTask;

/** Arrow cleanup and instant respawn. */
public final class GeneralModule extends Module {
   /** How often landed arrows are checked, in ticks. */
   static final long CLEANUP_PERIOD = 10L;
   /** Never hold more than this many landed arrows; the oldest go first. */
   static final int MAX_LANDED = 20000;
   /** Landed arrows in the order they landed, so the due ones are always at the front. */
   private final ArrayDeque<Landed> landed = new ArrayDeque<>();
   private BukkitTask cleanup;

   public GeneralModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      this.cleanup = Bukkit.getScheduler().runTaskTimer(this.plugin, this::cleanUp, CLEANUP_PERIOD, CLEANUP_PERIOD);
   }

   @Override
   public void apply() {
      if (!this.on(Feature.ARROW_CLEANUP)) {
         this.landed.clear();
      }
   }

   @Override
   public void stop() {
      if (this.cleanup != null) {
         this.cleanup.cancel();
      }

      this.landed.clear();
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onArrowLand(ProjectileHitEvent event) {
      // Tridents are the thrower's item, never cleaned up.
      if (event.getHitBlock() != null && event.getEntity() instanceof AbstractArrow arrow && !(arrow instanceof Trident) && this.on(Feature.ARROW_CLEANUP)) {
         if (this.landed.size() >= MAX_LANDED) {
            this.landed.pollFirst();
         }

         long delay = Math.max(1L, Math.round(this.settings().value(Feature.ARROW_CLEANUP) * 20.0));
         this.landed.addLast(new Landed(arrow, Bukkit.getCurrentTick() + delay));
      }
   }

   /** One timer for every landed arrow instead of a task each. */
   private void cleanUp() {
      long now = Bukkit.getCurrentTick();
      while (!this.landed.isEmpty() && this.landed.peekFirst().due() <= now) {
         AbstractArrow arrow = this.landed.pollFirst().arrow();
         if (arrow.isValid() && arrow.isInBlock()) {
            arrow.remove();
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      if (this.on(Feature.INSTANT_RESPAWN)) {
         Player player = event.getEntity();
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline() && player.isDead()) {
               player.spigot().respawn();
            }
         }, 1L);
      }
   }

   private record Landed(AbstractArrow arrow, long due) {
   }
}
