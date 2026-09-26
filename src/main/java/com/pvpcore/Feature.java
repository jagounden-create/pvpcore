package com.pvpcore;

import com.pvpcore.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Every switch the plugin has. The order here is the order in the menu and in /pvpcore status.
 * Materials used here must exist on the oldest supported version (1.21).
 */
public enum Feature {
   // ---------------------------------------------------------------- Sword
   KNOCKBACK_DISTANCE(
      Section.COMBAT, "combat.knockback-distance.enabled", true, Material.PISTON, "Knockback Distance",
      new Value("combat.knockback-distance.factor", 0.9, 0.5, 1.5, 0.05, Unit.FACTOR, "1.0x"),
      Requirement.KNOCKBACK_EVENT,
      "How far a hit throws players back.",
      "Lower keeps fights close."
   ),
   VERTICAL_KNOCKBACK(
      Section.COMBAT, "combat.vertical-knockback.enabled", true, Material.SLIME_BALL, "Vertical Knockback",
      new Value("combat.vertical-knockback.lift", 0.45, 0.3, 0.8, 0.05, Unit.LIFT, "0.4"),
      Requirement.KNOCKBACK_EVENT,
      "Every hit near the ground lifts,",
      "even when ping hides the landing."
   ),
   INSTANT_KNOCKBACK(
      Section.COMBAT, "combat.instant-knockback", true, Material.LIGHTNING_ROD, "Instant Knockback", null,
      Requirement.LEAF,
      "Hits and knockback reach both",
      "players the moment they land."
   ),
   ATTRIBUTE_SWAPPING(
      Section.COMBAT, "combat.attribute-swapping", true, Material.DIAMOND_SWORD, "Attribute Swapping", null,
      Requirement.PAPER_SWITCH,
      "Swap items mid-hit and keep the",
      "damage of the item you swapped from."
   ),
   HIT_DELAY(
      Section.COMBAT, "combat.hit-delay.enabled", false, Material.REPEATER, "Hit Delay",
      new Value("combat.hit-delay.ticks", 20.0, 0.0, 40.0, 1.0, Unit.TICKS, "20 ticks"),
      Requirement.NONE,
      "Invulnerability after taking a hit."
   ),
   NO_SWEEP(
      Section.WEAPONS, "combat.disable-sweep", false, Material.IRON_SWORD, "No Sweep Damage", null,
      Requirement.NONE,
      "Sword sweeps stop hurting players",
      "standing next to your target."
   ),
   LUNGE_COOLDOWN(
      Section.WEAPONS, "combat.lunge-cooldown.enabled", true, Material.TRIDENT, "Lunge Cooldown",
      new Value("combat.lunge-cooldown.seconds", 3.0, 0.5, 30.0, 0.5, Unit.SECONDS, null),
      Requirement.SPEAR,
      "Time between spear Lunge dashes.",
      "Jabs still hit while it recharges."
   ),
   BEDROCK_BUFF(
      Section.WEAPONS, "combat.bedrock-buff.enabled", true, Material.BEDROCK, "Bedrock Buff",
      new Value("combat.bedrock-buff.percent", 2.0, 0.5, 10.0, 0.5, Unit.PERCENT, null),
      Requirement.NONE,
      "Bedrock players deal a little more",
      "damage with swords and axes.",
      "Needs Geyser or Floodgate."
   ),

   // ---------------------------------------------------------------- Mace
   SMASH_PROTECTION(
      Section.MACE, "mace.smash-fall-protection", true, Material.FEATHER, "Smash Fall Safety", null,
      Requirement.NONE,
      "A smash that connects never costs",
      "fall damage, even when lag or",
      "hit immunity swallowed the hit."
   ),
   SMASH_DAMAGE(
      Section.MACE, "mace.smash-damage.enabled", false, Material.MACE, "Smash Damage",
      new Value("mace.smash-damage.factor", 1.0, 0.25, 2.0, 0.05, Unit.FACTOR, "1.0x"),
      Requirement.NONE,
      "Scales the bonus damage a smash",
      "gets from falling."
   ),
   SMASH_CAP(
      Section.MACE, "mace.damage-cap.enabled", false, Material.ANVIL, "Mace Damage Cap",
      new Value("mace.damage-cap.hearts", 20.0, 5.0, 50.0, 1.0, Unit.HEARTS, null),
      Requirement.NONE,
      "Most damage one mace hit can deal,",
      "before armor."
   ),
   SMASH_COOLDOWN(
      Section.MACE, "mace.smash-cooldown.enabled", false, Material.HEAVY_CORE, "Smash Cooldown",
      new Value("mace.smash-cooldown.seconds", 1.5, 0.5, 10.0, 0.5, Unit.SECONDS, null),
      Requirement.NONE,
      "After a smash, the next one deals",
      "normal hit damage until it wears off."
   ),
   WIND_CHARGE_COOLDOWN(
      Section.MACE, "mace.wind-charge-cooldown.enabled", false, Material.WIND_CHARGE, "Wind Charge Cooldown",
      new Value("mace.wind-charge-cooldown.ticks", 10.0, 0.0, 100.0, 5.0, Unit.TICKS, "10 ticks"),
      Requirement.NONE,
      "Time between wind charge throws."
   ),
   NO_ELYTRA_SMASH(
      Section.MACE, "mace.no-elytra-smash", false, Material.ELYTRA, "No Elytra Smash", null,
      Requirement.NONE,
      "Falls that began with an elytra",
      "glide get no smash bonus."
   ),

