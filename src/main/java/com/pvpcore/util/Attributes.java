package com.pvpcore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

/**
 * Attack attributes, looked up by key: they were renamed in 1.21.2 (generic.attack_damage became attack_damage), and
 * the old and new API spell the constants differently, so neither constant can be named directly.
 */
public final class Attributes {
   private static volatile Attribute attackDamage;
   private static volatile Attribute attackSpeed;
   private static volatile boolean looked;

   private Attributes() {
   }

   public static Attribute attackDamage() {
      lookUp();
      return attackDamage;
   }

   public static Attribute attackSpeed() {
      lookUp();
      return attackSpeed;
   }

   /** The attribute's current value, or {@code fallback} when the entity doesn't have it. */
   public static double value(LivingEntity entity, Attribute attribute, double fallback) {
      if (attribute == null) {
         return fallback;
      }

      AttributeInstance instance = entity.getAttribute(attribute);
      return instance == null ? fallback : instance.getValue();
   }

   private static void lookUp() {
      if (!looked) {
         attackDamage = find("attack_damage", "generic.attack_damage");
         attackSpeed = find("attack_speed", "generic.attack_speed");
         looked = true;
      }
   }

   private static Attribute find(String... keys) {
      for (String key : keys) {
         try {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (attribute != null) {
               return attribute;
            }
         } catch (RuntimeException | LinkageError ignored) {
            // try the next name
         }
      }

      return null;
   }
}
