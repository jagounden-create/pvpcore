package com.pvpcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnockbackModuleTest {
   @Test
   void landsWithinPing() {
      assertTrue(KnockbackModule.landsWithin(0.05, 0.0, 1));
      assertFalse(KnockbackModule.landsWithin(Double.POSITIVE_INFINITY, -1.0, 8));
      // falling fast from 1.5 blocks lands inside 3 ticks
      assertTrue(KnockbackModule.landsWithin(1.5, -0.5, 3));
      // rising at the top of a jump from 2 blocks up does not
      assertFalse(KnockbackModule.landsWithin(2.0, 0.3, 3));
   }

   @Test
   void liftMatchesVanillaAtVanillaCap() {
      // vanilla: min(0.4, vy / 2 + strength)
      assertEquals(Math.min(0.4, -0.0784 / 2 + 0.4), KnockbackModule.liftFor(-0.0784, 0.4, 0.4), 1.0E-12);
      assertEquals(0.45, KnockbackModule.liftFor(0.2, 0.5, 0.45), 1.0E-12);
   }
}
