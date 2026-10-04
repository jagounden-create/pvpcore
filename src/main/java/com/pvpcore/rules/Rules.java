package com.pvpcore.rules;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** rules.yml. Like config.yml, a file with a syntax error is reported and never overwritten. */
public final class Rules {
   private final JavaPlugin plugin;
   private final File file;
   private final Map<Material, Rule> rules = new EnumMap<>(Material.class);
   /** The key each rule has in rules.yml, which may be written as "cobweb" or "minecraft:cobweb". */
   private final Map<Material, String> keys = new EnumMap<>(Material.class);
   private YamlConfiguration config;
   private boolean affectMobs;
   private boolean writable;
   private boolean saveQueued;
   private String loadError;

   public Rules(JavaPlugin plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "rules.yml");
   }

   public boolean load() {
      if (!this.file.isFile()) {
         this.plugin.saveResource("rules.yml", false);
      }

      YamlConfiguration disk = new YamlConfiguration();
      try {
         disk.load(this.file);
      } catch (IOException | InvalidConfigurationException e) {
         this.writable = false;
         this.loadError = e.getMessage() == null ? "unknown error" : e.getMessage().trim().split("\n", 2)[0];
         this.plugin.getLogger().severe("rules.yml could not be read, so it was NOT changed or overwritten: " + this.loadError);
         if (this.config == null) {
            this.config = new YamlConfiguration();
         }

         return false;
      }

      this.writable = true;
      this.loadError = null;
      this.config = disk;
      this.affectMobs = disk.getBoolean("affect-mobs", false);
      this.rules.clear();
      this.keys.clear();
      ConfigurationSection section = disk.getConfigurationSection("rules");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            Material material = Rule.parseMaterial(key);
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (material == null) {
               this.plugin.getLogger().warning("rules.yml: '" + key + "' is not an item or block on this server version - skipped.");
               continue;
            }

            if (entry == null) {
               continue;
            }

            if (this.rules.containsKey(material)) {
               this.plugin.getLogger().warning("rules.yml: '" + key + "' is a second rule for " + material.name() + " - skipped.");
               continue;
            }

            this.rules.put(material, read(material, entry));
            this.keys.put(material, key);
         }
      }

      return true;
   }

   static Rule read(Material material, ConfigurationSection entry) {
      return new Rule(
         material,
         entry.getDouble("damage", 1.0),
         entry.getDouble("max-damage", 0.0),
         entry.getDouble("cooldown", 0.0),
         entry.getBoolean("disabled", false),
         entry.getInt("chunk-limit", 0),
         entry.getInt("player-limit", 0),
         entry.getStringList("worlds")
      );
   }

   static void write(ConfigurationSection entry, Rule rule) {
      entry.set("damage", Math.abs(rule.damage() - 1.0) > 1.0E-9 ? rule.damage() : null);
      entry.set("max-damage", rule.maxDamage() > 0.0 ? rule.maxDamage() : null);
      entry.set("cooldown", rule.cooldown() > 0.0 ? rule.cooldown() : null);
      entry.set("disabled", rule.disabled() ? Boolean.TRUE : null);
      entry.set("chunk-limit", rule.chunkLimit() > 0 ? rule.chunkLimit() : null);
      entry.set("player-limit", rule.playerLimit() > 0 ? rule.playerLimit() : null);
      entry.set("worlds", rule.worlds().isEmpty() ? null : rule.worlds());
   }

   public boolean writable() {
      return this.writable;
   }

   public String loadError() {
      return this.loadError;
   }

   public boolean affectMobs() {
      return this.affectMobs;
   }

   public Rule get(Material material) {
      return material == null ? null : this.rules.get(material);
   }

   public boolean isEmpty() {
      return this.rules.isEmpty();
   }

   public Collection<Rule> all() {
      return Collections.unmodifiableCollection(this.rules.values());
   }

   /** Rules sorted by item name, for the menu and /pvpcore rules. */
   public List<Rule> sorted() {
      List<Rule> sorted = new ArrayList<>(this.rules.values());
      sorted.sort((a, b) -> a.material().name().compareTo(b.material().name()));
      return sorted;
   }

   public boolean hasBlockLimits() {
      for (Rule rule : this.rules.values()) {
         if (rule.limitsBlocks()) {
            return true;
         }
      }

      return false;
   }

   private String path(Material material) {
      return "rules." + this.keys.getOrDefault(material, material.name());
   }

   public void put(Rule rule) {
      this.rules.put(rule.material(), rule);
      this.keys.putIfAbsent(rule.material(), rule.material().name());
      ConfigurationSection entry = this.config.getConfigurationSection(this.path(rule.material()));
      if (entry == null) {
         entry = this.config.createSection(this.path(rule.material()));
      }

      write(entry, rule);
      this.requestSave();
   }

   public boolean remove(Material material) {
      boolean removed = this.rules.remove(material) != null;
      this.config.set(this.path(material), null);
      this.keys.remove(material);
      // An empty "rules:" section would be dropped by YAML; keep it so the file still shows where rules go.
      if (!this.config.isConfigurationSection("rules")) {
         this.config.createSection("rules");
      }

      this.requestSave();
      return removed;
   }

   private void requestSave() {
      if (!this.writable || this.saveQueued) {
         return;
      }

      this.saveQueued = true;
      if (this.plugin.isEnabled()) {
         Bukkit.getScheduler().runTask(this.plugin, this::flush);
      }
   }

   public void flush() {
      if (!this.saveQueued) {
         return;
      }

      this.saveQueued = false;
      if (!this.writable) {
         return;
      }

      try {
         this.config.save(this.file);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Could not save rules.yml: " + e.getMessage());
      }
   }
}
