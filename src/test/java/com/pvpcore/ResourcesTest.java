package com.pvpcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.util.Text;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** config.yml, plugin.yml and rules.yml must agree with the code. */
class ResourcesTest {
   static YamlConfiguration resource(String name) throws IOException {
      try (InputStream in = ResourcesTest.class.getClassLoader().getResourceAsStream(name)) {
         assertNotNull(in, name + " missing");
         return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
      }
   }

   @Test
   void configHasEverySwitchWithTheCodeDefaults() throws IOException {
      YamlConfiguration config = resource("config.yml");
      for (Feature feature : Feature.values()) {
         assertTrue(config.isBoolean(feature.path()), feature.path() + " missing from config.yml");
         assertEquals(feature.fallback(), config.getBoolean(feature.path()), feature.path() + " default differs");
         Feature.Value value = feature.value();
         if (value != null) {
            assertTrue(config.isSet(value.path()), value.path() + " missing from config.yml");
            assertEquals(value.fallback(), config.getDouble(value.path()), 1.0E-9, value.path() + " default differs");
         }
      }
   }

   @Test
   void everyMessageTheCodeSendsExistsAndParses() throws IOException {
      YamlConfiguration config = resource("config.yml");
      ConfigurationSection messages = config.getConfigurationSection("messages");
      assertNotNull(messages);
      Pattern used = Pattern.compile("(?:actionBar|send|message|notice)\\([^\"]*\"([a-z-]+)\"");
      int found = 0;
      try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
         for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
            Matcher matcher = used.matcher(Files.readString(file));
            while (matcher.find()) {
               String key = matcher.group(1);
               found++;
               assertTrue(messages.isString(key), "messages." + key + " used in " + file.getFileName() + " but missing from config.yml");
            }
         }
      }

      assertTrue(found >= 14, "only " + found + " message uses found - is the pattern still right?");
      for (String key : messages.getKeys(false)) {
         Text.mm(messages.getString(key), Map.of("seconds", "1", "item", "x", "limit", "2", "player", "p"));
      }
   }

   @Test
   void pluginYmlDeclaresEveryPermissionAndTheCommand() throws IOException {
      YamlConfiguration plugin = resource("plugin.yml");
      assertEquals("com.pvpcore.PvPCore", plugin.getString("main"));
      assertEquals("1.21", plugin.getString("api-version"));
      assertTrue(plugin.isConfigurationSection("commands.pvpcore"));
      for (String permission : List.of("pvpcore.admin", "pvpcore.bypass.crystals", "pvpcore.bypass.combattag", "pvpcore.bypass.rules")) {
         assertTrue(plugin.isConfigurationSection("permissions." + permission), permission + " missing from plugin.yml");
      }

      assertFalse(plugin.contains("authors"), "no author branding");
      assertFalse(plugin.contains("author"), "no author branding");
   }

   @Test
   void nothingMentionsAnOldServerName() throws IOException {
      for (String name : List.of("config.yml", "plugin.yml", "rules.yml")) {
         try (InputStream in = ResourcesTest.class.getClassLoader().getResourceAsStream(name)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertFalse(text.contains("nullscape"), name);
            assertFalse(text.contains("ffacore"), name);
         }
      }

      try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
         for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
            String text = Files.readString(file).toLowerCase();
            assertFalse(text.contains("nullscape"), file.toString());
            assertFalse(text.contains("ffacore"), file.toString());
         }
      }
   }
}
