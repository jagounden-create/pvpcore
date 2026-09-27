package com.pvpcore.module;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.util.Text;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Mace balance and fairness. Damage changes only apply to hits on players, so mob farms and bosses stay vanilla.
 */
public final class MaceModule extends Module {
   /** Vanilla's smash threshold: a mace hit smashes after falling more than this. */
   static final double SMASH_MIN_FALL = 1.5;
   /** How long after a smash attempt its fall is still covered. */
   static final int ATTEMPT_WINDOW_TICKS = 40;
   /** Falling this far after the attempt would not have hurt anyway (vanilla's safe fall distance, plus slack). */
   static final double SAFE_EXTRA_FALL = 3.5;
   private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> smashReady = new ConcurrentHashMap<>();
   private final Set<UUID> glided = ConcurrentHashMap.newKeySet();
   private final Enchantment density = density();

   public MaceModule(PvPCore plugin) {
      super(plugin);
   }

   /** Registered only when Paper's pre-attack event exists; it also sees hits that hit immunity swallows. */
   public Listener preAttackListener() {
      return new PreAttack();
   }

   @Override
   public void apply() {
      if (!this.on(Feature.SMASH_PROTECTION)) {
         this.attempts.clear();
      }

      if (!this.on(Feature.SMASH_COOLDOWN)) {
         this.smashReady.clear();
      }

      if (!this.on(Feature.NO_ELYTRA_SMASH)) {
         this.glided.clear();
      }
   }

   private static Enchantment density() {
      try {
         return Enchantment.DENSITY;
      } catch (LinkageError | RuntimeException e) {
         return null;
      }
   }

   static boolean isMace(ItemStack item) {
      return Compat.MACE && item != null && item.getType() == Material.MACE;
   }

   static boolean canSmash(Player player) {
      return player.getFallDistance() > SMASH_MIN_FALL && !player.isGliding();
   }

   /** The bonus vanilla adds to a smash: 4 per block for the first 3, 2 per block for the next 5, then 1, plus Density. */
   static double smashBonus(double fall, int densityLevel) {
      if (fall <= SMASH_MIN_FALL) {
         return 0.0;
      }

      double bonus;
      if (fall <= 3.0) {
         bonus = 4.0 * fall;
      } else if (fall <= 8.0) {
         bonus = 12.0 + 2.0 * (fall - 3.0);
      } else {
         bonus = 22.0 + fall - 8.0;
      }

      return bonus + 0.5 * Math.max(0, densityLevel) * fall;
   }

   /**
    * Whether a fall that ends now is the same fall a smash attempt was made in, and short enough to forgive: the fall
    * distance kept growing from the attempt, and grew by as much as the player actually dropped since.
    */
   static boolean covers(double fallAtAttempt, double yAtAttempt, double fallNow, double yNow, int ticksSince) {
      if (ticksSince < 0 || ticksSince > ATTEMPT_WINDOW_TICKS) {
         return false;
      }

      double extra = fallNow - fallAtAttempt;
      double dropped = yAtAttempt - yNow;
      return extra >= -0.01 && extra <= SAFE_EXTRA_FALL && Math.abs(extra - dropped) <= 1.0;
   }

   private int densityLevel(ItemStack weapon) {
      return this.density == null ? 0 : weapon.getEnchantmentLevel(this.density);
   }

