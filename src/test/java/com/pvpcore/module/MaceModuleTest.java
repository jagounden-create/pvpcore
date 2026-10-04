package com.pvpcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Settings;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class MaceModuleTest {
   @Test
   void smashBonusFollowsVanilla() {
      assertEquals(0.0, MaceModule.smashBonus(1.5, 0));
      assertEquals(8.0, MaceModule.smashBonus(2.0, 0), 1.0E-9);
      assertEquals(12.0, MaceModule.smashBonus(3.0, 0), 1.0E-9);
      assertEquals(16.0, MaceModule.smashBonus(5.0, 0), 1.0E-9);
      assertEquals(22.0, MaceModule.smashBonus(8.0, 0), 1.0E-9);
      assertEquals(24.0, MaceModule.smashBonus(10.0, 0), 1.0E-9);
      // Density adds half a point per level per block
      assertEquals(24.0 + 0.5 * 3 * 10, MaceModule.smashBonus(10.0, 3), 1.0E-9);
   }

   @Test
   void fallSafetyCoversOnlyTheSameShortFall() {
      // swallowed hit at 6 blocks fallen, landed 2 blocks lower in the same fall
      assertTrue(MaceModule.covers(6.0, 70.0, 8.0, 68.0, 5));
      // landed long after
      assertFalse(MaceModule.covers(6.0, 70.0, 8.0, 68.0, MaceModule.ATTEMPT_WINDOW_TICKS + 1));
      // fell a long way further after the attempt: that part is real fall damage
      assertFalse(MaceModule.covers(6.0, 90.0, 26.0, 70.0, 20));
      // a new, separate fall after the smash reset the fall distance
      assertFalse(MaceModule.covers(6.0, 70.0, 3.0, 67.0, 10));
      // a separate drop that happens to be about as long: the heights don't line up
      assertFalse(MaceModule.covers(2.0, 70.0, 5.0, 72.0, 30));
   }

   @Test
   void windStopOnlyWhileFallingAndLookingDown() {
      Settings.Tuning tuning = Settings.Tuning.DEFAULTS; // falling 0.2+ blocks a tick, looking 60+ degrees down
      assertTrue(MaceModule.canStop(-0.8, 80.0F, false, tuning));
      assertTrue(MaceModule.canStop(-0.2, 60.0F, false, tuning));
      assertFalse(MaceModule.canStop(-0.8, 80.0F, true, tuning), "standing");
      assertFalse(MaceModule.canStop(-0.1, 80.0F, false, tuning), "barely falling");
      assertFalse(MaceModule.canStop(0.5, 80.0F, false, tuning), "going up");
      assertFalse(MaceModule.canStop(-0.8, 30.0F, false, tuning), "looking ahead");
   }

   @Test
   void windStopKeepsTheLiftAndPartOfTheSidewaysSpeed() {
      Vector after = MaceModule.stopVelocity(new Vector(0.4, -1.2, -0.2), 0.1, 0.5);
      assertEquals(0.2, after.getX(), 1.0E-9);
      assertEquals(0.1, after.getY(), 1.0E-9);
      assertEquals(-0.1, after.getZ(), 1.0E-9);
   }
}