   // ---------------------------------------------------------------- Cart
   CART_HIT_REG(
      Section.CART, "cart.hit-reg.enabled", true, Material.TNT_MINECART, "Cart Hit Reg",
      new Value("cart.hit-reg.max-ms", 250.0, 50.0, 500.0, 50.0, Unit.MILLIS, null),
      Requirement.NONE,
      "A flame arrow that reached a cart's",
      "spot just before the cart appeared",
      "still sets it off. Makes up for ping."
   ),
   CART_BOW_DAMAGE(
      Section.CART, "cart.bow-damage.enabled", true, Material.BOW, "Bow Cart Damage",
      new Value("cart.bow-damage.factor", 0.8, 0.1, 1.5, 0.05, Unit.FACTOR, "1.0x"),
      Requirement.NONE,
      "Damage of carts set off by a bow",
      "arrow (insta cart)."
   ),
   CART_CROSSBOW_DAMAGE(
      Section.CART, "cart.crossbow-damage.enabled", true, Material.CROSSBOW, "Crossbow Cart Damage",
      new Value("cart.crossbow-damage.factor", 0.6, 0.1, 1.5, 0.05, Unit.FACTOR, "1.0x"),
      Requirement.NONE,
      "Damage of carts set off by a",
      "crossbow arrow. Nerfed harder than",
      "bow carts by default."
   ),
   CART_POWER(
      Section.CART, "cart.consistent-power.enabled", false, Material.TNT, "Consistent Power",
      new Value("cart.consistent-power.power", 6.0, 4.0, 11.5, 0.5, Unit.POWER, "random 4 to 11.5"),
      Requirement.NONE,
      "Every cart blast has the same",
      "strength instead of a random roll."
   ),
   CART_SELF_DAMAGE(
      Section.CART, "cart.self-damage.enabled", false, Material.TOTEM_OF_UNDYING, "Self Damage",
      new Value("cart.self-damage.percent", 50.0, 0.0, 100.0, 10.0, Unit.PERCENT, "100%"),
      Requirement.NONE,
      "How much your own cart blast",
      "hurts you."
   ),
   CART_TERRAIN(
      Section.CART_PERF, "cart.protect-terrain", false, Material.GRASS_BLOCK, "Protect Terrain", null,
      Requirement.NONE,
      "Cart blasts still hurt players",
      "but leave blocks intact."
   ),
   CART_FUSE(
      Section.CART, "cart.activator-fuse.enabled", false, Material.ACTIVATOR_RAIL, "Activator Fuse",
      new Value("cart.activator-fuse.ticks", 40.0, 0.0, 80.0, 5.0, Unit.TICKS, "80 ticks"),
      Requirement.NONE,
      "Fuse time of carts lit by an",
      "activator rail."
   ),
   CART_LIMIT(
      Section.CART_PERF, "cart.chunk-limit.enabled", true, Material.RAIL, "Cart Limit",
      new Value("cart.chunk-limit.max", 12.0, 1.0, 64.0, 1.0, Unit.COUNT, null),
      Requirement.NONE,
      "Most TNT carts one chunk can hold.",
      "Stops cart stacks lagging the server."
   ),
   CART_CLEANUP(
      Section.CART_PERF, "cart.cleanup.enabled", false, Material.BRUSH, "Cart Cleanup",
      new Value("cart.cleanup.seconds", 30.0, 5.0, 300.0, 5.0, Unit.SECONDS, null),
      Requirement.NONE,
      "Unlit TNT carts players place are",
      "removed after this long."
   ),
   CART_MERGE(
      Section.CART_PERF, "cart.merge-blasts", true, Material.GUNPOWDER, "Blast Merge", null,
      Requirement.NONE,
      "Carts going off together in one",
      "spot make one blast, not a chain",
      "of lag spikes."
   ),
   CART_NO_DROPS(
      Section.CART_PERF, "cart.no-block-drops", true, Material.HOPPER, "No Blast Drops", null,
      Requirement.NONE,
      "Blocks a cart blast breaks drop",
      "nothing, so no item lag."
   ),

