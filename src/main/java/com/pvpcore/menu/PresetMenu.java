package com.pvpcore.menu;

import com.pvpcore.Preset;
import com.pvpcore.PvPCore;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class PresetMenu extends Menu {
   PresetMenu(PvPCore plugin, Player viewer) {
      super(plugin, viewer);
   }

   @Override
   protected int rows() {
      return 3;
   }

   @Override
   protected Component title() {
      return new Style(this.plugin.settings()).title("Presets");
   }

   @Override
   protected void draw() {
      Preset[] presets = Preset.values();
      int[] slots = Layout.centered(9, presets.length);
      for (int i = 0; i < presets.length; i++) {
         Preset preset = presets[i];
         List<Component> lore = new ArrayList<>();
         for (String line : preset.description()) {
            lore.add(this.style.line(line));
         }

         lore.add(Component.empty());
         lore.add(this.style.line("Resets every switch, then applies"));
         lore.add(this.style.line("this setup. Item rules are kept."));
         lore.add(Component.empty());
         lore.add(this.style.hint("Shift-click", "to apply"));
         this.button(slots[i], Items.icon(preset.icon(), this.style.name(preset.label(), true), lore, false), (player, click) -> {
            if (!click.isShiftClick()) {
               deny(player);
               player.sendActionBar(this.style.name("Shift-click to apply " + preset.label(), false));
               return;
            }

            this.plugin.settings().apply(preset);
            this.plugin.applyAll();
            this.plugin.getLogger().info(player.getName() + " applied the " + preset.label() + " preset.");
            player.sendActionBar(this.style.name(preset.label() + " preset applied", true));
            sound(player, 1.4F);
         });
      }

      this.button(22, Items.icon(Material.ARROW, this.style.name("Back", true), List.of(), false), (player, click) -> {
         sound(player, 0.9F);
         this.open(new HubMenu(this.plugin, this.viewer));
      });
   }
}
