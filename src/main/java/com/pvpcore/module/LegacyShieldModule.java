package com.pvpcore.module;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.util.Geometry;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Shields on 1.21 to 1.21.4, before shields became data-driven. Same switches as {@link ShieldModule}: the delay goes
 * through Paper's per-player shield delay, and stuns are vanilla axe disables with the duration, the raise-delay
 * stun, the follow-up hit and the break sound handled here.
 */
public final class LegacyShieldModule extends Module {
   static final int VANILLA_DELAY_TICKS = 5;
   static final int VANILLA_STUN_TICKS = 100;
   private final Map<UUID, Integer> heldImmunity = new ConcurrentHashMap<>();

   public LegacyShieldModule(PvPCore plugin) {
      super(plugin);
   }

   /** Registered only when Paper's shield disable event exists (1.21.4). */
   public Listener disableListener() {
      return new DisableListener();
   }

   @Override
   public void apply() {
      this.plugin.tweaks().shieldStun(this.on(Feature.SHIELD_STUN));
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.applyDelay(player);
      }
   }

   @Override
   public void stop() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         player.setShieldBlockingDelay(VANILLA_DELAY_TICKS);
      }

      for (Map.Entry<UUID, Integer> held : this.heldImmunity.entrySet()) {
         Player player = Bukkit.getPlayer(held.getKey());
         if (player != null && player.getMaximumNoDamageTicks() == 0) {
            player.setMaximumNoDamageTicks(held.getValue());
         }
      }

      this.heldImmunity.clear();
   }

   private int delayTicks() {
      if (!this.on(Feature.SHIELD_DELAY)) {
         return VANILLA_DELAY_TICKS;
      }

      return (int)Math.round(this.settings().value(Feature.SHIELD_DELAY) / 50.0);
   }

   private int stunTicks() {
      return Math.max(1, (int)Math.round(this.settings().value(Feature.SHIELD_STUN) * 20.0));
   }

   private void applyDelay(Player player) {
      int wanted = this.delayTicks();
      if (player.getShieldBlockingDelay() != wanted) {
         player.setShieldBlockingDelay(wanted);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.applyDelay(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      Player player = event.getPlayer();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline()) {
            this.applyDelay(player);
         }
      });
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onWorldChange(PlayerChangedWorldEvent event) {
      this.applyDelay(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Integer held = this.heldImmunity.remove(event.getPlayer().getUniqueId());
      if (held != null && event.getPlayer().getMaximumNoDamageTicks() == 0) {
         event.getPlayer().setMaximumNoDamageTicks(held);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onRaise(PlayerInteractEvent event) {
      if (event.getAction().isRightClick() && event.getHand() != null && isShield(event.getItem()) && !this.on(Feature.SHIELD_USAGE)) {
         event.setUseItemInHand(Result.DENY);
      }
   }

   static boolean isShield(ItemStack item) {
      return item != null && item.getType() == Material.SHIELD;
   }

   static boolean isAxe(ItemStack item) {
      return item != null && Tag.ITEMS_AXES.isTagged(item.getType());
   }

   /**
    * An axe on a shield that is raised but still inside its raise delay: vanilla lets the hit through as damage.
    * Here it stuns, the same as the data-driven version does.
    */
   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onAxeHit(EntityDamageByEntityEvent event) {
      if (this.on(Feature.SHIELD_STUN)
         && this.on(Feature.SHIELD_USAGE)
         && event.getCause() == DamageCause.ENTITY_ATTACK
         && event.getEntity() instanceof Player victim
         && event.getDamager() instanceof Player attacker
         && !attacker.equals(victim)
         && victim.isHandRaised()
         && !victim.isBlocking()
         && isShield(victim.getActiveItem())
         && !victim.hasCooldown(Material.SHIELD)
         && isAxe(attacker.getInventory().getItemInMainHand())
         && Geometry.facing(victim, attacker.getLocation(), 90.0F)) {
         event.setCancelled(true);
         this.stun(victim, attacker, this.stunTicks());
      }
   }

   /** Vanilla disabled a blocking shield. Without Paper's event (1.21 - 1.21.3) the duration is set a tick later. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockedHit(EntityDamageByEntityEvent event) {
      if (Compat.SHIELD_DISABLE_EVENT
         || event.getCause() != DamageCause.ENTITY_ATTACK
         || !(event.getEntity() instanceof Player victim)
         || !(event.getDamager() instanceof Player attacker)
         || !victim.isBlocking()
         || !isAxe(attacker.getInventory().getItemInMainHand())) {
         return;
      }

      boolean stunOn = this.on(Feature.SHIELD_STUN);
      int ticks = this.stunTicks();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (victim.isOnline() && victim.hasCooldown(Material.SHIELD)) {
            victim.setCooldown(Material.SHIELD, stunOn ? Math.max(0, ticks - 1) : 0);
         }
      });
      if (stunOn) {
         this.afterStun(victim, attacker);
      }
   }

   private void stun(Player victim, Player attacker, int ticks) {
      victim.setCooldown(Material.SHIELD, ticks);
      victim.clearActiveItem();
      this.afterStun(victim, attacker);
   }

   private void afterStun(Player victim, Player attacker) {
      if (!this.plugin.tweaks().shieldStunActive()) {
         // No Paper switch to skip the blocked hit's damage tick: hold the follow-up window open for this tick.
         victim.setNoDamageTicks(0);
         int max = victim.getMaximumNoDamageTicks();
         if (max != 0) {
            this.heldImmunity.putIfAbsent(victim.getUniqueId(), max);
            victim.setMaximumNoDamageTicks(0);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               Integer held = this.heldImmunity.remove(victim.getUniqueId());
               if (held != null && victim.getMaximumNoDamageTicks() == 0) {
                  victim.setMaximumNoDamageTicks(held);
               }
            });
         }
      }

      if (this.on(Feature.BREAK_SOUND)) {
         float pitch = 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F;
         victim.playSound(victim, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
         if (!attacker.equals(victim)) {
            attacker.playSound(attacker, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
         }
      }
   }

   private final class DisableListener implements Listener {
      @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
      public void onStun(PlayerShieldDisableEvent event) {
         if (!LegacyShieldModule.this.on(Feature.SHIELD_STUN)) {
            event.setCancelled(true);
            return;
         }

         double seconds = LegacyShieldModule.this.settings().value(Feature.SHIELD_STUN);
         int ticks = (int)Math.round(event.getCooldown() * seconds / 5.0);
         event.setCooldown(Math.max(1, ticks));
      }

      @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
      public void onStunned(PlayerShieldDisableEvent event) {
         if (event.getDamager() instanceof Player attacker) {
            LegacyShieldModule.this.afterStun(event.getPlayer(), attacker);
         }
      }
   }
}
