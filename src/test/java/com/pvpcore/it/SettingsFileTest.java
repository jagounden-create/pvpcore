package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsFileTest extends PluginTest {
   File config() {
      return new File(this.plugin.getDataFolder(), "config.yml");
   }

   @Test
   void aBrokenConfigIsNeverOverwritten() throws IOException {
      this.set(Feature.SHIELD_DELAY, 150);
      this.ticks(1);
      String broken = "shields:\n  delay: [unclosed\n";
      Files.writeString(this.config().toPath(), broken);

      assertFalse(this.plugin.reload(), "reload reports the error");
      assertEquals(150.0, this.plugin.settings().value(Feature.SHIELD_DELAY), "the last good settings stay");
      this.plugin.settings().set(Feature.SHIELD_STUN, false);
      this.ticks(2);
      assertEquals(broken, Files.readString(this.config().toPath()), "the broken file was not touched");

      Files.writeString(this.config().toPath(), "shields:\n  delay:\n    ms: 90\n");
      assertTrue(this.plugin.reload());
      assertEquals(90.0, this.plugin.settings().value(Feature.SHIELD_DELAY));
   }

   @Test
   void missingSettingsAreAddedAndYourValuesKept() throws IOException {
      Files.writeString(this.config().toPath(), "shields:\n  delay:\n    enabled: true\n    ms: 60\npots:\n  double-click: true\npearls:\n  cooldown: 14\n");
      assertTrue(this.plugin.reload());
      YamlConfiguration saved = YamlConfiguration.loadConfiguration(this.config());
      assertEquals(60, saved.getInt("shields.delay.ms"), "your value is kept");
      assertFalse(saved.contains("pots.double-click"), "settings nothing reads any more are dropped");
      assertTrue(saved.isConfigurationSection("pearls.cooldown"), "the old pearl cooldown number became the new section");
      for (Feature feature : Feature.values()) {
         assertTrue(saved.isSet(feature.path()), feature.path() + " was added");
      }

      assertTrue(Files.readString(this.config().toPath()).contains("# "), "comments are written back");
   }

   @Test
   void outOfRangeValuesAreClamped() throws IOException {
      Files.writeString(this.config().toPath(), "combat:\n  vertical-knockback:\n    lift: 9\n");
      assertTrue(this.plugin.reload());
      assertEquals(Feature.VERTICAL_KNOCKBACK.value().max(), this.plugin.settings().value(Feature.VERTICAL_KNOCKBACK));
   }

   @Test
   void aBrokenRulesFileIsNeverOverwritten() throws IOException {
      File rules = new File(this.plugin.getDataFolder(), "rules.yml");
      String broken = "rules:\n  COBWEB: [oops\n";
      Files.writeString(rules.toPath(), broken);
      assertFalse(this.plugin.reload());
      this.plugin.rules().put(com.pvpcore.rules.Rule.empty(org.bukkit.Material.TNT).withDisabled(true));
      this.ticks(2);
      assertEquals(broken, Files.readString(rules.toPath()));
   }
}
