package com.pvpcore.module;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Integrations;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.util.Attributes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Server switches (attribute swapping, Leaf's instant knockback, Paper's shield-stun tick), hit delay and sweeps.
 * <p>
 * Attribute swapping is vanilla behaviour: a hit made in the same tick as an item swap still uses the attack damage
 * and speed of the item swapped from. Paper changes that and has a switch to put it back; where that switch is
 * missing, the same is done here: the swap's old values are noted, and a hit in that tick gets them for its own
 * moment only (removed again on the next tick). No separate plugin is needed.
 */
public final class CombatModule extends Module {
   static final int VANILLA_HIT_DELAY = 20;
   private final Set<UUID> changed = ConcurrentHashMap.newKeySet();
   /** Attack values from just before each player's latest swap, with the tick it happened in. */
   private final Map<UUID, Swap> swaps = new HashMap<>();
   /** Modifiers holding a swap's old values for the hit made in that tick. */
   private final Map<UUID, List<Held>> held = new HashMap<>();
   private final NamespacedKey damageKey;
   private final NamespacedKey speedKey;
   private BukkitTask watchdog;

   public CombatModule(PvPCore plugin) {
      super(plugin);
      this.damageKey = new NamespacedKey(plugin, "swap_damage");
      this.speedKey = new NamespacedKey(plugin, "swap_speed");
   }

   /** Registered only where Paper's pre-attack event exists: the moment a hit is decided, before damage is worked out. */
   public Listener swapListener() {
      return new SwapAttack();
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

         this.release(player);
      }

      this.swaps.clear();
   }

   // ------------------------------------------------------------------ attribute swapping, built in

   /** True when PvPCore itself keeps swap damage (no Paper switch here, and no other plugin doing it). */
   public boolean emulatingSwaps() {
      return this.on(Feature.ATTRIBUTE_SWAPPING) && Compat.PRE_ATTACK_EVENT && !this.plugin.tweaks().attributeSwappingAvailable()
         && !Integrations.attributeSwapPlugin();
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHotbarSwap(PlayerItemHeldEvent event) {
      this.remember(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHandSwap(PlayerSwapHandItemsEvent event) {
      this.remember(event.getPlayer());
   }

   /** Runs before the swap takes effect, so the values read here are the old item's. */
   private void remember(Player player) {
      if (!this.emulatingSwaps()) {
         return;
      }

      int now = Bukkit.getCurrentTick();
      Swap last = this.swaps.get(player.getUniqueId());
      if (last != null && last.tick() == now) {
         return; // several swaps in one tick: the hit uses what was held before the first
      }

      Attribute damage = Attributes.attackDamage();
      Attribute speed = Attributes.attackSpeed();
      if (damage != null && speed != null) {
         this.swaps.put(player.getUniqueId(), new Swap(now, Attributes.value(player, damage, 0.0), Attributes.value(player, speed, 4.0)));
      }
   }

   void restoreForHit(Player player) {
      Swap swap = this.swaps.remove(player.getUniqueId());
      if (swap == null || swap.tick() != Bukkit.getCurrentTick() || !this.emulatingSwaps()) {
         return;
      }

      this.release(player); // never stack on a hold that wasn't lifted yet
      List<Held> added = new ArrayList<>(2);
      this.hold(player, Attributes.attackDamage(), this.damageKey, swap.damage(), added);
      this.hold(player, Attributes.attackSpeed(), this.speedKey, swap.speed(), added);
      if (added.isEmpty()) {
         return;
      }

      this.held.put(player.getUniqueId(), added);
      Bukkit.getScheduler().runTask(this.plugin, () -> this.release(player));
   }

   private void hold(Player player, Attribute attribute, NamespacedKey key, double wanted, List<Held> added) {
      AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
      if (instance == null) {
         return;
      }

      double delta = wanted - instance.getValue();
      if (Math.abs(delta) < 1.0E-9) {
         return;
      }

      AttributeModifier modifier = new AttributeModifier(key, delta, AttributeModifier.Operation.ADD_NUMBER);
      instance.addTransientModifier(modifier);
      added.add(new Held(instance, modifier));
   }

   private void release(Player player) {
      List<Held> mods = this.held.remove(player.getUniqueId());
      if (mods != null) {
         for (Held mod : mods) {
            mod.instance().removeModifier(mod.modifier());
         }
      }
   }

   record Swap(int tick, double damage, double speed) {
   }

   record Held(AttributeInstance instance, AttributeModifier modifier) {
   }

   private final class SwapAttack implements Listener {
      @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
      public void onAttack(PrePlayerAttackEntityEvent event) {
         if (event.willAttack()) {
            CombatModule.this.restoreForHit(event.getPlayer());
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

      this.release(player);
      this.swaps.remove(player.getUniqueId());
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
