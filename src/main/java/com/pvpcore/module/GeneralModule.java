package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/** Arrow cleanup and instant respawn. */
public final class GeneralModule extends Module {
   public GeneralModule(PvPCore plugin) {
      super(plugin);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onArrowLand(ProjectileHitEvent event) {
      // Tridents are the thrower's item, never cleaned up.
      if (event.getHitBlock() != null && event.getEntity() instanceof AbstractArrow arrow && !(arrow instanceof Trident) && this.on(Feature.ARROW_CLEANUP)) {
         long delay = Math.max(1L, Math.round(this.settings().value(Feature.ARROW_CLEANUP) * 20.0));
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (arrow.isValid() && arrow.isInBlock()) {
               arrow.remove();
            }
         }, delay);
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
}
