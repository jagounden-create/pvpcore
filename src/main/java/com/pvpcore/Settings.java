package com.pvpcore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * config.yml, read into memory. A config.yml with a syntax error is never overwritten: the last good settings stay
 * in use, changes made in the menu apply but are not written, and the admin is told which line to fix.
 */
public final class Settings {
   /** Settings older versions had that nothing reads any more. */
   static final List<String> REMOVED = List.of("pots.anti-double-pot", "health.damage-indicators", "shields.instant-block", "pots.double-click");
   /** Old single-value settings whose path is now a section. */
   static final List<String> REPLACED_BY_SECTION = List.of("pearls.cooldown");
   static final String DEFAULT_ACCENT = "#7DD3FC";
   private final PvPCore plugin;
   private final File file;
   private YamlConfiguration config;
   private YamlConfiguration defaults;
   private boolean writable;
   private boolean saveQueued;
   private String loadError;
   /** By {@link Feature#ordinal()}: read by every event handler, so plain arrays rather than maps. */
   private final boolean[] enabled = new boolean[Feature.values().length];
   private final double[] values = new double[Feature.values().length];
   private final Map<String, String> messages = new HashMap<>();
   private List<String> crystalWorlds = List.of();
   private List<String> blockedCommands = List.of();
   private boolean killOnLogout = true;
   private String menuTitle = "PvP Settings";
   private String accent = DEFAULT_ACCENT;
   private Material filler = Material.BLACK_STAINED_GLASS_PANE;
   private boolean smallCaps = true;
   private Tuning tuning = Tuning.DEFAULTS;

   /**
    * Fine-tuning that has no switch of its own, read from config.yml.
    *
    * @param windStopMinFall    how fast (blocks per tick) a player must be falling for a wind charge stop
    * @param windStopMinPitch   how far down (degrees) they must look
    * @param windStopCooldown   ticks between stops
    * @param windStopHorizontal how much sideways speed a stop keeps (0 to 1)
    * @param windStopConsume    whether a stop uses up the wind charge
    * @param windJumpMinPitch   how far down a player must look for an instant wind jump
    * @param windJumpMaxHeight  how high above the ground (blocks) a wind jump still bursts at the feet
    * @param ghostRange         players this close (blocks) are expected to see each other
    * @param pearlRefund        whether a pearl stopped by the anti-glitch is given back
    */
   public record Tuning(
      double windStopMinFall,
      double windStopMinPitch,
      int windStopCooldown,
      double windStopHorizontal,
      boolean windStopConsume,
      double windJumpMinPitch,
      double windJumpMaxHeight,
      double ghostRange,
      boolean pearlRefund
   ) {
      public static final Tuning DEFAULTS = new Tuning(0.2, 60.0, 20, 0.5, true, 75.0, 2.0, 32.0, true);
   }

