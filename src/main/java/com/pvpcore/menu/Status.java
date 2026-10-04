package com.pvpcore.menu;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Integrations;
import com.pvpcore.PvPCore;
import com.pvpcore.ServerTweaks;
import com.pvpcore.Settings;
import com.pvpcore.util.Bedrock;
import java.util.ArrayList;
import java.util.List;

/** Whether a switch can work on this server, and anything the admin should know about it. */
public final class Status {
   private Status() {
   }

   public record State(boolean on, boolean available, String reason, List<String> notes) {
      public State {
         notes = List.copyOf(notes);
      }
   }

   public static State of(PvPCore plugin, Feature feature) {
      Settings settings = plugin.settings();
      ServerTweaks tweaks = plugin.tweaks();
      boolean on = settings.on(feature);
      String reason = null;
      List<String> notes = new ArrayList<>();

      switch (feature.requirement()) {
         case LEAF -> {
            if (!tweaks.instantKnockbackAvailable()) {
               reason = "Needs a Leaf server.";
            } else if (!on && tweaks.instantKnockbackByServer()) {
               notes.add("Still on: Leaf's own config turns it on.");
            }
         }
         case PAPER_SWITCH -> {
            if (Integrations.attributeSwapPlugin()) {
               reason = "PaperAttributeSwapFix handles this.";
            } else if (!tweaks.attributeSwappingAvailable()) {
               if (Compat.PRE_ATTACK_EVENT) {
                  notes.add("Done by PvPCore here:");
                  notes.add("this server has no Paper switch.");
               } else {
                  reason = "This server has no such switch.";
               }
            } else if (!on && tweaks.attributeSwappingByServer()) {
               notes.add("Still on: paper-global.yml turns it on.");
            }
         }
         case KNOCKBACK_EVENT -> {
            if (!Compat.KNOCKBACK_EVENT) {
               reason = "Needs a newer Paper build.";
            }
         }
         case SPEAR -> {
            if (!Compat.SPEAR) {
               reason = "Needs Minecraft 1.21.11 or newer.";
            }
         }
         case TRACKER -> {
            if (plugin.ghosts() == null || !plugin.ghosts().scannerAvailable()) {
               reason = "Needs a newer Paper build.";
            }
         }
         case PACKETS -> {
            if (!plugin.particlesAvailable()) {
               reason = "Not supported on this server.";
            }
         }
         case NONE -> {
         }
      }

      switch (feature) {
         case VERTICAL_KNOCKBACK -> {
            if (reason == null && Integrations.knockbackSync()) {
               reason = "KnockbackSync handles this.";
            }
         }
         case SHIELD_STUN -> {
            if (!tweaks.shieldStunAvailable()) {
               notes.add("Paper's stun switch is missing here;");
               notes.add("the follow-up is held open instead.");
            }

            if (!settings.on(Feature.SHIELD_USAGE)) {
               notes.add("Shield Usage is off.");
            }
         }
         case SHIELD_DELAY -> {
            if (!plugin.modernShields()) {
               notes.add("Rounded to 50ms steps on this version.");
            }

            if (!settings.on(Feature.SHIELD_USAGE)) {
               notes.add("Shield Usage is off.");
            }
         }
         case BREAK_SOUND -> {
            if (!settings.on(Feature.SHIELD_STUN)) {
               notes.add("Shield Stun is off.");
            }
         }
         case WIND_CHARGE_COOLDOWN, WIND_STOP, WIND_JUMP -> {
            if (!Compat.WIND_CHARGE) {
               reason = "No wind charges on this version.";
            }
         }
         case GHOST_SHIELDS -> {
            if (plugin.ghosts() != null && !plugin.ghosts().lightResync()) {
               notes.add("Only the held items are re-sent");
               notes.add("on this server.");
            }
         }
         case GHOST_HEARTS -> {
            if (plugin.ghosts() != null && !plugin.ghosts().lightResync()) {
               notes.add("Fixed by re-showing the player");
               notes.add("on this server (a short blink).");
            }
         }
         case PEARL_ANTI_GLITCH -> {
            if (!settings.tuning().pearlRefund()) {
               notes.add("Blocked pearls are not refunded.");
            }
         }
         case BEDROCK_BUFF -> notes.add("Detected with: " + Bedrock.source() + ".");
         case CART_TERRAIN -> {
            if (on && settings.on(Feature.CART_NO_DROPS)) {
               notes.add("No Blast Drops has nothing to do");
               notes.add("while terrain is protected.");
            }
         }
         case ITEM_RULES -> notes.add(plugin.rules().all().size() + " rules in rules.yml.");
         default -> {
         }
      }

      if (feature.category() == Feature.Category.MACE && !Compat.MACE) {
         reason = "No mace on this version.";
      }

      return new State(on, reason == null, reason == null ? "" : reason, notes);
   }
}
