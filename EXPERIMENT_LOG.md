# IslandCraft - Equipment of Arrakis

Server 0.6.8. Stage one: models, inventory placement and animation of four tools, plus the `Arrakis_Power` stat.
Identifier form `Equipment_of_Arrakis` (asset folders), package `com.paulorchard.islandcraft.equipmentofarrakis`, jar
`IslandCraft-EquipmentOfArrakis-<version>.jar`. All in-world ids are prefixed `Arrakis_`.

Loop: `gradlew deployMod` (close the world first), `gradlew test`, headless boot `tools/headless/run.sh "wait 30"`.
Models are regenerated with `node tools/assets/make_models.js <folder holding the vanilla Common/>`.

## Verified (bytecode, assets, headless server)

- **Utility section.** `InventoryComponent$Utility` only accepts items whose `Utility.Usable` is true (the container filter
  is `item.getUtility().isUsable()`). A `Utility` block with `Usable: true` is therefore both necessary and sufficient.
- **Which hand gets a click** (`InteractionContext.forInteraction`). With a main-hand item M and an off-hand item O:
  M's priority for the type in slot MainHand is compared with O's in slot OffHand (default 0 each).
  Equal: Primary goes to M; Secondary goes to O only if M has `Utility.Compatible`. M higher: M, unless M has no
  interaction of that type (then O). M lower: O. So the three off-hand tools get right click whenever the main hand
  holds a `Compatible` item (swords, pickaxes, food) or an item with no Secondary. The Cutter Ray is not Compatible,
  has a Secondary and has priority 10: both clicks stay with it. That is how vanilla two-handed items (rifles) block the
  off-hand. The off-hand item is not cleared from the Utility slot; it just cannot be used.
- **A stat needs no code to reach existing players.** `EntityStatMap.update()` creates a value for every
  `EntityStatType` asset that the map lacks, at its InitialValue, when an entity's stat map is set up. A new asset
  `Arrakis_Power` (InitialValue 100) therefore appears on new and old characters. `ResetType: MaxValue` refills on respawn.
- **Block damage as a pickaxe.** `BlockHarvestUtils.performBlockDamage` takes the stack that does the damage. The
  vanilla pickaxe swing calls it with the held stack; the ray calls it with an unbreakable
  (`withMaxDurability(0)`) `Tool_Pickaxe_Mithril` stack, tool id "Pickaxe", `DamageScale` 0.24. Durability is skipped
  for unbreakable stacks (otherwise it is written into the hotbar slot).
- **Strongest pickaxe.** Compared every `Tool_Pickaxe_*` spec by gather type: Mithril (ItemLevel 50) matches or beats
  all others in every gather type (startup log: "does not match or beat ... none"). Onyxium ties on Rocks quality 6 but
  has Soils 0.5 against 1.0. Adamantite is below on rocks quality and ores.
- **Animation retiming works.** Item animation sets (`Server/Item/Animations`) take a per-animation `Speed`; the vanilla
  Gun uses 3. Torch `Swing_Left` is 25 frames at 60 fps = 0.41667 s; at Speed 0.2 it lasts 2.08333 s, and
  `Arrakis_Dew_Reaper_Swing` has `RunTime` 2.08333.
- **Pitch following.** The torch animation set has `Camera.Pitch` (target nodes Head and LShoulder, range -60..60), which
  rotates the left arm with the look pitch; the three tools inherit it (`Parent: Torch`).
- Headless boot: no asset errors; all four items, the stat, the animation sets, interactions and particles resolve
  (`AssetSelfCheck` logs them at start).

## Not verified (needs a client)

- How the models sit in the left hand (grip offset and orientation of the lantern and the pickaxe), and whether the
  scale values (0.5, 0.6667) are what the hand renderer uses.
