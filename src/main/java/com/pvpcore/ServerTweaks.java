package com.pvpcore;

import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * Server switches that only exist as config fields in Paper (and Leaf). They are flipped at runtime while the plugin
 * runs and put back the way they were when it stops, so nobody has to edit paper-global.yml.
 */
public final class ServerTweaks {
   private static final String PAPER_GLOBAL = "io.papermc.paper.configuration.GlobalConfiguration";
   private static final String LEAF_KNOCKBACK = "org.dreeam.leaf.config.modules.gameplay.Knockback";
   private static final String SHIELD_STUN_FIELD = "skipVanillaDamageTickWhenShieldBlocked";
   private static final String EQUIPMENT_FIELD = "updateEquipmentOnPlayerActions";
   private final Logger logger;
   private final boolean paperAvailable;
   private boolean paperOriginal;
   private boolean paperTouched;
   private final boolean stunAvailable;
   private boolean stunOriginal;
   private boolean stunTouched;
   private final Field leafFlush;
   private boolean leafOriginal;
   private boolean leafTouched;

   ServerTweaks(Logger logger) {
      this.logger = logger;
      boolean paper = false;

      try {
         this.paperOriginal = readFlag(EQUIPMENT_FIELD);
         paper = true;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         logger.fine("Paper's update-equipment-on-player-actions switch is not on this server (" + e.getClass().getSimpleName() + ").");
      }

      this.paperAvailable = paper;
      boolean stun = false;

      try {
         this.stunOriginal = readFlag(SHIELD_STUN_FIELD);
         stun = true;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         logger.fine("Paper's skip-vanilla-damage-tick-when-shield-blocked switch is not on this server (" + e.getClass().getSimpleName() + ").");
      }

      this.stunAvailable = stun;
      Field flush;

      try {
         flush = Class.forName(LEAF_KNOCKBACK).getField("flushKnockback");
         this.leafOriginal = flush.getBoolean(null);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         flush = null;
      }

      this.leafFlush = flush;
   }

   public boolean attributeSwappingAvailable() {
      return this.paperAvailable;
   }

   public boolean instantKnockbackAvailable() {
      return this.leafFlush != null;
   }

   public boolean shieldStunAvailable() {
      return this.stunAvailable;
   }

   public boolean shieldStunActive() {
      if (!this.stunAvailable) {
         return false;
      }

      try {
         return readFlag(SHIELD_STUN_FIELD);
      } catch (ReflectiveOperationException | RuntimeException e) {
         return false;
      }
   }

   public boolean attributeSwappingActive() {
      if (!this.paperAvailable) {
         return false;
      }

      try {
         return !readFlag(EQUIPMENT_FIELD);
      } catch (ReflectiveOperationException | RuntimeException e) {
         return false;
      }
   }

   public boolean instantKnockbackActive() {
      if (this.leafFlush == null) {
         return false;
      }

      try {
         return this.leafFlush.getBoolean(null);
      } catch (IllegalAccessException | RuntimeException e) {
         return false;
      }
   }

   public boolean attributeSwappingByServer() {
      return this.paperAvailable && !this.paperOriginal;
   }

   public boolean instantKnockbackByServer() {
      return this.leafFlush != null && this.leafOriginal;
   }

   public void attributeSwapping(boolean on) {
      if (this.paperAvailable) {
         boolean update = !on && this.paperOriginal;

         try {
            if (readFlag(EQUIPMENT_FIELD) != update) {
               writeFlag(EQUIPMENT_FIELD, update);
            }

            this.paperTouched = on;
         } catch (ReflectiveOperationException | RuntimeException e) {
            this.logger.warning("Could not change attribute swapping: " + e);
         }
      }
   }

   public void instantKnockback(boolean on) {
      if (this.leafFlush != null) {
         boolean flush = on || this.leafOriginal;

         try {
            if (this.leafFlush.getBoolean(null) != flush) {
               this.leafFlush.setBoolean(null, flush);
            }

            this.leafTouched = on;
         } catch (IllegalAccessException | RuntimeException e) {
            this.logger.warning("Could not change instant knockback: " + e);
         }
      }
   }

   public void shieldStun(boolean on) {
      if (this.stunAvailable) {
         boolean skip = on || this.stunOriginal;

         try {
            if (readFlag(SHIELD_STUN_FIELD) != skip) {
               writeFlag(SHIELD_STUN_FIELD, skip);
            }

            this.stunTouched = on;
         } catch (ReflectiveOperationException | RuntimeException e) {
            this.logger.warning("Could not change Paper's shield-stun switch: " + e);
         }
      }
   }

   public void restore() {
      if (this.paperTouched) {
         this.attributeSwapping(false);
      }

      if (this.leafTouched) {
         this.instantKnockback(false);
      }

      if (this.stunTouched) {
         this.shieldStun(false);
      }
   }

   private static Object unsupportedSettings() throws ReflectiveOperationException {
      Class<?> global = Class.forName(PAPER_GLOBAL);
      Object instance = global.getMethod("get").invoke(null);
      if (instance == null) {
         throw new IllegalStateException("Paper's global configuration is not loaded");
      }

      return global.getField("unsupportedSettings").get(instance);
   }

   private static boolean readFlag(String field) throws ReflectiveOperationException {
      Object settings = unsupportedSettings();
      return settings.getClass().getField(field).getBoolean(settings);
   }

   private static void writeFlag(String field, boolean value) throws ReflectiveOperationException {
      Object settings = unsupportedSettings();
      settings.getClass().getField(field).setBoolean(settings, value);
   }
}
