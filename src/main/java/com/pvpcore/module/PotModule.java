package com.pvpcore.module;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.util.Vector;

public final class PotModule extends Module {
   static final float LOOK_DOWN_DEGREES = 45.0F;

   public PotModule(PvPCore plugin) {
      super(plugin);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onThrow(PlayerLaunchProjectileEvent event) {
      // Checked by item: before 1.21.5 every thrown potion entity is also a SplashPotion, lingering ones included.
      if (event.getProjectile() instanceof ThrownPotion potion && potion.getItem().getType() == Material.SPLASH_POTION && this.on(Feature.FAST_POTS)) {
         Player player = event.getPlayer();
         if (!(player.getLocation().getPitch() < LOOK_DOWN_DEGREES)) {
            Vector velocity = potion.getVelocity();
            potion.setVelocity(velocity.setY(velocity.getY() - this.settings().value(Feature.FAST_POTS)));
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onSplash(PotionSplashEvent event) {
      if (this.on(Feature.SELF_POT_FIX)) {
         ThrownPotion potion = event.getPotion();
         if (potion.getShooter() instanceof Player thrower && event.getAffectedEntities().contains(thrower)) {
            if (potion.getEffects().isEmpty()) {
               return;
            }

            for (PotionEffect effect : potion.getEffects()) {
               if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                  return;
               }
            }

            event.setIntensity(thrower, 1.0);
         }
      }
   }
}
