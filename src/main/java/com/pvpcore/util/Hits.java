package com.pvpcore.util;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

/**
 * What kind of hit a damage event is. Newer weapons have their own damage types (a mace smash is "mace_smash", spear
 * attacks are "spear") and servers don't all map those to the same Bukkit cause, so the type is checked as well.
 * Damage types are read by key, so this works on versions that don't have the newer ones.
 */
public final class Hits {
   public static final String MACE_SMASH = "mace_smash";
   public static final String SPEAR = "spear";
   public static final String PLAYER_ATTACK = "player_attack";

   private Hits() {
   }

   /** The damage type's key, like "mace_smash", or "" when it can't be read on this server. */
   public static String type(EntityDamageEvent event) {
      try {
         return event.getDamageSource().getDamageType().getKey().getKey();
      } catch (RuntimeException | LinkageError e) {
         return "";
      }
   }

   /** A swing of a held weapon: a normal attack, a sweep, a mace smash or a spear hit. */
   public static boolean melee(EntityDamageEvent event) {
      DamageCause cause = event.getCause();
      if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
         return true;
      }

      String type = type(event);
      return type.equals(MACE_SMASH) || type.equals(SPEAR) || type.equals(PLAYER_ATTACK);
   }

   /** The main hit of a swing (not the sweep that comes with it). */
   public static boolean primary(EntityDamageEvent event) {
      return melee(event) && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK;
   }

   public static boolean isSpear(ItemStack item) {
      return item != null && item.getType().name().endsWith("_SPEAR");
   }

   /** Charging a spear: holding it up to run someone through (as opposed to a quick jab). */
   public static boolean charging(Player player) {
      try {
         return player.isHandRaised() && isSpear(player.getActiveItem());
      } catch (RuntimeException | LinkageError e) {
         return false;
      }
   }
}
