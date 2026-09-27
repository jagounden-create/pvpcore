package com.pvpcore;

import org.bukkit.event.Listener;

public abstract class Module implements Listener {
   protected final PvPCore plugin;

   protected Module(PvPCore plugin) {
      this.plugin = plugin;
   }

   protected final Settings settings() {
      return this.plugin.settings();
   }

   protected final boolean on(Feature feature) {
      return this.plugin.settings().on(feature);
   }

   /** Called once after every module is registered. */
   public void start() {
   }

   /** Called on enable and after every settings change. */
   public void apply() {
   }

   /** Called on disable; must leave players and the server the way vanilla would. */
   public void stop() {
   }
}
