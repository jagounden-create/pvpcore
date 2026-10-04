package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;

/** Arrow cleanup. */
class GeneralGameplayTest extends PluginTest {
   /** MockBukkit's arrow can't tell whether it is stuck in a block; this one is. */
   static final class StuckArrow extends ArrowMock {
      StuckArrow(ServerMock server) {
         super(server, UUID.randomUUID());
      }

      @Override
      public boolean isInBlock() {
         return true;
      }
   }

   StuckArrow land() {
      StuckArrow arrow = new StuckArrow(this.server);
      arrow.setLocation(new Location(this.world, 0.5, 64.0, 0.5));
      this.server.registerEntity(arrow);
      this.call(new ProjectileHitEvent(arrow, null, this.world.getBlockAt(0, 63, 0), BlockFace.UP));
      return arrow;
   }

   @Test
   void landedArrowsVanishAfterTheSetTime() {
      this.set(Feature.ARROW_CLEANUP, 2.0); // 40 ticks
      StuckArrow first = this.land();
      this.ticks(20);
      StuckArrow second = this.land();
      this.ticks(15);
      assertTrue(first.isValid(), "not yet");
      this.ticks(15);
      assertFalse(first.isValid(), "gone after 2 seconds");
      assertTrue(second.isValid(), "landed a second later");
      this.ticks(20);
      assertFalse(second.isValid());
   }

   @Test
   void cleanupOffKeepsArrows() {
      this.set(Feature.ARROW_CLEANUP, false);
      StuckArrow arrow = this.land();
      this.ticks(1300);
      assertTrue(arrow.isValid());
   }
}
