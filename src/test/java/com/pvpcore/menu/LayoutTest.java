package com.pvpcore.menu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LayoutTest {
   @Test
   void oddCountsSitAroundTheMiddle() {
      assertArrayEquals(new int[]{13}, Layout.centered(9, 1));
      assertArrayEquals(new int[]{12, 13, 14}, Layout.centered(9, 3));
      assertArrayEquals(new int[]{11, 12, 13, 14, 15}, Layout.centered(9, 5));
      assertArrayEquals(new int[]{10, 11, 12, 13, 14, 15, 16}, Layout.centered(9, 7));
   }

   @Test
   void evenCountsMirrorAroundAnEmptyMiddle() {
      assertArrayEquals(new int[]{3, 5}, Layout.centered(0, 2));
      assertArrayEquals(new int[]{19, 20, 21, 23, 24, 25}, Layout.centered(18, 6));
   }

   @Test
   void rejectsRowsThatCannotFit() {
      assertThrows(IllegalArgumentException.class, () -> Layout.centered(0, 9));
   }

   @Test
   void rulesPageSlotsStayInsideTheBorder() {
      for (int i = 0; i < RulesMenu.PER_PAGE; i++) {
         int slot = RulesMenu.slot(i);
         int row = slot / 9;
         int column = slot % 9;
         org.junit.jupiter.api.Assertions.assertTrue(row >= 1 && row <= 4 && column >= 1 && column <= 7, "slot " + slot);
      }
   }
}
