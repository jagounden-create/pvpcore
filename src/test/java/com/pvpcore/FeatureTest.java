package com.pvpcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeatureTest {
   @Test
   void idsAndPathsAreUnique() {
      Set<String> ids = new HashSet<>();
      Set<String> paths = new HashSet<>();
      for (Feature feature : Feature.values()) {
         assertTrue(ids.add(feature.id()), "duplicate id " + feature.id());
         assertTrue(paths.add(feature.path()), "duplicate path " + feature.path());
         if (feature.value() != null) {
            assertTrue(paths.add(feature.value().path()), "duplicate path " + feature.value().path());
         }
      }
   }

   @Test
   void noPathIsInsideAnother() {
      // "a.b" as a switch and "a.b.c" as another would make YAML overwrite one with the other.
      Set<String> all = new HashSet<>();
      for (Feature feature : Feature.values()) {
         all.add(feature.path());
         if (feature.value() != null) {
            all.add(feature.value().path());
         }
      }

      for (String path : all) {
         for (String other : all) {
            assertTrue(path.equals(other) || !other.startsWith(path + "."), path + " is a parent of " + other);
         }
      }
   }

   @Test
   void defaultsSitInsideTheirRangeOnAStep() {
      for (Feature feature : Feature.values()) {
         Feature.Value value = feature.value();
         if (value == null) {
            continue;
         }

         assertTrue(value.min() <= value.fallback() && value.fallback() <= value.max(), feature + " default outside range");
         assertTrue(value.step() > 0, feature + " step");
         assertEquals(value.fallback(), value.clamp(value.fallback()), 1.0E-12, feature + " default is not a clean value");
         if (value.unit().whole()) {
            assertEquals(Math.rint(value.step()), value.step(), feature + " whole unit with fractional step");
         }
      }
   }

   @Test
   void everyPageFitsTheMenu() {
      for (Feature.Section section : Feature.Section.values()) {
         int size = section.features().size();
         assertTrue(size >= 1 && size <= 7, section + " has " + size + " switches; a row holds 1 to 7");
      }

      for (Feature.Category category : Feature.Category.values()) {
         int sections = category.sections().size();
         // top spacer + 2 rows per section + navigation must fit in 6 rows
         assertTrue(sections >= 1 && 2 + 2 * sections <= 6, category + " needs " + (2 + 2 * sections) + " rows");
      }
   }

   @Test
   void iconsAreDistinctOnEachPageAndNeverLookLikeNavigation() {
      for (Feature.Category category : Feature.Category.values()) {
         Set<org.bukkit.Material> icons = new HashSet<>();
         for (Feature feature : category.features()) {
            assertTrue(icons.add(feature.icon()), category + " uses " + feature.icon() + " twice");
            assertTrue(feature.icon() != org.bukkit.Material.ARROW, feature + " looks like the Back button");
            assertTrue(!feature.icon().name().endsWith("_STAINED_GLASS_PANE"), feature + " looks like a status pane");
         }
      }
   }

   @Test
   void lookupsRoundTrip() {
      for (Feature feature : Feature.values()) {
         assertEquals(feature, Feature.byId(feature.id()));
         assertEquals(feature, Feature.byId(feature.name()));
      }

      for (Feature.Category category : Feature.Category.values()) {
         assertEquals(category, Feature.Category.byId(category.id()));
      }

      for (Preset preset : Preset.values()) {
         assertEquals(preset, Preset.byId(preset.id()));
      }

      assertEquals(null, Feature.byId("nope"));
   }

   @Test
   void clampNeverDrifts() {
      Feature.Value lift = Feature.VERTICAL_KNOCKBACK.value();
      double value = lift.min();
      for (int i = 0; i < 100; i++) {
         value = lift.clamp(value + lift.step());
      }

      assertEquals(lift.max(), value, 0.0);
      for (int i = 0; i < 100; i++) {
         value = lift.clamp(value - lift.step());
      }

      assertEquals(lift.min(), value, 0.0);
      assertEquals(lift.fallback(), lift.clamp(Double.NaN));
      assertEquals(20.0, Feature.HIT_DELAY.value().clamp(20.4));
   }

   @Test
   void unitsFormat() {
      assertEquals("20 ticks (1s)", Feature.Unit.TICKS.format(20));
      assertEquals("1 tick (0.05s)", Feature.Unit.TICKS.format(1));
      assertEquals("5 ticks", Feature.Unit.TICKS.step(5));
      assertEquals("120ms", Feature.Unit.MILLIS.format(120));
      assertEquals("0.9x", Feature.Unit.FACTOR.format(0.9));
      assertEquals("2.5%", Feature.Unit.PERCENT.format(2.5));
      assertNotNull(Feature.Unit.HEARTS.format(20));
   }
}
