package com.pvpcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Other plugins that own part of the combat. PvPCore steps aside for them rather than fighting over it. */
public final class Integrations {
   private Integrations() {
   }

   /** PaperAttributeSwapFix owns Paper's equipment switch when installed. */
   public static boolean attributeSwapPlugin() {
      return enabled("PaperAttributeSwapFix") || enabled("PASF");
   }

   /** KnockbackSync handles ping-based vertical knockback when installed. */
   public static boolean knockbackSync() {
      return enabled("KnockbackSync");
   }

   private static boolean enabled(String name) {
      Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
      return plugin != null && plugin.isEnabled();
   }
}