   // ---------------------------------------------------------------- Diamond SMP: shields
   SHIELD_USAGE(
      Section.SHIELDS, "shields.usage", true, Material.SHIELD, "Shield Usage", null,
      Requirement.NONE,
      "Players can block with shields."
   ),
   SHIELD_DELAY(
      Section.SHIELDS, "shields.delay.enabled", true, Material.SUGAR, "Shield Delay",
      new Value("shields.delay.ms", 120.0, 0.0, 250.0, 10.0, Unit.MILLIS, "250ms"),
      Requirement.NONE,
      "Time before a raised shield blocks.",
      "Set once, so shields never reload."
   ),
   SHIELD_STUN(
      Section.SHIELDS, "shields.stun.enabled", true, Material.IRON_AXE, "Shield Stun",
      new Value("shields.stun.seconds", 5.0, 0.5, 10.0, 0.5, Unit.SECONDS, "5s"),
      Requirement.NONE,
      "Axes always stun a raised shield",
      "and the follow-up hit lands."
   ),
   BREAK_SOUND(
      Section.SHIELDS, "shields.break-sound", true, Material.BELL, "Break Sound", null,
      Requirement.NONE,
      "Both players hear the shield break."
   ),

   // ---------------------------------------------------------------- Diamond SMP: pots & pearls
   FAST_POTS(
      Section.POTS_PEARLS, "pots.fast-pots.enabled", true, Material.SPLASH_POTION, "Fast Pots",
      new Value("pots.fast-pots.speed", 2.5, 0.25, 3.5, 0.25, Unit.SPEED, null),
      Requirement.NONE,
      "Pots thrown at your feet burst",
      "instantly, even mid-jump."
   ),
   SELF_POT_FIX(
      Section.POTS_PEARLS, "pots.full-strength-self-pots", true, Material.GLISTERING_MELON_SLICE, "Pot Accuracy", null,
      Requirement.NONE,
      "Your own healing pots always heal",
      "you at full strength."
   ),
   SMOOTH_PEARLS(
      Section.POTS_PEARLS, "pearls.land-on-impact", true, Material.ENDER_PEARL, "Smooth Pearls", null,
      Requirement.NONE,
      "Land exactly where your pearl hit,",
      "never short or stuck in a wall."
   ),
   NO_PEARL_DAMAGE(
      Section.POTS_PEARLS, "pearls.no-damage", false, Material.ENDER_EYE, "No Pearl Damage", null,
      Requirement.NONE,
      "Pearls stop hurting you on landing."
   ),
   PEARL_COOLDOWN(
      Section.POTS_PEARLS, "pearls.cooldown.enabled", false, Material.CLOCK, "Pearl Cooldown",
      new Value("pearls.cooldown.ticks", 20.0, 0.0, 300.0, 5.0, Unit.TICKS, "20 ticks"),
      Requirement.NONE,
      "Time between pearl throws."
   ),
   XP_CLUMPS(
      Section.POTS_PEARLS, "clumps.enabled", true, Material.EXPERIENCE_BOTTLE, "XP Clumps",
      new Value("clumps.merge-radius", 2.0, 0.0, 6.0, 0.5, Unit.BLOCKS, null),
      Requirement.NONE,
      "XP orbs merge and absorb instantly,",
      "so bottles mend gear in one go."
   ),

