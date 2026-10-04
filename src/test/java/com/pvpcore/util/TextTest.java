package com.pvpcore.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TextTest {
   @Test
   void smallCapsLeavesTagsAlone() {
      assertEquals("<#FF0000>ʜɪ", Text.caps("<#FF0000>Hi"));
      assertEquals("ᴀ1ʙ", Text.caps("a1B"));
   }

   @Test
   void trimsNumbers() {
      assertEquals("0.45", Text.trim(0.45));
      assertEquals("5", Text.trim(5.0));
      assertEquals("0", Text.trim(-0.0001));
      assertEquals("0.1", Text.trim(0.1000001));
   }

   @Test
   void points() {
      assertEquals("17", Text.points(16.2));
      assertEquals("0", Text.points(-3));
   }

   @Test
   void placeholdersAreNeverParsedAsTags() {
      assertEquals("hello <red>you", Text.plain(Text.mm("hello <name>", Map.of("name", "<red>you"))));
   }

   @Test
   void escapedTextStaysLiteral() {
      assertEquals("a <b> c", Text.plain(Text.mm(Text.escape("a <b> c"))));
   }
}