   void attempt(Player attacker) {
      if (this.on(Feature.SMASH_PROTECTION) && isMace(attacker.getInventory().getItemInMainHand()) && canSmash(attacker)) {
         this.attempts.put(attacker.getUniqueId(), new Attempt(Bukkit.getCurrentTick(), attacker.getFallDistance(), attacker.getLocation().getY()));
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onMaceHit(EntityDamageByEntityEvent event) {
      if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player attacker)) {
         return;
      }

      ItemStack weapon = attacker.getInventory().getItemInMainHand();
      if (!isMace(weapon)) {
         return;
      }

      if (!Compat.PRE_ATTACK_EVENT) {
         this.attempt(attacker);
      }

      if (!(event.getEntity() instanceof Player)) {
         return;
      }

      double damage = event.getDamage();
      boolean changed = false;
      if (canSmash(attacker)) {
         double bonus = Math.min(damage, smashBonus(attacker.getFallDistance(), this.densityLevel(weapon)));
         if (bonus > 0.0 && this.on(Feature.NO_ELYTRA_SMASH) && this.glided.contains(attacker.getUniqueId())) {
            damage -= bonus;
            bonus = 0.0;
            changed = true;
         }

         if (bonus > 0.0 && this.on(Feature.SMASH_COOLDOWN)) {
            int now = Bukkit.getCurrentTick();
            Integer ready = this.smashReady.get(attacker.getUniqueId());
            if (ready != null && now < ready) {
               damage -= bonus;
               bonus = 0.0;
               changed = true;
               this.plugin.actionBar(attacker, "smash-cooldown", Map.of("seconds", Text.trim(Math.ceil((ready - now) / 2.0) / 10.0)));
            } else {
               // Told by message, not an item cooldown: an item cooldown could stop the mace hitting at all.
               this.smashReady.put(attacker.getUniqueId(), now + (int)Math.round(this.settings().value(Feature.SMASH_COOLDOWN) * 20.0));
            }
         }

         if (bonus > 0.0 && this.on(Feature.SMASH_DAMAGE)) {
            damage += bonus * (this.settings().value(Feature.SMASH_DAMAGE) - 1.0);
            changed = true;
         }
      }

      if (this.on(Feature.SMASH_CAP)) {
         double cap = this.settings().value(Feature.SMASH_CAP) * 2.0;
         if (damage > cap) {
            damage = cap;
            changed = true;
         }
      }

      if (changed) {
         event.setDamage(Math.max(0.0, damage));
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onFall(EntityDamageEvent event) {
      if (event.getCause() == DamageCause.FALL && event.getEntity() instanceof Player player && this.on(Feature.SMASH_PROTECTION)) {
         Attempt attempt = this.attempts.remove(player.getUniqueId());
         if (attempt != null && covers(attempt.fall(), attempt.y(), player.getFallDistance(), player.getLocation().getY(), Bukkit.getCurrentTick() - attempt.tick())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onWindCharge(ProjectileLaunchEvent event) {
      if (Compat.WIND_CHARGE && this.on(Feature.WIND_CHARGE_COOLDOWN) && event.getEntity() instanceof WindCharge && event.getEntity().getShooter() instanceof Player player) {
         int ticks = this.settings().ticks(Feature.WIND_CHARGE_COOLDOWN);
         // Vanilla sets its own 10 tick cooldown right after this event.
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
               player.setCooldown(Material.WIND_CHARGE, Math.max(0, ticks - 1));
            }
         });
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onGlide(EntityToggleGlideEvent event) {
      if (event.isGliding() && event.getEntity() instanceof Player player && this.on(Feature.NO_ELYTRA_SMASH)) {
         this.glided.add(player.getUniqueId());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      if (!this.glided.isEmpty()) {
         Player player = event.getPlayer();
         // Landed: the next fall is a clean one.
         if (!player.isGliding() && ((LivingEntity)player).isOnGround()) {
            this.glided.remove(player.getUniqueId());
         }
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      this.forget(event.getEntity().getUniqueId());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.forget(event.getPlayer().getUniqueId());
      this.smashReady.remove(event.getPlayer().getUniqueId());
   }

   private void forget(UUID id) {
      this.attempts.remove(id);
      this.glided.remove(id);
   }

   record Attempt(int tick, double fall, double y) {
   }

   private final class PreAttack implements Listener {
      @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
      public void onAttack(PrePlayerAttackEntityEvent event) {
         if (event.willAttack() && event.getAttacked() instanceof LivingEntity) {
            MaceModule.this.attempt(event.getPlayer());
         }
      }
   }
}
