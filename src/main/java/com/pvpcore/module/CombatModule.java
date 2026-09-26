package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Integrations;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

/** Server switches (attribute swapping, Leaf's instant knockback, Paper's shield-stun tick), hit delay and sweeps. */
public final class CombatModule extends Module {
   static final int VANILLA_HIT_DELAY = 20;
   private final Set<UUID> changed = ConcurrentHashMap.newKeySet();
   private BukkitTask watchdog;

   public CombatModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      // /paper reload re-reads paper-global.yml and would silently undo the switches, so re-push them now and then.
      this.watchdog = Bukkit.getScheduler().runTaskTimer(this.plugin, this::pushServerSwitches, 100L, 100L);
   }

   @Override
   public void apply() {
      this.pushServerSwitches();

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.applyHitDelay(player);
      }
   }

   @Override
   public void stop() {
      if (this.watchdog != null) {
         this.watchdog.cancel();
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.changed.remove(player.getUniqueId())) {
            player.setMaximumNoDamageTicks(VANILLA_HIT_DELAY);
         }
      }
   }

   private void pushServerSwitches() {
      this.plugin.tweaks().attributeSwapping(this.on(Feature.ATTRIBUTE_SWAPPING) && !Integrations.attributeSwapPlugin());
      this.plugin.tweaks().instantKnockback(this.on(Feature.INSTANT_KNOCKBACK));
      this.plugin.tweaks().shieldStun(this.on(Feature.SHIELD_STUN));
   }

   private void applyHitDelay(Player player) {
      if (this.on(Feature.HIT_DELAY)) {
         player.setMaximumNoDamageTicks(this.settings().ticks(Feature.HIT_DELAY));
         this.changed.add(player.getUniqueId());
      } else if (this.changed.remove(player.getUniqueId())) {
         player.setMaximumNoDamageTicks(VANILLA_HIT_DELAY);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.applyHitDelay(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline()) {
            this.applyHitDelay(player);
         }
      });
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onWorldChange(PlayerChangedWorldEvent event) {
      this.applyHitDelay(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      if (this.changed.remove(player.getUniqueId())) {
         player.setMaximumNoDamageTicks(VANILLA_HIT_DELAY);
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onSweep(EntityDamageByEntityEvent event) {
      if (event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK
         && this.on(Feature.NO_SWEEP)
         && event.getEntity() instanceof Player
         && event.getDamager() instanceof Player) {
         event.setCancelled(true);
      }
   }
}
