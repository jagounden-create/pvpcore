package com.pvpcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SmallModulesTest {
   @Test
   void combatTagBlocksCommandsWithOrWithoutPrefix() {
      List<String> blocked = List.of("spawn", "home");
      assertTrue(CombatTagModule.blocked("/spawn", blocked));
      assertTrue(CombatTagModule.blocked("/SPAWN now", blocked));
      assertTrue(CombatTagModule.blocked("/essentials:home base", blocked));
      assertTrue(CombatTagModule.blocked("//home", blocked));
      assertFalse(CombatTagModule.blocked("/spawner", blocked));
      assertFalse(CombatTagModule.blocked("/msg spawn hi", blocked));
      assertFalse(CombatTagModule.blocked("/", blocked));
   }

   @Test
   void bedrockBuffIsOneMultiplier() {
      assertEquals(1.02, WeaponsModule.bedrockMultiplier(2.0), 1.0E-12);
      assertEquals(1.0, WeaponsModule.bedrockMultiplier(-5.0), 1.0E-12);
      assertTrue(WeaponsModule.swordOrAxe(Material.NETHERITE_SWORD));
      assertTrue(WeaponsModule.swordOrAxe(Material.WOODEN_AXE));
      assertFalse(WeaponsModule.swordOrAxe(Material.MACE));
      assertFalse(WeaponsModule.swordOrAxe(Material.NETHERITE_PICKAXE));
   }

   @Test
   void onlyHitParticlesCountAsHitParticles() {
      assertTrue(ParticleModule.hitParticle("minecraft:crit"));
      assertTrue(ParticleModule.hitParticle("minecraft:enchanted_hit"));
      assertTrue(ParticleModule.hitParticle("minecraft:sweep_attack"));
      assertFalse(ParticleModule.hitParticle("minecraft:damage_indicator"));
      assertFalse(ParticleModule.hitParticle("minecraft:gust_emitter_large"));
   }

   @Test
   void xpNeverOverflows() {
      assertEquals(Integer.MAX_VALUE, ClumpsModule.add(Integer.MAX_VALUE, 5));
      assertEquals(12, ClumpsModule.add(5, 7));
   }

   @Test
   void spearChargesAreHeldToANormalHitWhileGlidingThenScaledThenCapped() {
      double none = Double.POSITIVE_INFINITY;
      assertEquals(20.0, WeaponsModule.chargeDamage(20.0, none, 1.0, none), 1.0E-9);
      assertEquals(10.0, WeaponsModule.chargeDamage(20.0, none, 0.5, none), 1.0E-9);
      assertEquals(15.0, WeaponsModule.chargeDamage(40.0, none, 1.0, 15.0), 1.0E-9);
      // gliding: a normal hit's 9 damage, then halved
      assertEquals(4.5, WeaponsModule.chargeDamage(40.0, 9.0, 0.5, none), 1.0E-9);
      assertEquals(0.0, WeaponsModule.chargeDamage(-3.0, none, 1.0, none), 1.0E-9);
   }

   @Test
   void ghostChecksStayInsideWhatTheServerSends() {
      // configured 32, players tracked to 48 blocks, 10 chunk view: 32
      assertEquals(32.0, GhostModule.expectedRange(32.0, 48.0, 10), 1.0E-9);
      // a short tracking range wins, with a margin
      assertEquals(20.0, GhostModule.expectedRange(32.0, 24.0, 10), 1.0E-9);
      // a 2 chunk client view distance: only the chunk around them is certain
      assertEquals(16.0, GhostModule.expectedRange(32.0, 48.0, 2), 1.0E-9);
      assertEquals(0.0, GhostModule.expectedRange(32.0, 48.0, 0), 1.0E-9);
   }

   @Test
   void healthLinesChangeOnlyWithWhatIsShown() {
      assertEquals(HealthModule.key(17.2, 0.0), HealthModule.key(17.9, 0.0), "both show 18");
      assertTrue(HealthModule.key(17.0, 0.0) != HealthModule.key(18.0, 0.0));
      assertTrue(HealthModule.key(20.0, 4.0) != HealthModule.key(20.0, 0.0));
      assertTrue(HealthModule.key(4.0, 20.0) != HealthModule.key(20.0, 4.0));
      assertEquals(HealthModule.key(0.0, 0.0), HealthModule.key(-1.0, -1.0));
   }

   @Test
   void chunkKeysMatchPaper() {
      for (int[] chunk : new int[][]{{0, 0}, {5, -3}, {-1, -1}, {-1875000, 1875000}}) {
         assertEquals(org.bukkit.Chunk.getChunkKey(chunk[0], chunk[1]), GhostModule.chunkKey(chunk[0], chunk[1]));
      }
   }
}
