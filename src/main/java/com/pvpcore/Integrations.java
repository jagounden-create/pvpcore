package com.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Other plugins that own part of the combat. PvPCore steps aside for them rather than fighting over it.
 * <p>
 * Asked on every knockback and hotbar swap, so the answers are looked up at most once a second.
 */
public final class Integrations {
   static final int RECHECK_TICKS = 20;
   private static int checked = Integer.MIN_VALUE;
   private static boolean attributeSwap;
   private static boolean knockbackSync;

   private Integrations() {
   }

   /** PaperAttributeSwapFix owns Paper's equipment switch when installed. */
   public static boolean attributeSwapPlugin() {
      refresh();
      return attributeSwap;
   }

   /** KnockbackSync handles ping-based vertical knockback when installed. */
   public static boolean knockbackSync() {
      refresh();
      return knockbackSync;
   }

   /** Look again on the next question: a plugin was just enabled or disabled, or settings were reloaded. */
   public static void forget() {
      checked = Integer.MIN_VALUE;
   }

   private static void refresh() {
      int now = Bukkit.getCurrentTick();
      if (checked == Integer.MIN_VALUE || now - checked >= RECHECK_TICKS || now < checked) {
         checked = now;
         attributeSwap = enabled("PaperAttributeSwapFix") || enabled("PASF");
         knockbackSync = enabled("KnockbackSync");
      }
   }

   private static boolean enabled(String name) {
      Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
      return plugin != null && plugin.isEnabled();
   }
}
