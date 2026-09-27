package com.pvpcore.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuleTest {
   @Test
   void emptyRuleDoesNothing() {
      Rule rule = Rule.empty(Material.COBWEB);
      assertFalse(rule.active());
      assertFalse(rule.changesDamage());
      assertFalse(rule.hasCooldown());
      assertFalse(rule.limitsBlocks());
      assertTrue(rule.appliesIn(null) || rule.worlds().isEmpty());
   }

   @Test
   void valuesAreClampedAndCleaned() {
      Rule rule = new Rule(Material.MACE, -1, Double.NaN, 1.0E9, false, -5, Integer.MAX_VALUE, null);
      assertEquals(0.0, rule.damage());
      assertEquals(0.0, rule.maxDamage());
      assertEquals(Rule.MAX_COOLDOWN, rule.cooldown());
      assertEquals(0, rule.chunkLimit());
      assertEquals(Rule.MAX_LIMIT, rule.playerLimit());
      assertTrue(rule.worlds().isEmpty());
      assertEquals(1.0, new Rule(Material.MACE, Double.POSITIVE_INFINITY, 0, 0, false, 0, 0, List.of()).damage());
   }

   @Test
   void stepsDoNotDrift() {
      Rule rule = Rule.empty(Material.NETHERITE_SWORD);
      for (int i = 0; i < 4; i++) {
         rule = rule.withDamage(rule.damage() - 0.05);
      }

      assertEquals(0.8, rule.damage(), 0.0);
      assertTrue(rule.changesDamage());
      assertEquals(15 * 20, rule.withCooldown(15).cooldownTicks());
   }

   @Test
   void weaponsStartCooldownsOnHit() {
      assertTrue(Rule.weapon(Material.NETHERITE_SWORD));
      assertTrue(Rule.weapon(Material.DIAMOND_AXE));
      assertTrue(Rule.weapon(Material.MACE));
      assertTrue(Rule.weapon(Material.TRIDENT));
      assertFalse(Rule.weapon(Material.ENDER_PEARL));
      assertFalse(Rule.weapon(Material.GOLDEN_APPLE));
   }

   @Test
   void readsAndWritesYaml() {
      YamlConfiguration yaml = new YamlConfiguration();
      ConfigurationSection entry = yaml.createSection("rules.COBWEB");
      Rule rule = Rule.empty(Material.COBWEB).withPlayerLimit(8).withChunkLimit(32).withCooldown(0.5).withWorlds(List.of("arena"));
      Rules.write(entry, rule);
      assertNull(entry.get("damage"), "neutral values are not written");
      assertNull(entry.get("disabled"));
      assertEquals(rule, Rules.read(Material.COBWEB, entry));
   }

   @Test
   void theExamplesInRulesYmlWork() throws IOException, InvalidConfigurationException {
      String text;
      try (InputStream in = RuleTest.class.getClassLoader().getResourceAsStream("rules.yml")) {
         text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
      assertFalse(shipped.getBoolean("affect-mobs"));
      assertTrue(shipped.isConfigurationSection("rules"));
      assertTrue(shipped.getConfigurationSection("rules").getKeys(false).isEmpty(), "no rules are active out of the box");

      // Un-comment the example block and make sure it is valid YAML with valid items.
      List<String> example = new ArrayList<>();
      boolean inside = false;
      for (String line : text.split("\n")) {
         if (line.startsWith("# rules:")) {
            inside = true;
         }

         if (inside && line.startsWith("#")) {
            example.add(line.length() > 2 ? line.substring(2) : "");
         }
      }

      YamlConfiguration parsed = new YamlConfiguration();
      parsed.loadFromString(String.join("\n", example));
      ConfigurationSection rules = parsed.getConfigurationSection("rules");
      assertTrue(rules.getKeys(false).size() >= 5);
      for (String key : rules.getKeys(false)) {
         Material material = Material.matchMaterial(key);
         assertTrue(material != null, key + " is not a material");
         assertTrue(Rules.read(material, rules.getConfigurationSection(key)).active(), key + " example does nothing");
      }
   }
}
