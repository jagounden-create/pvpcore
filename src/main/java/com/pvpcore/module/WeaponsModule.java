package com.pvpcore.module;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.util.Attributes;
import com.pvpcore.util.Bedrock;
import com.pvpcore.util.Hits;
import com.pvpcore.util.Text;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Spears (Lunge cooldown, charge damage) and the Bedrock damage buff.
 * <p>
 * Lunge (1.21.11+) is an enchantment effect on the spear's jab: it charges hunger through Paper's exhaustion event
 * (reason ENCHANTMENT_EFFECT) and pushes the player forward. Neither client nor server checks item cooldowns for jabs,
 * so a lunge during the cooldown is undone instead: no hunger is taken and the forward push is cancelled.
 * <p>
 * A spear charge is the held attack: the spear is raised and runs into people, dealing damage that grows with speed.
 * Riding an elytra or a horse into someone makes it enormous, so it can be scaled, capped, or held to a normal hit's
 * damage while gliding. Hits on players only.
 */
public final class WeaponsModule extends Module {
   private final Map<UUID, Integer> lungeReady = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> suppressed = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();
   private final Enchantment lunge = lunge();

   public WeaponsModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void apply() {
      if (!this.on(Feature.LUNGE_COOLDOWN)) {
         this.lungeReady.clear();
         this.suppressed.clear();
      }

      Bedrock.reset();
   }

   private static Enchantment lunge() {
      if (!Compat.SPEAR) {
         return null;
      }

      try {
         return Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lunge"));
      } catch (LinkageError | RuntimeException e) {
         return null;
      }
   }

   static boolean isSpear(Material material) {
      return material.name().endsWith("_SPEAR");
   }

   // ------------------------------------------------------------------ spear charges

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCharge(EntityDamageByEntityEvent event) {
      if (!Compat.SPEAR || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player)) {
         return;
      }

      boolean scale = this.on(Feature.SPEAR_CHARGE_DAMAGE);
      boolean cap = this.on(Feature.SPEAR_CHARGE_CAP);
      boolean glide = this.on(Feature.NO_GLIDE_CHARGE);
      if (!scale && !cap && !glide || !Hits.charging(attacker)) {
         return;
      }

      double before = event.getDamage();
      double damage = chargeDamage(
         before,
         glide && attacker.isGliding() ? Attributes.value(attacker, Attributes.attackDamage(), before) : Double.POSITIVE_INFINITY,
         scale ? this.settings().value(Feature.SPEAR_CHARGE_DAMAGE) : 1.0,
         cap ? this.settings().value(Feature.SPEAR_CHARGE_CAP) * 2.0 : Double.POSITIVE_INFINITY
      );
      if (Math.abs(damage - before) > 1.0E-9) {
         event.setDamage(damage);
      }
   }

   /** A charge's damage: held to a normal hit while gliding, then scaled, then capped. */
   static double chargeDamage(double damage, double glideLimit, double factor, double cap) {
      double out = Math.min(damage, glideLimit) * factor;
      return Math.max(0.0, Math.min(out, cap));
   }

   static boolean swordOrAxe(Material material) {
      String name = material.name();
      return name.endsWith("_SWORD") || name.endsWith("_AXE");
   }

   // ------------------------------------------------------------------ lunge

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onLunge(EntityExhaustionEvent event) {
      if (this.lunge == null || !(event.getEntity() instanceof Player player) || !this.on(Feature.LUNGE_COOLDOWN)) {
         return;
      }

      if (!"ENCHANTMENT_EFFECT".equals(event.getExhaustionReason().name())) {
         return;
      }

      ItemStack hand = player.getInventory().getItemInMainHand();
      if (!isSpear(hand.getType()) || hand.getEnchantmentLevel(this.lunge) <= 0) {
         return;
      }

      int now = Bukkit.getCurrentTick();
      Integer ready = this.lungeReady.get(player.getUniqueId());
      if (ready != null && now < ready) {
         event.setCancelled(true);
         this.suppressed.put(player.getUniqueId(), now);
         // The client may already have started the dash itself; stop it next tick.
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
               Vector velocity = player.getVelocity();
               player.setVelocity(new Vector(0.0, velocity.getY(), 0.0));
            }
         });
         this.notice(player, ready - now);
      } else {
         this.lungeReady.put(player.getUniqueId(), now + (int)Math.round(this.settings().value(Feature.LUNGE_COOLDOWN) * 20.0));
      }
   }

   /** The server's own forward push from a suppressed lunge goes out later the same tick; flatten it. */
   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPush(PlayerVelocityEvent event) {
      Integer tick = this.suppressed.get(event.getPlayer().getUniqueId());
      if (tick != null) {
         if (Bukkit.getCurrentTick() - tick <= 1) {
            Vector velocity = event.getVelocity();
            event.setVelocity(new Vector(0.0, velocity.getY(), 0.0));
         } else {
            this.suppressed.remove(event.getPlayer().getUniqueId());
         }
      }
   }

   private void notice(Player player, int ticksLeft) {
      long now = System.currentTimeMillis();
      Long last = this.lastNotice.get(player.getUniqueId());
      if (last == null || now - last >= 750L) {
         this.lastNotice.put(player.getUniqueId(), now);
         this.plugin.actionBar(player, "lunge-cooldown", Map.of("seconds", Text.trim(Math.ceil(ticksLeft / 2.0) / 10.0)));
      }
   }

   // ------------------------------------------------------------------ bedrock

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBedrockHit(EntityDamageByEntityEvent event) {
      if (!this.on(Feature.BEDROCK_BUFF) || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player)) {
         return;
      }

      if (event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) {
         return;
      }

      if (swordOrAxe(attacker.getInventory().getItemInMainHand().getType()) && Bedrock.isBedrock(attacker)) {
         event.setDamage(event.getDamage() * bedrockMultiplier(this.settings().value(Feature.BEDROCK_BUFF)));
      }
   }

   static double bedrockMultiplier(double percent) {
      return 1.0 + Math.max(0.0, percent) / 100.0;
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID id = event.getPlayer().getUniqueId();
      // The cooldown survives a relog while it runs; spent ones are dropped.
      Integer ready = this.lungeReady.get(id);
      if (ready != null && ready <= Bukkit.getCurrentTick()) {
         this.lungeReady.remove(id);
      }

      this.suppressed.remove(id);
      this.lastNotice.remove(id);
      Bedrock.forget(id);
   }
}