- That the Z radial lists the tools and that Primary repeats while held (assumed from the Gun's 0.05 s RunTime).
- How the chosen vanilla poses look: Interact (Blood Extractor), Build (Compactor), Swing_Left (Dew Reaper) were picked
  from their keyframes (forearm straightens; hand turns 90 degrees forward; arm swing), not seen.
- The molten effect (prompt 04): how the globs look (size, spread, speed, glow), that they fall and vanish as meant, and that other players see them. Not seen on a client. See the lock-on section for the laser line.

## Cutter Ray surface effect (prompt 04)

The beam is gone, in every form: no model variants, no stat, no entities, no particle line. The Cutter Ray now shows
only a molten effect on the surface it cuts. The gun is the vanilla Pulse Rifle, with no Shoot animation.

**Verified in the assets and the jar**
- Particle systems are lists of spawners, spawned at a point with `ParticleUtil.spawnParticleEffect(id, position,
  accessor)`. A spawner has initial velocity (speed, yaw, pitch ranges), `Attractors` with a `LinearAcceleration`
  (the vanilla lava splash uses Y -7.2 for gravity), and `ParticleCollision` (`BlockType: Solid`, `Action: Expire`
  makes a particle vanish when it hits something solid).
- `Particles/Textures/Fire/Lava_Bubble2.png` is 8 frames of 32 x 32; frame 0 is a glowing lava-textured ball.
  `Particles/Textures/Basic/Glow.png` is the soft glow sprite.
- The point on the surface is where the look ray enters the targeted block (`BeamMath.entryDistance`, tested), moved
  0.08 block back toward the player so the globs start in the air.

**Built**: system `Arrakis_Cutter_Molten` with one spawner, spawned each beam tick (while firing and a block is
targeted):
- `Arrakis_Cutter_Molten_Globs`, currently set to the **100% test setting**: a constant stream, 90 globs spread over 0.2 s
  (450 a second) from each beam tick, so the streams of consecutive ticks overlap. Sizes 0.02 to 0.06, tinted orange
  (#ff6a00), shrinking to almost nothing as they fall. Let go from rest (speed 0 to 0.12, pitch -90 to -60, nothing
  moves upward) with gravity -9. The engine has no per-particle delay, so the 0.03 s pause before falling is the
  natural start from rest: after 0.03 s a glob has moved 4 mm, after 0.1 s 4.5 cm. Life 0.5 to 0.9 s; they also vanish
  on touching a solid block. Lava bubble texture, not lit by the world. To scale back, lower `TotalParticles` (and
  `MaxConcurrentParticles`).
There is no glow spot: a first version had one, and in game it appeared about a second after the globs and after the
block broke, so it was removed. Nothing is shown when no block is in range. The effect is data: change sizes, speeds, counts or the texture in the
two spawner files; `ParticleId` in `Arrakis_Cutter_Beam.json` picks the system.

**Not verified (needs a client)**: all of it visually, in particular whether particle scale 0.04 to 0.12 is the
size wanted, whether the spread looks like globs thrown off a cut, and whether a burst per tick looks continuous.

## Power recharge

`PowerRechargeSystem` (a ticking system over players) adds `ArrakisPower.RECHARGE_PER_SECOND` (10) per second, up to 100,
while the player has not tried to spend power for 0.35 s (`IDLE_NANOS`; beam ticks come every 0.1 to 0.2 s). A spend
attempt counts as use even at 0 power, so holding the button on an empty pack does not recharge. Dead players do not
recharge. It is code and not a Regenerating rule in the stat asset because a stat rule cannot tell whether the Cutter
Ray is firing. Not tested with a player: the headless server only checks that the plugin loads.

## Lock-on, laser line, globs at the beam end (replaces "Molten trail"; supersedes how the effect is spawned above)

The aim-following trail is gone (`BeamMath.trailPoints`, `MAX_LINK`, `TrailSpacing`, the per-player last point). The Cutter Ray now
locks onto a block and draws a red line to it.

**Lock-on** (`CutterBeamInteraction`, `MoltenTrail`)
- First beam tick with power left and a block within `Range` (5) under the crosshair: that block, the face the look ray entered
  (`BeamMath.entry`: distance, face normal, face centre) and the globs' point (face centre plus 0.08 along the normal) are fixed for
  the whole lock. No block in range: nothing happens and no power is spent. Later ticks pay `PowerPerSecond * RunTime` and damage the
  locked block (same call, same Mithril stand-in, same `DamageScale`); they never re-target.
- Every world tick (`MoltenTrailSystem`, now an entity-ticking system over players because adding and removing the laser entities needs
  the command buffer): the lock breaks when the block id at the locked position changed (broken or replaced, by the beam or anything
  else), or when `TargetUtil.getTargetBlock` has not been the locked block for more than `LockGraceSeconds` (0.15, in
  `Arrakis_Cutter_Beam.json`; the time counts from when the crosshair left and resets when it is back).
- A break sets `needsRelease`. While it is set, beam ticks do nothing: no damage, no power, no globs, and they do not count as use of
  power (`ArrakisPower.spend` is not called), so recharge starts. Release is told by the gap between beam ticks of any kind
  (`lastArrival`): a tick that arrives more than `EXPIRY_NANOS` (0.3 s) after the previous one is a new press and clears the flag.
  Refused ticks are recorded as arrivals (that is what lets a held button be told from a release) but not as use. The prompt asked that
  refused ticks "do not extend the idle time"; read literally that would clear the flag while the button is still down, so the idle time
  here is the power idle time. Limit: a release and re-press faster than 0.3 s counts as still held and is refused once.
- The lock also ends, without needing a release, when no beam tick has fired for 0.3 s (button released), the player dies, holds
  another item, leaves, or power reaches 0 (Creative mode breaks the block at once, which ends it the first way).
- Trying with an empty pack still counts as use (so holding the button at 0 does not recharge), as before.

**Ticks after Failed.** Verified in the jar: `SimpleInstantInteraction` runs `firstRun` and then `SimpleInteraction.tick0`, which on a
Failed state only jumps to the failure label or ends the chain; the only server code that restarts a held interaction
(`InteractionManager.tryRunHeldInteraction`) is called by the NPC systems, not for players. So for a player the next tick is started by
the client, and whether a Failed chain is started again while the button is down is not something the server can show. The beam
therefore ends refused ticks as Finished (the default), which come again after `RunTime` like every other tick; release detection
relies only on that. Not tested with a client.

**Laser line** (`CutterLaser`, `BeamSegment`, `BeamSegmentSystem`, `tools/assets/make_laser.js`) - second version
The first version (one-block rods of 3 units, red) was seen in game: far too thick, with gaps between the segments. Now:
- A white-hot core that tapers from 4 "pixels" at the barrel to 1 at the locked face, plus a translucent white glow around it that
  pulses in and out. A "pixel" is `CutterLaser.PIXEL` = 0.003 block (4 px = 0.012 block, about 0.38 model unit; the vanilla Laser_Red
  sight is 0.6 unit): the one number to change to make the whole line thicker or thinner. The glow reaches 1 to 3 pixels out on each
  side, 3 times a second.
- Entities can only be scaled evenly, so the line is a row of pieces of different scale (`BeamMath.pieces`, tested): both models
  (`Arrakis_Cutter_Laser` white opaque, `Arrakis_Cutter_Laser_Glow` white alpha 110) are a 1 x 1 x 96 unit box, a piece of width w is
  96 w long, so the pieces get shorter towards the thin end (about 9 for a 5 block line); each starts 0.85 of a length after the one
  before (15% overlap), and the last is cut to end exactly at the face. The scale of every piece is set from its width, and the glow
  pieces' scale is changed every tick (`EntityScaleComponent`, which the engine sends as a model update when it changes).
- Not known without a client, and possible causes of the gaps and the thickness seen: the model's long axis may not lie along the look
  direction (the first version would then show as slabs across the line, which is both thick and gappy); pitch may not be applied
  to the body of a model entity; 32 units to a block may not hold. `arrakis laser rig` (as a player) puts three 1.5 block rods
  ahead of you: the middle one should point away from you, the one on your right straight up, the one on your left to your right;
  `arrakis laser clear` removes them. If they do not, the model axis or rotation is what to change.
- The glow relies on the client blending a translucent texture; with an alpha test only, it is not drawn and the white core is
  seen alone (the texture alpha of 110 was chosen to fail that way).
- A broken lock now needs a gap of 0.5 s (`RELEASE_NANOS`) between beam ticks to count as a release, up from 0.3 s, so a server or
  network hiccup while the button is down cannot clear it and let the gun jump to another block.
- The pieces of the core are kept in `State.segments`, those of the glow in `State.glows`; nothing is spawned per tick.
- Entity components, hand point and safety nets: unchanged (see above: Transform, HeadRotation, UUID, Model, EntityScale, BoundingBox,
  NetworkId, Visible, Intangible, NonSerialized, `BeamSegment`; hand point eye + 0.9 forward, 0.22 right, 0.32 down; the sweeper).
- Globs: unchanged effect, thrown from the fixed point at the end of the beam, `SpawnsPerTick` bursts per world tick, tinted with the
  locked block's `getParticleColor()`. The glob is a soft white glow (`Basic/Glow.png`, additive), not the yellow lava bubble.

**Verified in the headless server (no client)**: all assets load (`model asset Arrakis_Cutter_Laser: ok`, `particle system
Arrakis_Cutter_Molten_1: ok`), a segment is accepted by the store with that component set, `BeamSegmentSystem` removes an unowned segment within a
tick, an owned one stays, and after a restart (chunk loaded again) the count is 0, so it is not saved. `arrakis laser spawn [keep] [x y z]`
and `arrakis laser count` are the commands for this (operators only). An entity in a chunk that is not loaded logs `Entity has moved into a
chunk that isn't currently loaded` every tick and is dropped, which does not happen next to a player.

**Not verified (needs a client)**: that the line is visible and red, 3 units thick reads right, the unit size of 32 per block for entity models
(from the Rat's hitbox against its model), that the segment lies along the look direction and not across it, that pitch is applied to a model
entity's body (it is set on both the transform and the head rotation), that clients interpolate the 30-per-second moves, the hand offset,
the `LockGraceSeconds` feel, release detection and the Failed behaviour above with a real held button, a second player seeing the line and globs, and
that no beam entity is left after release, power 0, slot change, death or relog (the code paths are there; only the unowned/owned cases
were run).

## Molten globs: directions, delay and intensity (latest)

- Intensity is about 50% of the test setting: `SpawnsPerTick` 5 bursts per world tick, each burst 3 parts (see below)
  of 1 or 2 globs, about 7.5 globs a tick, 225 a second (100% would be `SpawnsPerTick` 10).
- Directions: let go from rest with pitch -90 to -10 degrees (straight down to nearly sideways, never upward), any yaw,
  speed 0 to 0.5, then gravity. Some globs fall straight, some peel off outward.
- Gravity delay: the engine has no per-particle delay before gravity. The globs start from rest, so each hangs for
  about 0.03 s anyway. The variation of about +/- 0.05 s comes from starting the glob spawner three times in the
  system, with a `StartDelay` of 0, 0.04 and 0.08 s: some globs appear and start to fall later at the same spot.
  They are not visible while they wait, which is the difference from a glob that really hangs. A true hang would need a
  stationary sprite per delay followed by a falling one. Not tested with a player.

## Molten globs hang, then fall (latest; replaces the start-delay copies above)

A glob now hangs still where it is thrown off, with no gravity, for 0.5 to 0.75 s and then falls. The engine has no
per-particle delay before gravity, so `tools/assets/make_molten.js` generates four pairs of spawners: a **Hold** (a
stationary glob living for the delay, 0.5, 0.58, 0.67 and 0.75 s) and a **Fall** (the same glob, same size and
position, started after the delay with `StartDelay`: random direction from straight down to almost sideways, never
upward, speed 0 to 0.5, gravity -9, shrinking to almost nothing, life 0.5 to 0.9 s, vanishes on touching something
solid). Sizes 0.03 to 0.06. Each pair has a fixed offset (a few hundredths of a block) so the globs of a burst do not
overlap; this fixed offset replaces the random one so that a Fall starts exactly where its Hold hung. The Hold outlives
its delay by 0.04 s, hidden under the Fall.
A burst is 4 globs, so `SpawnsPerTick` 2 is about 240 globs a second (about 50%); 4 is about 100%. Globs now live
about 1.3 s instead of 0.7 s, so the number on screen at once is higher for the same rate. Not tested with a player.

## Molten globs: smaller, varied colour, cooling (latest)

Sizes are now 0.018 to 0.036 (about 0.6 of before). The four globs of a burst have different orange tints, from bright
(#ff6a00) to dark (#b84000); a Hold keeps its tint and a falling glob darkens to a quarter of it over its life
(for example #ff6a00 to #3f1a00). Whether the tint multiplies the lava texture or blends with it, and how dark the end
colour reads, are not seen in game.

## Cutter Ray Mk2 (Arrakis_Cutter_Ray_Mk2, prompt 05)

A second, separate gun: the vanilla `Weapon_Gun` with seven changes. It shares nothing with the Cutter Ray's code except `ArrakisPower` and the
block-damage call; the old ray (its code, models, particles) is untouched apart from one codec registration and a few lines in the start-up
self-check. Everything is generated by `tools/assets/make_mk2.js <unpacked Assets.zip>` and `tools/assets/make_molten.js mk2`; no vanilla asset is
edited. Obtainable from the creative menu (category Items.Tools) and `/give Arrakis_Cutter_Ray_Mk2`; no recipe.

**Copies of vanilla assets (vanilla id -> Mk2 id)**
- `Weapon_Gun` (item) -> `Arrakis_Cutter_Ray_Mk2`: same Model, Texture, Icon, IconProperties, Quality (Technical), Tags, `Weapon: {}`; Categories is
  Items.Tools (as the other tools), name and description in server.lang.
- item player animations (inline Parent `Rifle` + Shoot) -> `Arrakis_Cutter_Ray_Mk2` (an animation set asset, Parent Rifle, Shoot overridden).
- `Rifle/Attacks/Shoot/Shoot`, `Shoot_Moving`, `Shoot_FPS` (.blockyanim) -> `Arrakis_Cutter_Mk2_Shoot`, `_Shoot_Moving`, `_Shoot_FPS`, kick at 20%.
- `Handgun_Shoot` (camera effect) -> `Arrakis_Cutter_Mk2_Shoot`: Intensity 0.01 (0.05 x 0.2). The shake asset `Handgun_Shoot` is used as it is.
- `Gun_Shoot` (interaction) -> `Arrakis_Cutter_Mk2_Shoot` (RunTime 0.05, the vanilla gun's own override of the 0.25).
- `GunPvP_Assault_Rifle_Bullet` (projectile) -> `Arrakis_Cutter_Mk2_Bullet`; `Bullet_Blunderbuss` (its model) -> `Arrakis_Cutter_Mk2_Bullet`.
- `RifleShooting` (muzzle) -> `Arrakis_Cutter_Mk2_Muzzle` (without `Rifle_Bullet_Case`).
- `Gun_Impact` (impact) -> `Arrakis_Cutter_Mk2_Impact` (the smoke at its position, plus the globs) and `Arrakis_Cutter_Mk2_Sparks`; the spawners `Gun_Debris`,
  `Explosion_Fail_Sparks`, `_Sparks_Linger`, `Explosion_Fail_Flash_Glow` -> `Arrakis_Cutter_Mk2_Debris`, `_Sparks`, `_Sparks_Linger`, `_Flash_Glow` (emit offsets zeroed).
  The smoke spawner and the muzzle's other spawners are referenced as they are (no sound in them, checked).
- `Gun_Attack`, `Gun_Swing_Left`, `Gun_Swing_Left_Damage`, `Gun_Swing_Left_Effect` (the right-click swing) -> `Arrakis_Cutter_Mk2_Attack`, `_Swing_Left`,
  `_Swing_Left_Damage`, `_Swing_Left_Effect` (and their root interactions), without `SFX_Unarmed_Impact` / `SFX_Unarmed_Swing`. The swing keeps its 5 damage.
- New, not copies: `Arrakis_Cutter_Mk2_Gate` (interaction, type `Arrakis_Cutter_Mk2_Range_Gate`, code `CutterMk2GateInteraction`), root `Arrakis_Cutter_Mk2_Primary`
  = [Gate, Shoot], particle systems `Arrakis_Cutter_Mk2_Globs` (+ six spawners `Arrakis_Cutter_Mk2_Globs_Hold_n` / `_Fall_n`).

**The seven changes**
1. *No casing.* The only thing that draws it is the `Rifle_Bullet_Case` spawner of the muzzle system (texture `Bullet_Case.png`; nothing else in the rifle model, the Shoot
   animation or the interaction refers to a case: checked in the model's node names and the animation files). It is gone from the Mk2 muzzle.
2. *Recoil at 20%.* (a) Camera shake: Intensity 0.05 -> 0.01. (b) Gun and arm kick: it is in the keyframes (the first keyframe of each node is the held pose, equal to the
   idle pose; later keyframes are the kick and its recovery). The generator scales every later keyframe towards the first by 0.2 (positions linearly, rotations by slerp).
   It checks that duration, node names, frame counts and frame times are unchanged and the first keyframes untouched, and prints the largest swing: Shoot 45.00 -> 9.00 degrees
   (21 kick keyframes), Shoot_Moving 45.00 -> 9.00 (14), Shoot_FPS 5.00 -> 1.01 (2); e.g. the right arm of Shoot_FPS is pushed back 6 units in vanilla and 1.2 now.
   `Mk2AssetsTest` checks the same from the jar. The animation `Speed` stays 3 (it is the playback speed, not the kick).
3. *No sound.* Removed in the Mk2 copies: `SFX_GunPvP_Assault_Rifle_Bullet_Death` (projectile DeathSoundEventId); `SFX_Blunderbuss_Bullet_WhizBy` (a SoundEventId on the
   bullet model's FlyIdle animation: audible while the bullet flies; the prompt did not list it); `SFX_Unarmed_Impact` (twice: the swing damage and the item's variable)
   and `SFX_Unarmed_Swing` (the swing effect); the block break/hit audio is switched off with `SetBlockSettings.NO_SEND_AUDIO` in the damage call. Not present in vanilla
   and so nothing to remove: `Gun_Shoot` has no sound, the projectile has no launch/hit/miss sound set (the `launch*SoundEventId` fields named in the prompt belong to the other
   projectile system, `ProjectileConfig`, which this gun does not use), no impact spawner has a sound, the item has no sound set, the Rifle animation set and the Shoot animations
   have none. `Gun_Shoot_Fail` (SFX_Bow_No_Ammo) is not used by the Mk2. Still audible: footsteps and the like; and in Adventure mode the swing's `Block_Break_Adventure`
   breaks a block with the block's own break sound. The self-check logs the projectile's death/hit/miss sound ids (all null).
4. *Sparks at the crosshair.* The impact is spawned by the engine where the bullet dies, with the Y offsets 0.2 to 0.4 above. Now the gate spawns `Arrakis_Cutter_Mk2_Sparks` (debris,
   sparks, linger sparks, flash glow; no PositionOffset, emit offsets zeroed in the copies) at `eye + dir * BeamMath.entryDistance(...)`, the point where the look ray enters the targeted block
   (`TargetUtil.getTargetBlock`, as the old ray). The impact system keeps only the smoke, at Y 0.2 as before. They cannot double up (different systems, one spawner each) and nothing is
   spawned when the shot is refused. Done at the moment of the shot, not when the bullet arrives (17 ms for 5 blocks); the legacy projectile has no hit hook.
   `RifleShooting_Impact` is not used by this gun (the projectile has no hit or miss particles, only the death particles).
5. *White globs.* `make_molten.js mk2`: the existing Hold/Fall design (sizes 0.025/0.036/0.03, delays 0.5/0.58/0.67, Glow.png added to the scene), #ffffff falling to light grey #bfbfbf,
   no emit offset, not tinted; the six spawners are listed in `Arrakis_Cutter_Mk2_Impact` with the smoke's PositionOffset (Y 0.2), so they start at the very point of the smoke. The default run
   (the Cutter Ray's globs) is byte for byte what it was. Number of globs: **3 per hit, not 2 to 4**: a Hold and its Fall are two spawners and a random count would need them to agree,
   which a particle system cannot do; the other way, three impact systems chosen by a Chaining interaction, would put the 20 shots a second at risk on an unverified engine behaviour.
6. *Fire rate unchanged, block damage 1.* Primary = [Gate, Shoot], Shoot at RunTime 0.05 as vanilla. The bullet has no hit hook, so the gate damages the block at the shot with
   `BlockHarvestUtils.performBlockDamage`, an unbreakable `Tool_Pickaxe_Mithril` stand-in, tool id "Pickaxe", `damageScale` = the data setting `BlockDamage` (default 1). **Unit** (from the
   bytecode of `damageSingleBlock`): damage = the tool's power on the block's gather type times `damageScale`, taken off the block's health, which starts at 1.0; the block breaks at 0. For
   soft blocks it is 1.0 whatever the scale. **Changed afterwards to a crude pickaxe:** the stand-in is now `Tool_Pickaxe_Crude` with `BlockDamage` 1, so every hit is one swing of a crude pickaxe. Crude power: Rocks 0.25 (quality 1), Soils 0.35, SoftBlocks 1.0, volcanic rock 0.084. So stone takes **4 hits** (0.2 s at 20 shots a second), dirt and sand **3** (0.15 s) if they are Soils (a soft block would take 1); rock of a higher quality than 1 and ores the crude pickaxe cannot mine do not break, as for that pickaxe. (Before: Mithril, power 1.0, 1 hit each.) `BlockDamage` and `ToolItemId` are in `Arrakis_Cutter_Mk2_Gate.json`. Creative mode breaks the block at once, as the old ray does.
7. *Range 5, silent refusal.* `Arrakis_Cutter_Mk2_Range_Gate` is first in Primary. Refused (state Failed) when `TargetUtil.getTargetBlock(player, Range)` finds no block, or power is 0.
   **A Failed interaction stops the chain** (verified in the jar: `Interaction.tick` throws `ChainCancelledException` for Failed, and `InteractionManager` then cancels the chain), so the shot never runs:
   no animation, muzzle, projectile, shake or sound. What the client does while the button is held after a Failed chain is not known (the server never restarts a held player interaction; only the NPC
   systems call `tryRunHeldInteraction`): if it starts a new chain at once, holding the button and sweeping onto a rock fires; if it waits for the next click, the player has to click again.
   `TimeToLive` of the bullet is set to 0.05 but **this server version never reads it** (it appears only in the config class), so there is no flight cap; the gate is the only range limit,
   and a bullet that does fly lives for the engine's 60 s despawn.
- *Power:* 1 per second of firing = 0.05 per shot (`PowerPerSecond` x `FireInterval`), taken in the gate first. At 0 power the gate refuses and the attempt counts as use, as the old ray does; a refusal
  for range does not.
- *Creatures:* `Damage` 0. A bullet that reaches a creature stops there, spawns the impact (smoke and globs) and runs `DamageSystems.executeDamage` with 0: the health stat loses 0, the hit animation
  and the reticle marker return early for an amount of 0 or less, and the projectile damage carries no knockback. Not verified: whether an NPC's awareness systems still take the 0 damage as an
  attack (it may turn it hostile), and the sparks and block damage are decided by the look ray, so they also happen when a creature stands in front of the block.

**Right clicks and the off-hand tools while the Mk2 is held.** As the vanilla gun and the Cutter Ray: no `Utility.Compatible` and a Secondary, so by the rules in "Verified" above both clicks stay with the gun and the
off-hand tools cannot be used; the item also writes out priorities 10/10 as the Cutter Ray does (harmless). The Secondary is the vanilla swing (5 damage to creatures, which the prompt keeps).

**Verified in the headless server (no client)**: all Mk2 assets load with no error (the self-check logs the item, its animation set with the Shoot at speed 3, the gate, the shot at runTime 0.05, the root,
the projectile with damage 0 and no sound ids, and the three particle systems); `give Arrakis_Cutter_Ray_Mk2` runs; `gradlew test` includes `Mk2AssetsTest` (kick, camera, no sound id and no casing in any
Mk2 asset, smoke and globs on one point, sparks without offset).

**Needs a human on a client**: that Mk2 looks and holds like the vanilla gun and fires 20 times a second; that no casing leaves the gun; the feel of the recoil at a fifth (camera and arms, standing and
moving); that nothing is audible (firing, hitting, swinging); that the sparks, debris and flash sit exactly under the crosshair, the smoke where it was, and the white globs hang and fall from the smoke's
point; that a shot at 6 blocks or the sky does nothing at all and that the button then fires again as soon as a block is in range (the Failed question above); the block damage and its look (cracks) at
20 hits a second, and the hit counts above; power draining 1 per second and recharging; that a mob or player takes no damage, flinches or knockback and whether a mob turns on the shooter; a second
player seeing the muzzle, impact, sparks and globs.

## Cutter Ray Mk2: sounds (prompt 06)

**Superseded in part by prompt 07 (next section): the per-shot fire sound (SFX_Arrakis_Cutter_Mk2_Fire, FireSoundMinInterval) was removed because 20 overlapping clips a second made noise; what is said below about the fizzle, the world/local pair, the code-side playing and the cut still holds.** Two sounds back on the Mk2, both from the vanilla Portal_Zap clips; everything else stays silent. Nothing vanilla is edited; the old ray is untouched.

**Sound events** (`Server/Audio/SoundEvents/Equipment_of_Arrakis/`): `SFX_Arrakis_Cutter_Mk2_Fire` / `_Local` (one layer, Files Portal_Zap_01, 03, 04 by vanilla path, `RoundRobinHistorySize` 2, pitch -1..+1, volume -1..0 dB, MaxInstance 4) and
`SFX_Arrakis_Cutter_Mk2_Fizzle` / `_Local` (one layer, one file: the trimmed clip, pitch -1..+1, layer -4 dB, MaxInstance 2). Category `AudioCat_Weapons` (what the blunderbuss uses, so the weapon slider applies).
- *World/local pair, copied from the vanilla pairs* (SFX_Blunderbuss_Fire / _Local, SFX_Shield_T2_Raise / _Local): the world event has `Parent: SFX_Attn_Loud` (positional, MaxDistance 45, StartAttenuation 15, the blunderbuss shot's preset); the local one has no Parent
  (no attenuation: plays flat in the shooter's ears). Vanilla uses different, stereo files for the local one; here both use the same mono clips. Levels (0 dB for the shot, -4 dB for the fizzle) are a guess: the Portal_Zap clips' own level was not measured.
- *Random selection*: it is the client's. The server sends the layer (files, `RoundRobinHistorySize`) in the sound event packet; the picking is not in the server jar, so it is inferred from the vanilla events (several Files, one per play) and not tested.

**Where they play: from the gate's code, not from `Effects` of the Shoot interaction.** `CutterMk2GateInteraction` calls `SoundUtil.playSoundEvent3d(shooter, world event, eye, true)` (everyone but the shooter, positional) and `SoundUtil.playSoundEvent2d(shooter, local event, SFX)` (the shooter only).
Reasons: the interval has to be data and per shooter, which `Effects` cannot do (a sound in `Effects` plays at every run, 20 a second); and a refusal cancels the chain (`ChainCancelledException`), so an interaction hung on a Failed branch never runs. A shot that goes through plays the fire sound after the gate has let it
through and taken its power. The Shoot interaction has no sound. Data settings of the gate: `FireSoundMinInterval` 0.1 s (about every second shot makes a sound; 0 = every shot) and `FizzleMinInterval` 0.27 s (one fizzle at a time on a held trigger), kept per shooter.
- Fizzle: only when power is left and `getTargetBlock` finds no block within Range. At 0 power the refusal stays silent; the shot refusals do nothing else (no power, no animation).
- What it should sound like (expected, not heard): at 20 shots a second and 0.1 s, ten electric zaps a second, each 1.2 to 1.6 s long, so with MaxInstance 4 about four overlapping at any time: a crackling, buzzing stream of arcs rather than 30 stacked clips.
  What `MaxInstance` does when full (drop the new one or cut the oldest) is the client's, unverified; with `PreventSoundInterruption` true as in vanilla weapon events.

**The cut** (`tools/assets/make_fizzle.js <Assets.zip>`, node only; no audio tool is on this machine: no ffmpeg, sox, Python, and the JDK/Gradle/IDEA installs have none that edits Ogg): reads Portal_Zap_02.ogg out of the zip, parses pages and packets, reads the block sizes (256/2048) from the identification header and the
mode table (2 modes, short and long) from the end of the setup header, and adds up the decoded length packet by packet. **Check on the source:** the granule position of every page of the source file equals the length so computed (and the last, 61726 = 1.40 s, is below it, as an end-trim should be), which shows the block-size reading is right.
Then the headers and the first 67 of 130 audio packets (decoded length 12,480 at the 67th, the first to pass 11,907) go into new pages, the last has EOS and granule 11,907 (the decoder drops the rest: exactly 0.27 s), sequence numbers and CRC-32s redone. No re-encode. Result: 15,145 bytes (from 39,781), 5 pages.
The script re-parses its output (CRC of every page, BOS/EOS, sequence numbers, one stream, the three headers byte for byte, the granule of every earlier page one the source has, last granule 11,907). `FizzleClipTest` repeats the page, CRC, stream and granule checks on the file in the jar and the sound-event file lists.
- **Click at the end:** none avoided. A fade-out needs the audio re-encoded; an abrupt cut at 0.27 s may click if the signal is loud at that point. Nothing was done about it. If it clicks, options are a different cut point or a short added fade done by another tool.
- Not listened to: no player here.

**Verified in the headless server:** all four sound events load with no error or unused-key warning (the self-check logs layers, MaxInstance and file count), the clip is in the jar, `gradlew test` passes.
**Not verified (no client):** that the clip plays and sounds right, the random pick and no immediate repeat, volume levels, that the world sound reaches a second player and the local one the shooter only (the shooter excluded from the world one by the `true` flag, read in the jar's lambda), that the gate sounds fire on every refusal path as intended (fizzle once per 0.27 s while the button is held: depends on the client repeating the Failed chain, see above), silence at 0 power.

**For the repo:** `Arrakis_Cutter_Mk2_Fizzle.ogg` is a modified copy of a game audio clip and sits in `src/main/resources` and so in the jar and in git. Better to keep it out of git, since it is derived from the game's data: add the path to `.gitignore` and run `make_fizzle.js` in the Gradle build (an Exec task before `processResources`,
needing the game's Assets.zip on the build machine). Left as it is for now (committed like the other generated assets) until you say.

## Cutter Ray Mk2: held-fire sound and block impact sound (prompt 07)

Replaces the per-shot fire sound of prompt 06 (deleted: events `SFX_Arrakis_Cutter_Mk2_Fire` / `_Local`, the gate's `FireSoundMinInterval` setting, codec key and code) with the vanilla held-weapon pattern, and adds a block impact sound. The fizzle (events, file, `FizzleMinInterval`) is untouched; its file is byte for byte what it was (`cmp` after the refactor of the cutter).

**1. Held fire: start, loop, end.** Modelled on `Gun_Flamethrower` / `FlamethrowerSource`.
- *Chain:* `Arrakis_Cutter_Mk2_Primary` = [`Arrakis_Cutter_Mk2_Gate`, `Arrakis_Cutter_Mk2_Firing`, `Arrakis_Cutter_Mk2_Shoot`]. `Arrakis_Cutter_Mk2_Firing` is an `ApplyEffect` interaction (instant), so no Parallel branch was needed: it sits between the gate and the shot, runs only when the gate lets the shot through (a refusal cancels the chain before it), and so is refreshed 20 times a second while firing.
- *Effect* `Arrakis_Cutter_Mk2_Firing` (`Server/Entity/Effects/Equipment_of_Arrakis/`): `ApplicationEffects` World/LocalSoundEventId = `SFX_Arrakis_Cutter_Mk2_Firing` / `_Local`; `WorldRemovalSoundEventId` / `LocalRemovalSoundEventId` = `..._Firing_End` / `_End_Local`; `OverlapBehavior` Overwrite; `Infinite` false; `Duration` 0.25 (data).
- *What the overlap behaviours do* (read in the jar, `EffectControllerComponent.addEffect`): effect not active -> a new active effect is created and sent to the client as an Add: that is the start. Effect already active: **Ignore** changes nothing, so the countdown of the first application runs out, the effect lapses and is re-applied, and the start would replay every Duration; **Extend** adds Duration to the remaining time at each refresh, which at 20 refreshes a second grows without bound, so the end sound would come long after the release; **Overwrite** sets the remaining time to Duration, with no new active effect, so the effect lives while it is refreshed and lapses Duration after the last refresh. Overwrite is the one that keeps the loop going without replaying the start, as the flamethrower (0.2 s refresh, 0.5 s Duration) suggests. Confirmed on the server side. That the *client* does not replay the start when the existing effect is updated is inferred from the flamethrower: the sounds are played by the client from the effect data, no sound call is in the server's effect code.
- *Sound events* (all `AudioCat_Weapons`; world with `Parent: SFX_Attn_Loud`, local without a Parent, as the Mk2's other pairs). `SFX_Arrakis_Cutter_Mk2_Firing`: layer 1 is the start, a one-shot of Portal_Zap_01/03/04 (random, `RoundRobinHistorySize` 2, pitch -1..+1, volume -1..0, 0 dB); layers 2 and 3 are two **looping** layers of the same three clips, staggered by `StartDelay` 0.3 s and 1.0 s, at -7 and -9 dB, pitch -1..+1: two zap tracks of 1.2 to 1.6 s overlapping, a random clip on each pass, so a zap starts every 0.7 s or less and the tails overlap, which should read as a continuous crackle (a guess, from the Portal_Void example of looped random textures; not heard). MaxInstance 2, `PreventSoundInterruption` false.
  `SFX_Arrakis_Cutter_Mk2_Firing_End`: one one-shot layer of the same three clips at -4 dB, event `Pitch` -3 plus a spread of -1..+1, MaxInstance 2, `PreventSoundInterruption` true. **Pitch:** the validator requires `MaxPitch` >= 0 in `RandomSettings`: the first try (-4..-2) was rejected ("Must be greater than or equal to 0.0") and took the effect down with it, so the lowering is the event-level `Pitch` -3.
- *Fizzle and the end sound:* a range refusal does not refresh the effect, so 0.25 s later the end sound plays. The fizzle plays at once and lasts 0.27 s, so a player hears the fizzle and then the lower power-down zap just after it, a natural pair rather than one drowning the other (not heard). A target lost for one tick (0.05 s) mid-fire: the effect survives (0.25 s is five missed ticks), no end sound and the loop goes on, but the gate plays one fizzle over the loop (at most one per 0.27 s). Options if that is unwanted: apply the effect also on a range-refused tick (the end sound then comes 0.25 s after the last refusal, after the fizzle is over), or play the fizzle only when nothing was fired for a moment. Chosen: leave it (simplest; the blip is short and tells the player the aim slipped).
- *0 power:* the gate refuses, the effect lapses, the end sound plays once: the "power ran out" cue, as intended.
- *Quick re-press:* if the new press comes within the 0.25 s the effect is still active, so there is no new start and no end: one uninterrupted loop. After that, a new start. Not heard.

**2. Block impact sound.** `SFX_Arrakis_Cutter_Mk2_Impact` / `_Local`: layer A `Arrakis_Cutter_Mk2_Impact_Stone.ogg` (Stone_Hit_03, mono 48 kHz) at 0 dB, pitch -1..+1; layer B `Arrakis_Cutter_Mk2_Impact_Burn.ogg` (Entity_Effect_Burn_05, mono 44.1 kHz) at -4 dB, pitch -2..+2; volume spread -1..0 on both; MaxInstance 2; `PreventSoundInterruption` true. Both files are the first 0.1 s of their clip (4,800 and 4,410 samples), made by `tools/assets/make_mk2_sounds.js --length 0.1`.
- Played by `CutterMk2GateInteraction` at the point of the sparks (where the look ray enters the block face; world: positional for the others, local: for the shooter), each time a shot damages a block and at least `ImpactSoundMinInterval` (0.1 s, gate data) has passed since the shooter's last one: every second shot at 20 shots a second. Not when the shot is refused.
- *Does the engine's own block-hit sound play too?* No. In `BlockHarvestUtils.performBlockDamage` the block's Hit sound, its Break sound and the tool's hit-sound layer are all skipped when `SetBlockSettings.NO_SEND_AUDIO` (1024) is set, which the gate sets (read in the bytecode). The gathering-effects sound and the wrong-tool sound are separate calls not gated by the flag; they apply to incorrect-tool or gathering-effect cases and were not checked in the game. So our impact does not double with the stone's own sound.
- *The 0.1 s cuts:* no fade (it needs a re-encode), so each piece stops abruptly: a possible click or audible stop at 0.1 s, perhaps softened because the two layers end at slightly different moments. The first 0.1 s are 5.5 KB (stone) and 5.2 KB (burn), as dense as the rest of the clips, which suggests neither starts in silence; a guess from byte counts, not a listen.
  A dev-only fade, if wanted: decode with a tool (ffmpeg or sox, or a Java Vorbis decoder), apply a few ms of fade-out, re-encode with oggenc/ffmpeg, and keep the result as the generated file. Cost: a dev dependency on an external tool and a re-encode (the quality loss on a 0.1 s clip is small). Nothing added.

**Tools.** `tools/assets/trim_ogg.js` (shared: `trimOgg(buffer, seconds)` picks the sample target from each file's own rate, plus `readZipEntry` and `assetsZip`); `make_fizzle.js` is now a thin caller (same 11,907-sample cut, same file); `make_mk2_sounds.js [--length 0.1]`. Both read Assets.zip (default `%APPDATA%/Hytale/install/release/package/game/latest/Assets.zip`). For each cut the script checks that every granule position of the source equals the length counted from the block sizes it read.
`FizzleClipTest` checks all three clips (valid page CRCs and sequence numbers, one stream, BOS/EOS, identification header then comment header, last granule = round(seconds x the file's own rate), so within one sample), the sound event files, and the chain order and effect settings in the jar. `make_mk2.js` also writes the Firing interaction, the effect and the new Primary root (the files in the repo were written from the same text, since the vanilla folder was not unpacked).

**Verified in the headless server:** the eight sound events, the effect (duration 0.25, OVERWRITE, not infinite), the Firing interaction and the three clips resolve with no error (the effect's mono validator accepted the mono clips, looping layers are accepted); `gradlew test` passes; the fizzle file is unchanged.
**Not verified (no client):** everything audible; that the client plays the start once, keeps the loop on re-application and stops it when the effect lapses; the end sound timing; the loop's density; levels (guesses); `MaxInstance` behaviour; whether the client repeats the Failed chain on a held trigger (fizzle repetition); a second player hearing the world sounds; how the shooter's own client plays the effect sound (a Local one, as the vanilla pair).

## Compactor: compacting sand (prompt 08)

The Compactor's right click now compacts sand: a held click on a sand block within 3 blocks cracks the block over `CompactSeconds` (1.0), then one drop pops out of the face, the crack resets, and it repeats; the block is never broken. A low hum loops while it really compacts. The model, texture, scale, aim pose and animation set are untouched; the old ray, the Mk2 and the other tools are untouched.

**What changed.** `Arrakis_Compactor_Aim` (the interaction in `Arrakis_Root_Compactor`, `RequireNewClick` true) keeps all its Charging fields, the aim animation, `AllowIndefiniteHold` and its `Next`/`Failed`, but its Type is now `Arrakis_Compactor_Compact` (`CompactorInteraction`), with the settings as data: `Range` 3, `CompactSeconds` 1, `DropsPerCycle` 1, `PowerPerSecond` 1, `MaxCrack` 0.9, `AllowedGroups` ["Sand"], `ExcludedBlocks` ["Arrakis_Sand"], `ExcludedPrefixes` ["Arrakis_Worm_Ripple_Layer_", "Arrakis_Worm_Sunk_"], `HumEffectId`. Item and root JSON did not need to change. New: effect `Arrakis_Compactor_Active`, sound events `SFX_Arrakis_Compactor_Active` / `_Local`, `CompactMath` (pure rules), `CompactorTrail` (state and engine calls), `CompactorSystem` (cleanup), `CompactorSelfTest` (dev command `arrakis compactor selftest`).

**How the repeating tick sits next to the held aim.** It does not need a second interaction: `CompactorInteraction` *extends* the vanilla `ChargingInteraction` and overrides `tick0`: it calls the vanilla code first (aim, hold, release, `Next`), then, if the interaction is still `NotFinished` (the Charging code sets that while the client reports the button held, Finished on release, read in the bytecode), it compacts. The engine ticks a running interaction on every world tick, so no `RunTime` repeat, Parallel branch or Repeat fork was needed (a `RepeatInteraction` with `ForkInteractions` exists, but it repeats until a fork fails, which a release would not cause). Time is taken from the real clock between ticks (at most 0.2 s per tick), so the tick rate does not matter. Inferred, not seen: that `tick0` really runs on every world tick while held (the Charging class depends on the same, to see the client's release); if it ran less often the cycle would still be right (time-based), only the crack steps would be coarser.

**Which blocks.** `CompactMath.allowed(group, id, ...)`: `BlockType.getGroup()` exactly in `AllowedGroups`, id not in `ExcludedBlocks`, id not starting with an `ExcludedPrefixes` entry (empty prefixes ignored). Other `Arrakis_` blocks are not excluded (tested). Anything else, or no block within range: no crack, no drop, no power spent, no hum; the aim pose carries on. Vanilla `Soil_Sand` read in the headless server: group "Sand", id `Soil_Sand`.

**The crack, and what stops a break.** Not through `BlockHarvestUtils.performBlockDamage` (that call breaks the block when health reaches 0, and cannot be told not to). The engine's block health is a per-chunk component, `BlockHealthChunk` (map of block position to health, 1.0 = whole); `damageBlock(instant, world, pos, delta)` subtracts and tells the clients near it (the packet that draws the crack), `getBlockHealth` reads (1.0 if no record). `damageBlock` itself never removes a block; only `performBlockDamage` does that. So: each tick the health is lowered to `1 - MaxCrack * progress` and **never below `1 - MaxCrack`** (`MaxCrack` is clamped to at most 0.9, so at least 0.1 is left), and only by the part not already taken, never raised by a lagging player. What this player took is remembered (`State.taken`) and given back when the cycle completes, the target changes, the button is released, power is 0, or the sweeper ends it (see below). Nothing in the Compactor code calls a break.
- *Measured in the headless server (`arrakis compactor selftest`, real engine calls on a Soil_Sand block placed at 0, 200, 0):* half a cycle shows 0.55; the same tick again changes nothing; a second player behind adds nothing; a second player at the end of the cycle gives 0.1 (0.099999994); 1000 ticks of pushing far beyond the end (progress 5, health -50) stay at 0.1; the block is still there; player one letting go leaves 0.55, player two letting go leaves 1.0 and the record is gone; the block is still there.
- *`repairBlock` does not do a partial heal:* it removes the damage record whatever the amount (measured: 0.1 + 0.2 gave 1.0), so a give-back is a negative `damageBlock` for a partial one and `removeBlock` when everything taken comes back. With two players compacting the same block, the first to let go gives back only its share.
- *A normal pickaxe afterwards:* inferred from `performBlockDamage` (it reads the same `getBlockHealth` and subtracts), not exercised here: after the heal the block is at 1.0 and breaks as any block. While a crack is on it (health between 0.1 and 1.0) a pickaxe swing finishes it sooner, as for any cracked block; that is the intended meaning of a crack.
- *Hit sounds and particles:* the engine plays the block's Hit sound, and the tool's hit sound, from `performBlockDamage`, which is not called, so **no engine block sound plays at all** (the hum is the only sound, as asked). Whether the *client* draws hit particles for the damage update (the sand particles) is not known; the server sends only the damage packet, it spawns no hit particles itself in that path (the particle calls in `performBlockDamage` are for gathering effects only). If the sand particles do not appear, the nearest alternative is spawning the block's particle system from code at the face; not done.

**Drops.** `CompactorTrail.dropsOf` mirrors the engine's break path: the block's `Gathering.Breaking` (quantity, item id, drop list id) or, for a soft block, `Gathering.Soft` ids, passed to `BlockHarvestUtils.getDrops(block, quantity, itemId, dropListId)`, which is the engine's own drop generation (item default, drop list lookup through the item module). So loot tables and block changes flow through. For `Soil_Sand` it gives `Soil_Sand x1` (read in the headless server). `DropsPerCycle` repeats the call. They are spawned with `ItemComponent.generateItemDrop` (the engine's own item entity, with the pickup delay and despawn timer it uses) at the middle of the face the look ray enters, 0.15 out, pushed out along the face normal at 1.5 and up at 1.5. The private `spawnDrops` of `BlockHarvestUtils` could not be called, so this part copies what it does. Not run in the headless server (it needs a player for the look ray): needs a client.

**Power.** `PowerPerSecond` times the time since the last tick (about 1 per second), only when there is a valid target and the cycle is running. At 0 power: the attempt is counted as use (`ArrakisPower.spend(.., 0)` sets the use time, as the Cutter Ray does, so holding the button at 0 does not recharge) and the cycle is reset and the crack given back. With no valid target `spend` is not called, so the power recharges normally while aiming at stone or the sky.

**Resets.** Release (the Charging code ends the hold: state is not `NotFinished`), a different target block or block id, a non-sand block, 0 power, a gap of more than 0.2 s between ticks: `CompactorTrail.release` in the interaction. `CompactorSystem` (every world tick, per player) ends a compacting whose ticks have stopped for 0.3 s (cancelled chain, client gone), whose utility slot no longer holds the Compactor (item swap), who is dead or invalid. Disconnect: `CompactorTrail.forget` gives the health back on the world thread.

**The hum.** `Arrakis_Compactor_Active` (`Server/Entity/Effects/Equipment_of_Arrakis/`): ApplicationEffects World/LocalSoundEventId only (no removal sounds), OverlapBehavior Overwrite, Duration 0.25, Infinite false: the structure and overlap choice of the Mk2 firing effect (Overwrite resets the time left without making a new effect, so a refresh does not restart the loop; read in `EffectControllerComponent.addEffect` in prompt 07). It is applied from code on each compacting tick (`EffectControllerComponent.addEffect`, as `ApplyEffectInteraction` does), not on idle aim ticks, so the loop runs while compacting and ends about 0.25 s after it stops. `SFX_Arrakis_Compactor_Active` (+ `_Local`): one looping layer of `Sounds/Weapons/Staff/Staff_Charged_LP_01.ogg` (vanilla; mono, 44.1 kHz, 3.66 s, 54 KB, identical to the candidate copy), **Volume -6 dB**, MaxInstance 2, `PreventSoundInterruption` false, `AudioCat_Weapons`, world with `Parent: SFX_Attn_Loud`, local without a Parent (as the other Equipment sounds). No pitch spread. The clip is used from the game by path, not copied. A refresh clicking, a gap at the loop seam: unknown, needs ears (the clip is a vanilla "LP" loop).

**Hands (not changed, from the earlier rules in this log).** An off-hand tool gets right click only if the main-hand item has `Utility.Compatible` (pickaxes, swords, food) or no Secondary (an empty hand). The Cutter Ray and the Mk2 keep both clicks (no `Utility.Compatible` and a Secondary), so the Compactor cannot be used while either is held. Read from the item data, not tried in game.

**Verified in the headless server:** the interaction type registers and the Compactor's aim asset loads as `CompactorInteraction`; the effect (duration 0.25, OVERWRITE, not infinite) and both sound events load with no validator error (mono clip accepted in the effect, a looping layer accepted); `gradlew test` passes (`CompactMathTest`: the rule with the three lists and the worm ids 1 to 16, case-exact group, future Arrakis blocks allowed, the cycle and its drop at 1.0 s, reset, lag-spike clamp (never two drops from one tick), the crack floor for any progress or setting, the asset defaults); the engine crack/heal/drop calls above.
**Needs a human on a client:** that the interaction really compacts each tick while held and stops on release, with the aim pose unbroken; the crack growing over about 1 s and the sand hit particles; the item popping out of the face and being picked up; the crack resetting and repeating; the block never breaking however long it is held, with two players, and a pickaxe breaking it afterwards; no action on `Arrakis_Sand`, `Arrakis_Worm_Ripple_Layer_N` and `Arrakis_Worm_Sunk_N` (needs the Dunes and Worms mods), stone, dirt, the sky; 3 blocks works and more is ignored; the hum only while compacting, smooth loop, stopping about 0.25 s after, heard positionally by a second player, and the level; power draining about 1 a second only while compacting; that the Compactor cannot be used while the Cutter Ray or the Mk2 is held, and works with a pickaxe or an empty hand; that no creature is hurt (nothing in the code touches creatures).

## On-screen display: POWER, AMMO and the tool panel (prompt 09)

Built the way the Water mod builds its HUD. A new custom HUD `EquipmentHud` (key `EquipmentOfArrakis`) per player, with POWER (big white number, label above) left of the hotbar, AMMO right of it, and a tool panel (icon and name of the off-hand tool in use) above POWER. Read only: no tool, no power rule, no recharge and no vanilla HUD component was touched.

**Files.** `EquipmentHud` (the `CustomUIHud`), `HudSystem` (a per-player ticking system), `HudSettings` (config and live values), `HudMath` (the pure rules), `AmmoReadout` (the hook), `Common/UI/Custom/Hud/Equipment_of_Arrakis/Arrakis_Equipment.ui`, the plugin wiring and the `/arrakis hud` commands in `PowerCommands`.

**Whether two custom HUDs coexist.** The server keeps custom HUDs in a map by key (`HudManager.customHuds`) and `CustomUIHud.show()`/`update()` each send a packet `CustomHud(key, zOrder, clear, commands)`: ours and Water's are separate entries with separate keys, and adding one does not touch the other (read in the jar). Element ids are unique to each document (Water's start with `#Water`/`#Exposure`, ours with `#Equip`/`#Power`/`#Ammo`/`#Tool`). That the client draws both side by side is inferred, not seen.

**POWER.** `ArrakisPower.get` each world tick; `HudMath.powerDisplay`: a whole number 0 to 100, 0 only when the value is really 0 (above 0 and under 1 shows 1), clamped to 100. Colour: `#ff5a2a` when the *shown* number is below `LowPowerThreshold` (20), white from 20 (so 19.6, which shows 20, is white). Sent only when the number or the colour changes and at most `PowerUpdatesPerSecond` (10) times a second; the colour is a `.Style.TextColor` update, the number a `.Text` update (both property forms are the ones vanilla pages use).
**AMMO.** Shows `AmmoPlaceholder` ("--") until a source reports a number. The hook is `AmmoReadout.setSource(uuid -> OptionalInt)` (public, called once per world tick per player, so cheap); nothing is wired to vanilla stats. Sent only on change.

**Layout and how it behaves at other resolutions.** There is no percentage unit in .ui, so the bottom row is the full screen width split into two halves of equal `FlexWeight`: the middle of the screen (the middle of the client-drawn, centred hotbar) is then exact at every resolution and aspect ratio. In the left half a flexible spacer pushes the POWER readout to the right end of the half, and a fixed pad of `PowerOffset` keeps its right end that far from the middle; the right half mirrors it for AMMO (pad `AmmoOffset`). Readouts have a fixed `ReadoutWidth`, text aligned to the side facing the hotbar (POWER right-aligned, AMMO left-aligned), and sit `BottomMargin` above the bottom of the screen. The tool panel is another row of the same kind above them (`ToolGap`). Labels and numbers are appended inline by the code (a label 16, a number 48, bold, white with a black outline of alpha `OutlineStrength` 0.6, `#000000(0.60)`), because their size, colour and outline are settings.
- *Limits expected:* the offsets are in the HUD's own pixels, the same units as the vanilla hotbar, so they should scale with the UI scale setting as the hotbar does (inferred, not seen). The defaults (`PowerOffset` 400, `AmmoOffset` 350, from the prompt's mockup numbers, 17% of 2000 px plus the Z slot) are a first guess: the real hotbar width in HUD pixels was not measurable here. Tune on your screen. If the hotbar's width in HUD pixels differs between aspect ratios the readouts will not follow it exactly, since the HUD cannot ask the client where the hotbar is.

**Settings (data).** `Equipment_of_Arrakis.json` in the plugin's data folder (written with the defaults at start-up so there is something to edit): `LabelSize`, `NumberSize`, `OutlineStrength`, `PowerOffset`, `AmmoOffset`, `BottomMargin`, `ReadoutWidth`, `LowPowerThreshold`, `TextColor`, `LowPowerColor`, `AmmoPlaceholder`, `ToolIconSize`, `ToolNameSize`, `ToolGap`, `ToolPanelWidth`, `PowerUpdatesPerSecond`, `ToolItems`. Defaults in `HudSettings` too. `/arrakis hud tune` lists the live values; `/arrakis hud tune <name> <value>` changes one (names are not case-sensitive; also `TextColor`, `LowPowerColor`, `AmmoPlaceholder`) and every display is rebuilt on the next tick; not saved. `/arrakis hud rig [tool]` shows the panel for 5 seconds (short names work: `compactor`, `dew_reaper`, `blood_extractor`); `/arrakis hud rig ammo <n>` shows the number in AMMO for 5 seconds. The commands use the same default permission as the other `/arrakis` commands (so far as the server's generated command permission makes them operator-only; not tried).

**The tool panel and how "in use" is detected.** Chosen: read the player's `InteractionManager` (the entity component that holds the running `InteractionChain`s). A tool is in use when a chain of type `Secondary` is running (`NotFinished`) whose root interaction id is the Secondary root of one of the `ToolItems` (looked up from the items: `Arrakis_Root_Blood_Extractor`, `Arrakis_Root_Dew_Reaper`, `Arrakis_Root_Compactor`), while that tool is the active item of the utility slot or the main hand, and the player is alive. For the Compactor that is the whole held right click (the aim pose counts even with no sand in range, because the Charging chain runs for as long as the button is held). Why not the effect: an entity effect refreshed by each tool would have needed each tool's Secondary chain edited (against "no tool changed") and would only lapse after its Duration; the chain check needs no asset change, costs one small map lookup per tick, and ends at once on release, swap, death or a cancelled chain. Adding a tool is one id in `ToolItems`. Disconnect: `HudSystem.forget` (the plugin's existing disconnect handler).
- Not verified: how long the Blood Extractor's and Dew Reaper's Secondary chains run. If either ends after one short swing (Finished) the panel would flash for that time rather than for the whole hold; the Compactor's is the held Charging chain.
- *Icon and name.* The `.ui` has an `ItemIcon #ToolIcon` (the element vanilla uses in `ItemRepairElement.ui`); the code sets `#ToolIcon.ItemId` to the item id (the property the vanilla repair and barter pages set; `ItemSlot` takes `.ItemId` the same way, and `ItemGridSlot` is the other route, not needed). The name is `Message.translation("server.items.<id>.name")` set as the label's text, so it follows the language files (the three tools have those keys).

**How it is kept alive.** `HudSystem` runs every world tick per player. A HUD that is gone (first tick, respawn, world change) or built for old settings is added again, as the Water system does (`getCustomHud(KEY) != hud`). A rebuild forgets what was sent, so every value is sent again.

**Verified in the headless server (no client):** the plugin loads with no asset or UI error; the settings file is written with the defaults; `arrakis hud tune` lists the values and sets and rejects as designed; `AssetSelfCheck` logs the document and the three tool items with their Secondary roots; `gradlew test` passes (`HudMathTest`: power text for 0, 0.01, 0.4, 0.99, 1, 56.4/56.6, 99.6, above 100, NaN and negative; colour at 19/20 and at 19.4/19.6; the change rule; the rate limit; the AMMO text; the outline colour; the tool panel state machine incl. switching tool and a new document; the document ids).
**Not verified (needs a human on a client):** everything visible. That the document parses on the client (it uses only constructs the vanilla .ui files use, but it was not loaded by a client); the look, sizes and positions against the hotbar at 1920x1080 and one other resolution or aspect ratio, in daylight and at night; the outline strength; red-orange below 20 and white at 20; recharge counting up; `rig ammo 117` and its return to "--"; the panel appearing for the Compactor, the Blood Extractor and the Dew Reaper while the button is held and going at once on release, swap (Z), death and logout; the icon and the translated name; the Water and exposure bars still updating at the same time; a second player seeing only their own numbers (the state is per player id and each HUD is sent to its own player); that the HUD scales with the UI scale setting like the hotbar.

## The Cutter Ray Mk2 becomes the Cutter Ray (the old beam Cutter Ray removed)

**Naming note for everything above:** the sections before this one call the gun "Cutter Ray Mk2" and use `Arrakis_Cutter_Ray_Mk2` / `Arrakis_Cutter_Mk2_*` ids; after this change those are `Arrakis_Cutter_Ray` / `Arrakis_Cutter_Ray_*`. The sections about the old beam gun (lock-on, laser line, molten globs) describe code and assets that no longer exist.

**Removed (the beam gun):** item `Arrakis_Cutter_Ray`, interaction `Arrakis_Cutter_Beam`, `Arrakis_Cutter_Ray_Secondary`, roots `Arrakis_Root_Cutter_Ray` and `_Secondary`, the laser models and textures (`Arrakis_Cutter_Laser*`, Server and Common), the old molten glob particles (`Arrakis_Cutter_Molten_*`), the Java `CutterBeamInteraction`, `MoltenTrail`, `MoltenTrailSystem`, `CutterLaser`, `BeamSegment`, `BeamSegmentSystem`, `StrongestPickaxe` (only the beam used it), the `/arrakis laser ...` commands, their lang entries, the `BeamMath` hand/piece code and its tests, `tools/assets/make_laser.js` and the old-ray branch of `make_molten.js`. `BeamMath.entry/entryDistance/offFace` stay: the gun's gate and the Compactor use them. The v3 laser work is still in `git stash` and is now moot.
**Renamed (the Mk2 gun):** every id and file with `Arrakis_Cutter_Ray_Mk2` -> `Arrakis_Cutter_Ray`, `Arrakis_Cutter_Mk2_` -> `Arrakis_Cutter_Ray_` (item, animation set, interactions, roots, projectile, model, particles, camera effect, effect, sound events, clips, animations), the particle folder `Equipment_of_Arrakis_Mk2` -> `Equipment_of_Arrakis`, the interaction type `Arrakis_Cutter_Mk2_Range_Gate` -> `Arrakis_Cutter_Ray_Range_Gate`, `CutterMk2GateInteraction` -> `CutterRayGateInteraction`, `Mk2AssetsTest` -> `CutterRayAssetsTest`, the generators `make_mk2.js` / `make_mk2_sounds.js` -> `make_cutter_ray.js` / `make_cutter_ray_sounds.js`, display name "Cutter Ray". The rename was one scripted pass over the text of `src` and `tools`; `make_molten.js` regenerates the glob files byte for byte as they are now.
**Saves:** an item id that no longer exists vanishes from inventories: `Arrakis_Cutter_Ray_Mk2` stacks are gone, while old `Arrakis_Cutter_Ray` (beam) stacks now simply are the new gun. `/give Arrakis_Cutter_Ray` gives the gun.
**Power:** already linked to `Arrakis_Power` (no change needed): `CutterRayGateInteraction` takes `PowerPerSecond` (1) x `FireInterval` (0.05) = 0.05 per shot with `ArrakisPower.spend` before a shot goes through, refuses at 0 power (and counts that as use so it does not recharge), and the HUD's POWER number follows it.
**Verified in the headless server:** all assets load with no error, the self-check lists the renamed item, gate, shot, root, projectile, particles, sound events, effect and clips; `gradlew test` passes. **Needs a client:** firing, sounds and particles as before (nothing about their behaviour changed), and that no leftover beam, laser or molten effects appear.

**Cutter Ray power cost changed to 10 per shot.** It was `PowerPerSecond` 1 x `FireInterval` 0.05 = 0.05 per shot (not zero, but 0.05 of a 100 pack, so it looked like nothing was used). The gate now has one setting, `PowerPerShot` (default 10; `PowerPerSecond` and `FireInterval` are gone, nothing else used them). At 20 shots a second that is 200 power a second: a full pack of 100 lasts 10 shots (0.5 s), and the last shot may take the rest (`ArrakisPower.spend` takes what is left on a short payment). The lang description says "Costs 10 power per shot". Not tried on a client.
