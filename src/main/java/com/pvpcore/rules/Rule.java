package com.pvpcore.rules;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * What one item or block is allowed to do. A value at its neutral setting (damage 1.0, everything else 0/false)
 * means that part of the rule does nothing.
 */
public record Rule(Material material, double damage, double maxDamage, double cooldown, boolean disabled, int chunkLimit, int playerLimit, List<String> worlds) {
   public static final double MAX_MULTIPLIER = 10.0;
   public static final double MAX_HEARTS = 1000.0;
   public static final double MAX_COOLDOWN = 3600.0;
   public static final int MAX_LIMIT = 100000;

   public Rule {
      damage = Double.isFinite(damage) ? clamp(damage, 0.0, MAX_MULTIPLIER) : 1.0;
      maxDamage = Double.isFinite(maxDamage) ? clamp(maxDamage, 0.0, MAX_HEARTS) : 0.0;
      cooldown = Double.isFinite(cooldown) ? clamp(cooldown, 0.0, MAX_COOLDOWN) : 0.0;
      chunkLimit = (int)clamp(chunkLimit, 0, MAX_LIMIT);
      playerLimit = (int)clamp(playerLimit, 0, MAX_LIMIT);
      worlds = worlds == null ? List.of() : List.copyOf(worlds);
   }

   public static Rule empty(Material material) {
      return new Rule(material, 1.0, 0.0, 0.0, false, 0, 0, List.of());
   }

   private static double clamp(double value, double min, double max) {
      double clamped = Math.max(min, Math.min(max, value));
      return Math.round(clamped * 1.0E6) / 1.0E6;
   }

   public Rule withDamage(double value) {
      return new Rule(this.material, value, this.maxDamage, this.cooldown, this.disabled, this.chunkLimit, this.playerLimit, this.worlds);
   }

   public Rule withMaxDamage(double value) {
      return new Rule(this.material, this.damage, value, this.cooldown, this.disabled, this.chunkLimit, this.playerLimit, this.worlds);
   }

   public Rule withCooldown(double value) {
      return new Rule(this.material, this.damage, this.maxDamage, value, this.disabled, this.chunkLimit, this.playerLimit, this.worlds);
   }

   public Rule withDisabled(boolean value) {
      return new Rule(this.material, this.damage, this.maxDamage, this.cooldown, value, this.chunkLimit, this.playerLimit, this.worlds);
   }

   public Rule withChunkLimit(int value) {
      return new Rule(this.material, this.damage, this.maxDamage, this.cooldown, this.disabled, value, this.playerLimit, this.worlds);
   }

   public Rule withPlayerLimit(int value) {
      return new Rule(this.material, this.damage, this.maxDamage, this.cooldown, this.disabled, this.chunkLimit, value, this.worlds);
   }

   public Rule withWorlds(List<String> value) {
      return new Rule(this.material, this.damage, this.maxDamage, this.cooldown, this.disabled, this.chunkLimit, this.playerLimit, value);
   }

   public boolean changesDamage() {
      return Math.abs(this.damage - 1.0) > 1.0E-9 || this.maxDamage > 0.0;
   }

   public boolean hasCooldown() {
      return this.cooldown > 0.0;
   }

   public int cooldownTicks() {
      return (int)Math.round(this.cooldown * 20.0);
   }

   public boolean limitsBlocks() {
      return this.chunkLimit > 0 || this.playerLimit > 0;
   }

   /** Whether this rule has any effect at all. */
   public boolean active() {
      return this.changesDamage() || this.hasCooldown() || this.disabled || this.limitsBlocks();
   }

   public boolean appliesIn(World world) {
      if (this.worlds.isEmpty()) {
         return true;
      }

      if (world == null) {
         return false;
      }

      for (String name : this.worlds) {
         if (name.equalsIgnoreCase(world.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Weapons start their cooldown when they hit. Everything else starts it when used (eaten, thrown, shot, placed,
    * emptied, popped), so punching someone with a pearl in hand never puts pearls on cooldown.
    */
   public static boolean weapon(Material material) {
      String name = material.name();
      return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_SPEAR") || name.equals("MACE") || name.equals("TRIDENT")
         || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
   }

   public static Material parseMaterial(String text) {
      if (text == null) {
         return null;
      }

      Material material = Material.matchMaterial(text.trim());
      return material == null || material.isAir() || material.isLegacy() ? null : material;
   }
}
