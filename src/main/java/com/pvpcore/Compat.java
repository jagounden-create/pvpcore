package com.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;

/**
 * What this server can do. Everything newer than 1.21 is looked up here once, so no class ever touches an API the
 * running server does not have. Listeners for version-specific events live in their own classes and are only
 * registered when the event exists (Bukkit resolves every handler's parameter types when it registers a listener).
 */
public final class Compat {
   /** 1.21.5+: shields are data-driven (blocks_attacks / weapon components). */
   public static final boolean DATA_DRIVEN_SHIELDS = hasField("io.papermc.paper.datacomponent.DataComponentTypes", "BLOCKS_ATTACKS")
      && hasField("io.papermc.paper.datacomponent.DataComponentTypes", "WEAPON");
   /** 1.21.5+: item tooltips are hidden through the tooltip_display component, default attributes included. */
   public static final boolean TOOLTIP_DISPLAY = hasClass("io.papermc.paper.datacomponent.item.TooltipDisplay");
   public static final boolean SHIELD_DISABLE_EVENT = hasClass("io.papermc.paper.event.player.PlayerShieldDisableEvent");
   public static final boolean PRE_ATTACK_EVENT = hasClass("io.papermc.paper.event.player.PrePlayerAttackEntityEvent");
   public static final boolean KNOCKBACK_EVENT = hasClass("io.papermc.paper.event.entity.EntityKnockbackEvent$Cause")
      && hasClass("com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent");
   public static final boolean WIND_CHARGE = hasClass("org.bukkit.entity.WindCharge") && Material.matchMaterial("WIND_CHARGE") != null;
   public static final boolean MACE = Material.matchMaterial("MACE") != null;
   /** 1.21.11+: spears and the Lunge enchantment. */
   public static final boolean SPEAR = Material.matchMaterial("IRON_SPEAR") != null;
   /** Paper's entity tracker API: which players a player is currently sent to. */
   public static final boolean TRACKED_BY = hasMethod("org.bukkit.entity.Entity", "getTrackedBy");

   private Compat() {
   }

   public static String serverVersion() {
      try {
         return Bukkit.getMinecraftVersion();
      } catch (Throwable ignored) {
         return Bukkit.getBukkitVersion();
      }
   }

   static boolean hasClass(String name) {
      try {
         Class.forName(name, false, Compat.class.getClassLoader());
         return true;
      } catch (ClassNotFoundException | LinkageError e) {
         return false;
      }
   }

   static boolean hasMethod(String className, String method, Class<?>... parameters) {
      try {
         Class.forName(className, false, Compat.class.getClassLoader()).getMethod(method, parameters);
         return true;
      } catch (ReflectiveOperationException | LinkageError e) {
         return false;
      }
   }

   static boolean hasField(String className, String field) {
      try {
         Class.forName(className, false, Compat.class.getClassLoader()).getField(field);
         return true;
      } catch (ReflectiveOperationException | LinkageError e) {
         return false;
      }
   }
}
