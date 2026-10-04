package com.pvpcore;

import com.pvpcore.util.Text;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.bukkit.Material;

/**
 * One-click starting points. Every preset starts from the defaults and then changes only what that game mode
 * plays differently, so features that only touch other items (a mace setting on a sword server) stay harmless.
 */
public enum Preset {
   DIAMOND_SMP(
      "Diamond SMP", Material.DIAMOND_CHESTPLATE,
      settings -> {
      },
      "The defaults: fast shields, real stuns,",
      "instant pots, tight knockback."
   ),
   SWORD(
      "Sword", Material.NETHERITE_SWORD,
      settings -> {
         settings.set(Feature.KNOCKBACK_DISTANCE, false);
         settings.setValue(Feature.VERTICAL_KNOCKBACK, 0.4);
         settings.set(Feature.NO_SWEEP, true);
      },
      "Vanilla knockback distance and lift,",
      "fixed for ping. No sweep damage."
   ),
   MACE(
      "Mace", Material.MACE,
      settings -> {
         settings.set(Feature.KNOCKBACK_DISTANCE, false);
         settings.setValue(Feature.VERTICAL_KNOCKBACK, 0.4);
         settings.set(Feature.SMASH_PROTECTION, true);
      },
      "Vanilla mace damage with smash fall",
      "safety and vanilla knockback."
   ),
   FFA(
      "FFA", Material.WIND_CHARGE,
      settings -> {
         settings.set(Feature.KNOCKBACK_DISTANCE, false);
         settings.setValue(Feature.VERTICAL_KNOCKBACK, 0.4);
         settings.set(Feature.SMASH_PROTECTION, true);
         settings.set(Feature.SMASH_CAP, true);
         settings.set(Feature.LUNGE_COOLDOWN, true);
         settings.set(Feature.SPEAR_CHARGE_CAP, true);
         settings.set(Feature.COMBAT_TAG, true);
         settings.set(Feature.INSTANT_RESPAWN, true);
      },
      "Mace and spear free-for-all: capped",
      "one-shots, combat tag, instant respawn",
      "and every ghost and pearl fix on."
   ),
   CART(
      "Cart", Material.TNT_MINECART,
      settings -> {
         settings.set(Feature.KNOCKBACK_DISTANCE, false);
         settings.setValue(Feature.VERTICAL_KNOCKBACK, 0.4);
         settings.set(Feature.CART_HIT_REG, true);
         settings.set(Feature.CART_TERRAIN, true);
         settings.set(Feature.CART_CLEANUP, true);
         settings.set(Feature.ARROW_CLEANUP, true);
      },
      "Cart hit reg, arenas that don't get",
      "blown apart, and quick cleanup."
   ),
   VANILLA(
      "Vanilla", Material.GRASS_BLOCK,
      settings -> {
         for (Feature feature : Feature.values()) {
            settings.set(feature, false);
         }
      },
      "Every switch off: pure vanilla",
      "combat, nothing changed."
   );

   private final String label;
   private final Material icon;
   private final Consumer<Settings> configure;
   private final List<String> description;

   Preset(String label, Material icon, Consumer<Settings> configure, String... description) {
      this.label = label;
      this.icon = icon;
      this.configure = configure;
      this.description = List.of(description);
   }

   public String label() {
      return this.label;
   }

   public Material icon() {
      return this.icon;
   }

   public List<String> description() {
      return this.description;
   }

   public String id() {
      return Text.id(this);
   }

   void configure(Settings settings) {
      this.configure.accept(settings);
   }

   public static Preset byId(String id) {
      String wanted = id.toLowerCase(Locale.ROOT).replace('_', '-');
      for (Preset preset : values()) {
         if (preset.id().equals(wanted)) {
            return preset;
         }
      }

      return null;
   }
}