   // ---------------------------------------------------------------- General: display
   HEALTH_BELOW_NAME(
      Section.DISPLAY, "health.below-name", true, Material.NAME_TAG, "Health Under Name", null,
      Requirement.NONE,
      "Health and a heart under every",
      "player's name, like 17 " + Text.HEART + "."
   ),
   HEALTH_ACTION_BAR(
      Section.DISPLAY, "health.action-bar", true, Material.RED_DYE, "Target Health", null,
      Requirement.NONE,
      "Hit someone and your action bar",
      "shows their health."
   ),
   NO_DAMAGE_HEARTS(
      Section.DISPLAY, "particles.no-damage-hearts", true, Material.REDSTONE, "No Damage Hearts", null,
      Requirement.PACKETS,
      "The dark heart particles a hit",
      "spawns are not sent."
   ),
   NO_HIT_PARTICLES(
      Section.DISPLAY, "particles.no-hit-particles", true, Material.GLOWSTONE_DUST, "No Hit Particles", null,
      Requirement.PACKETS,
      "Crit and sweep particles are not",
      "sent. Attackers still see their own."
   ),
   NO_EXPLOSION_PARTICLES(
      Section.DISPLAY, "particles.light-explosions", true, Material.FIRE_CHARGE, "Light Explosions", null,
      Requirement.PACKETS,
      "Big blasts show one small puff and",
      "no flying debris. Wind charges",
      "keep their gusts."
   ),

   // ---------------------------------------------------------------- General: server
   ARROW_CLEANUP(
      Section.SERVER, "general.arrow-cleanup.enabled", true, Material.SPECTRAL_ARROW, "Arrow Cleanup",
      new Value("general.arrow-cleanup.seconds", 10.0, 2.0, 60.0, 1.0, Unit.SECONDS, "60s"),
      Requirement.NONE,
      "Arrows stuck in blocks vanish",
      "after this long."
   ),
   INSTANT_RESPAWN(
      Section.SERVER, "general.instant-respawn", false, Material.RECOVERY_COMPASS, "Instant Respawn", null,
      Requirement.NONE,
      "Skip the death screen."
   ),
   COMBAT_TAG(
      Section.SERVER, "combat-tag.enabled", false, Material.IRON_BARS, "Combat Tag",
      new Value("combat-tag.seconds", 15.0, 5.0, 60.0, 1.0, Unit.SECONDS, null),
      Requirement.NONE,
      "Hits tag both players. Logging out",
      "while tagged kills you."
   ),
   BAN_CRYSTALS(
      Section.SERVER, "crystal-pvp.ban-crystals", true, Material.END_CRYSTAL, "Ban Crystal PvP", null,
      Requirement.NONE,
      "End crystals can't be placed, hit",
      "or blown up."
   ),
   BAN_ANCHORS(
      Section.SERVER, "crystal-pvp.ban-anchors", true, Material.RESPAWN_ANCHOR, "Ban Anchor PvP", null,
      Requirement.NONE,
      "Respawn anchors can't blow",
      "players up."
   ),
   ITEM_RULES(
      Section.SERVER, "item-rules.enabled", true, Material.SMITHING_TABLE, "Item Rules", null,
      Requirement.NONE,
      "Nerfs, cooldowns and limits from",
      "the Item Rules page."
   );

   private final Section section;
   private final String path;
   private final boolean fallback;
   private final Material icon;
   private final String title;
   private final Value value;
   private final Requirement requirement;
   private final List<String> description;

   Feature(Section section, String path, boolean fallback, Material icon, String title, Value value, Requirement requirement, String... description) {
      this.section = section;
      this.path = path;
      this.fallback = fallback;
      this.icon = icon;
      this.title = title;
      this.value = value;
      this.requirement = requirement;
      this.description = List.of(description);
   }

   public Section section() {
      return this.section;
   }

   public Category category() {
      return this.section.category();
   }

   public String path() {
      return this.path;
   }

   public boolean fallback() {
      return this.fallback;
   }

   public Material icon() {
      return this.icon;
   }

   public String title() {
      return this.title;
   }

   public Value value() {
      return this.value;
   }

   public Requirement requirement() {
      return this.requirement;
   }

   public List<String> description() {
      return this.description;
   }

   public String id() {
      return Text.id(this);
   }

   public static Feature byId(String id) {
      String wanted = id.toLowerCase(Locale.ROOT).replace('_', '-');

      for (Feature feature : values()) {
         if (feature.id().equals(wanted)) {
            return feature;
         }
      }

      return null;
   }

   public enum Category {
      SWORD("Sword", Material.NETHERITE_SWORD, "Knockback, hit reg, spears and buffs."),
      MACE("Mace", Material.MACE, "Smash damage, fall safety and wind charges."),
      CART("Cart", Material.TNT_MINECART, "Cart hit reg, nerfs and lag fixes."),
      DIAMOND_SMP("Diamond SMP", Material.DIAMOND_CHESTPLATE, "Shields, stuns, pots and pearls."),
      GENERAL("General", Material.COMPARATOR, "Health, particles, cleanup and bans.");

      private final String label;
      private final Material icon;
      private final String blurb;

