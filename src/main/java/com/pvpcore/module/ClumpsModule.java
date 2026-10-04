package com.pvpcore.module;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent.ChangeReason;

public final class ClumpsModule extends Module {
   public ClumpsModule(PvPCore plugin) {
      super(plugin);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPickupDelay(PlayerExpCooldownChangeEvent event) {
      if (event.getReason() == ChangeReason.PICKUP_ORB && this.on(Feature.XP_CLUMPS)) {
         event.setNewCooldown(0);
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPickup(PlayerPickupExperienceEvent event) {
      if (this.on(Feature.XP_CLUMPS)) {
         collapse(event.getExperienceOrb());
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onSpawn(EntitySpawnEvent event) {
      if (event.getEntity() instanceof ExperienceOrb orb && this.on(Feature.XP_CLUMPS)) {
         double radius = this.settings().value(Feature.XP_CLUMPS);
         if (!(radius <= 0.0)) {
            for (ExperienceOrb existing : orb.getWorld().getNearbyEntitiesByType(ExperienceOrb.class, orb.getLocation(), radius)) {
               if (existing != orb && existing.isValid()) {
                  collapse(existing);
                  existing.setExperience(add(existing.getExperience(), total(orb)));
                  event.setCancelled(true);
                  return;
               }
            }
         }
      }
   }

   static void collapse(ExperienceOrb orb) {
      if (orb.getCount() > 1) {
         int total = total(orb);
         orb.setCount(1);
         orb.setExperience(total);
      }
   }

   static int total(ExperienceOrb orb) {
      long total = (long)orb.getExperience() * Math.max(1, orb.getCount());
      return (int)Math.min(2147483647L, total);
   }

   static int add(int a, int b) {
      return (int)Math.min(2147483647L, (long)a + b);
   }
}