   Settings(PvPCore plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "config.yml");
      for (Feature feature : Feature.values()) {
         this.enabled[feature.ordinal()] = feature.fallback();
         this.values[feature.ordinal()] = feature.value() == null ? 0.0 : feature.value().fallback();
      }
   }

   /** @return true when config.yml was read, false when it has an error and the previous settings were kept */
   public boolean load() {
      this.defaults = readDefaults();
      if (!this.file.isFile()) {
         this.plugin.saveResource("config.yml", false);
      }

      YamlConfiguration disk = new YamlConfiguration();

      try {
         disk.load(this.file);
      } catch (IOException | InvalidConfigurationException e) {
         this.loadError = firstLine(e.getMessage());
         this.writable = false;
         this.plugin.getLogger().severe("config.yml could not be read, so it was NOT changed or overwritten: " + this.loadError);
         if (this.config == null) {
            this.plugin.getLogger().severe("Running on the default settings until config.yml is fixed and /pvpcore reload is used.");
            this.config = new YamlConfiguration();
            this.config.setDefaults(this.defaults);
            this.read();
         } else {
            this.plugin.getLogger().severe("Keeping the settings that were loaded before. Fix the file, then /pvpcore reload.");
         }

         return false;
      }

      this.loadError = null;
      this.writable = true;
      disk.setDefaults(this.defaults);
      boolean changed = false;

      for (String path : REMOVED) {
         if (disk.isSet(path)) {
            disk.set(path, null);
            changed = true;
         }
      }

      for (String path : REPLACED_BY_SECTION) {
         if (disk.isSet(path) && !disk.isConfigurationSection(path)) {
            disk.set(path, null);
            changed = true;
         }
      }

      boolean missing = false;
      for (String path : this.defaults.getKeys(true)) {
         if (!this.defaults.isConfigurationSection(path) && !disk.isSet(path)) {
            missing = true;
            break;
         }
      }

      if (missing) {
         disk.options().copyDefaults(true);
         changed = true;
      }

      changed |= refreshComments(disk, this.defaults);
      this.config = disk;
      if (changed) {
         this.saveNow();
         disk.options().copyDefaults(false);
      }

      if (missing) {
         this.plugin.getLogger().info("Added this version's new settings to config.yml.");
      }

      this.read();
      return true;
   }

   private void read() {
      YamlConfiguration config = this.config;

      for (Feature feature : Feature.values()) {
         this.enabled[feature.ordinal()] = config.getBoolean(feature.path(), feature.fallback());
         Feature.Value value = feature.value();
         if (value != null) {
            double raw = config.getDouble(value.path(), value.fallback());
            double clamped = value.clamp(raw);
            if (Math.abs(raw - clamped) > 1.0E-6) {
               this.plugin.getLogger().warning(value.path() + " is " + raw + ", outside " + value.min() + " to " + value.max() + " - using " + clamped + ".");
            }

            this.values[feature.ordinal()] = clamped;
         }
      }

      this.crystalWorlds = List.copyOf(config.getStringList("crystal-pvp.worlds"));
      List<String> commands = new ArrayList<>();
      for (String command : config.getStringList("combat-tag.blocked-commands")) {
         String cleaned = command.trim().toLowerCase(Locale.ROOT);
         while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
         }

         if (!cleaned.isEmpty()) {
            commands.add(cleaned);
         }
      }

      this.blockedCommands = List.copyOf(commands);
      this.killOnLogout = config.getBoolean("combat-tag.kill-on-logout", true);
      this.menuTitle = config.getString("menu.title", "PvP Settings");
      String accent = config.getString("menu.accent", DEFAULT_ACCENT);
      this.accent = accent != null && TextColor.fromHexString(accent) != null ? accent : DEFAULT_ACCENT;
      String fillerName = config.getString("menu.filler");
      Material filler = fillerName == null ? null : Material.matchMaterial(fillerName);
      this.filler = filler != null && (filler.isAir() || filler.isItem()) ? filler : Material.BLACK_STAINED_GLASS_PANE;
      this.smallCaps = config.getBoolean("menu.small-caps", true);
      Tuning d = Tuning.DEFAULTS;
      this.tuning = new Tuning(
         range(config.getDouble("mace.wind-stop.min-fall-speed", d.windStopMinFall()), 0.0, 4.0),
         range(config.getDouble("mace.wind-stop.min-pitch", d.windStopMinPitch()), -90.0, 90.0),
         (int)Math.round(range(config.getDouble("mace.wind-stop.cooldown-seconds", d.windStopCooldown() / 20.0), 0.0, 60.0) * 20.0),
         range(config.getDouble("mace.wind-stop.keep-horizontal", d.windStopHorizontal()), 0.0, 1.0),
         config.getBoolean("mace.wind-stop.consume", d.windStopConsume()),
         range(config.getDouble("mace.wind-jump.min-pitch", d.windJumpMinPitch()), -90.0, 90.0),
         range(config.getDouble("mace.wind-jump.max-height", d.windJumpMaxHeight()), 0.0, 6.0),
         range(config.getDouble("ghost-fixes.scanner.range", d.ghostRange()), 8.0, 128.0),
         config.getBoolean("pearls.anti-glitch.refund", d.pearlRefund())
      );
      this.messages.clear();
      ConfigurationSection section = config.getConfigurationSection("messages");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            this.messages.put(key, section.getString(key, ""));
         }
      }
   }

   private static double range(double value, double min, double max) {
      return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
   }

   private YamlConfiguration readDefaults() {
      InputStream stream = this.plugin.getResource("config.yml");
      if (stream == null) {
         return new YamlConfiguration();
      }

      try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
         return YamlConfiguration.loadConfiguration(reader);
      } catch (IOException e) {
         return new YamlConfiguration();
      }
   }

   static boolean refreshComments(YamlConfiguration config, YamlConfiguration defaults) {
      boolean changed = false;
      List<String> header = defaults.options().getHeader();
      if (!header.equals(config.options().getHeader())) {
         config.options().setHeader(header);
         changed = true;
      }

      for (String path : defaults.getKeys(true)) {
         if (config.isSet(path)) {
            List<String> comments = defaults.getComments(path);
            if (!comments.equals(config.getComments(path))) {
               config.setComments(path, comments);
               changed = true;
            }

            List<String> inline = defaults.getInlineComments(path);
            if (!inline.equals(config.getInlineComments(path))) {
               config.setInlineComments(path, inline);
               changed = true;
            }
         }
      }

      return changed;
   }

   private static String firstLine(String message) {
      if (message == null) {
         return "unknown error";
      }

      String trimmed = message.trim();
      int newline = trimmed.indexOf('\n');
      return newline < 0 ? trimmed : trimmed.substring(0, newline) + " ...";
   }

   // ------------------------------------------------------------------ saving

   /** Writes config.yml at the end of this tick, once, however many changes were made. */
   private void requestSave() {
      if (!this.writable) {
         return;
      }

      if (!this.saveQueued) {
         this.saveQueued = true;
         if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(this.plugin, this::flush);
         }
      }
   }

   public void flush() {
      if (this.saveQueued) {
         this.saveQueued = false;
         this.saveNow();
      }
   }

   private void saveNow() {
      if (!this.writable) {
         return;
      }

      try {
         this.config.save(this.file);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
      }
   }

   // ------------------------------------------------------------------ switches

   public boolean on(Feature feature) {
      return this.enabled[feature.ordinal()];
   }

   public double value(Feature feature) {
      Feature.Value value = feature.value();
      return value == null ? 0.0 : this.values[feature.ordinal()];
   }

   public int ticks(Feature feature) {
      return (int)Math.round(this.value(feature));
   }

   public void set(Feature feature, boolean on) {
      this.enabled[feature.ordinal()] = on;
      this.config.set(feature.path(), on);
      this.requestSave();
   }

   public double setValue(Feature feature, double amount) {
      Feature.Value value = feature.value();
      if (value == null) {
         return 0.0;
      }

      double clamped = value.clamp(amount);
      this.values[feature.ordinal()] = clamped;
      this.config.set(value.path(), value.unit().whole() ? (Object)(int)clamped : (Object)clamped);
      this.requestSave();
      return clamped;
   }

   public void resetAll() {
      for (Feature feature : Feature.values()) {
         this.enabled[feature.ordinal()] = feature.fallback();
         this.config.set(feature.path(), feature.fallback());
         Feature.Value value = feature.value();
         if (value != null) {
            this.values[feature.ordinal()] = value.fallback();
            this.config.set(value.path(), value.unit().whole() ? (Object)(int)value.fallback() : (Object)value.fallback());
         }
      }

      this.requestSave();
   }

   public void apply(Preset preset) {
      this.resetAll();
      preset.configure(this);
   }

   public int enabledCount() {
      int count = 0;
      for (Feature feature : Feature.values()) {
         if (this.on(feature)) {
            count++;
         }
      }

      return count;
   }

   public int enabledCount(Feature.Category category) {
      int count = 0;
      for (Feature feature : category.features()) {
         if (this.on(feature)) {
            count++;
         }
      }

      return count;
   }

   // ------------------------------------------------------------------ extras

   public boolean writable() {
      return this.writable;
   }

   public String loadError() {
      return this.loadError;
   }

   public boolean crystalWorld(World world) {
      List<String> worlds = this.crystalWorlds;
      if (worlds.isEmpty()) {
         return world.getEnvironment() != World.Environment.THE_END;
      }

      for (String name : worlds) {
         if (name.equalsIgnoreCase(world.getName())) {
            return true;
         }
      }

      return false;
   }

   public List<String> blockedCommands() {
      return this.blockedCommands;
   }

   public boolean killOnLogout() {
      return this.killOnLogout;
   }

   public String menuTitle() {
      return this.menuTitle;
   }

   public String accent() {
      return this.accent;
   }

   public Material filler() {
      return this.filler;
   }

   public boolean smallCaps() {
      return this.smallCaps;
   }

   public Tuning tuning() {
      return this.tuning;
   }

   /** A message from config.yml, falling back to the built-in one. Empty means "send nothing". */
   public String message(String key) {
      String text = this.messages.get(key);
      if (text == null) {
         text = this.defaults == null ? "" : this.defaults.getString("messages." + key, "");
      }

      return text == null ? "" : text;
   }
}