      Category(String label, Material icon, String blurb) {
         this.label = label;
         this.icon = icon;
         this.blurb = blurb;
      }

      public String label() {
         return this.label;
      }

      public Material icon() {
         return this.icon;
      }

      public String blurb() {
         return this.blurb;
      }

      public String id() {
         return Text.id(this);
      }

      public List<Section> sections() {
         List<Section> sections = new ArrayList<>();
         for (Section section : Section.values()) {
            if (section.category() == this) {
               sections.add(section);
            }
         }

         return sections;
      }

      public List<Feature> features() {
         List<Feature> features = new ArrayList<>();
         for (Feature feature : Feature.values()) {
            if (feature.category() == this) {
               features.add(feature);
            }
         }

         return features;
      }

      public static Category byId(String id) {
         String wanted = id.toLowerCase(Locale.ROOT).replace('_', '-');
         for (Category category : values()) {
            if (category.id().equals(wanted)) {
               return category;
            }
         }

         return null;
      }
   }

   /** A row of switches inside a category page. At most seven per section. */
   public enum Section {
      COMBAT(Category.SWORD, "Knockback & Hits"),
      WEAPONS(Category.SWORD, "Weapons"),
      MACE(Category.MACE, "Mace"),
      CART(Category.CART, "Cart PvP"),
      CART_PERF(Category.CART, "Performance"),
      SHIELDS(Category.DIAMOND_SMP, "Shields"),
      POTS_PEARLS(Category.DIAMOND_SMP, "Pots & Pearls"),
      DISPLAY(Category.GENERAL, "Display"),
      SERVER(Category.GENERAL, "Server");

      private final Category category;
      private final String label;

      Section(Category category, String label) {
         this.category = category;
         this.label = label;
      }

      public Category category() {
         return this.category;
      }

      public String label() {
         return this.label;
      }

      public List<Feature> features() {
         List<Feature> features = new ArrayList<>();
         for (Feature feature : Feature.values()) {
            if (feature.section() == this) {
               features.add(feature);
            }
         }

         return features;
      }
   }

   public enum Requirement {
      NONE,
      /** Leaf's flush-knockback switch. */
      LEAF,
      /** Paper's update-equipment-on-player-actions switch. */
      PAPER_SWITCH,
      /** Paper's EntityKnockbackEvent with causes (1.20.6+). */
      KNOCKBACK_EVENT,
      /** Spears and Lunge (1.21.11+). */
      SPEAR,
      /** Filtering outgoing packets, which needs a Paper server whose network internals were recognised. */
      PACKETS;
   }

   public enum Unit {
      TICKS(true),
      MILLIS(true),
      SECONDS(false),
      BLOCKS(false),
      SPEED(false),
      LIFT(false),
      FACTOR(false),
      PERCENT(false),
      HEARTS(true),
      POWER(false),
      COUNT(true);

      private final boolean whole;

      Unit(boolean whole) {
         this.whole = whole;
      }

      public boolean whole() {
         return this.whole;
      }

      public String format(double amount) {
         String n = Text.trim(amount);
         return switch (this) {
            case TICKS -> n + (amount == 1.0 ? " tick" : " ticks") + " (" + Text.trim(amount / 20.0) + "s)";
            case MILLIS -> n + "ms";
            case SECONDS -> n + "s";
            case BLOCKS -> n + (amount == 1.0 ? " block" : " blocks");
            case SPEED -> "+" + n;
            case LIFT, POWER, COUNT -> n;
            case FACTOR -> n + "x";
            case PERCENT -> n + "%";
            case HEARTS -> n + " " + Text.HEART;
         };
      }

      /** Short form for a step, e.g. "5 ticks" rather than "5 ticks (0.25s)". */
      public String step(double amount) {
         String n = Text.trim(amount);
         return switch (this) {
            case TICKS -> n + (amount == 1.0 ? " tick" : " ticks");
            case SPEED, LIFT, POWER, COUNT, FACTOR -> n;
            default -> this.format(amount);
         };
      }
   }

   public record Value(String path, double fallback, double min, double max, double step, Unit unit, String vanilla) {
      public double clamp(double amount) {
         if (Double.isNaN(amount)) {
            return this.fallback;
         }

         double clamped = Math.max(this.min, Math.min(this.max, amount));
         // Snap to 1e-9 so repeated +/- clicks never drift into 0.30000000000000004
         clamped = Math.round(clamped * 1.0E9) / 1.0E9;
         return this.unit.whole() ? Math.rint(clamped) : clamped;
      }
   }
}
