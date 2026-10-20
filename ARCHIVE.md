# Archive: what was tried and abandoned, and the client rules learned

The full history (every experiment, probe and bisect) is recoverable from git: tag `equipment-v2.1-baseline` is the last commit before the cleanup, and `equipment-v1-final` is the v1 mod.

## Client rules learned on a real client (0.7.0)

The server log only says "Crash"; the real message is in the client log, `%APPDATA%\Hytale\UserData\Logs\<date>_client.log`.

1. **A bad run-time `Set` disconnects the player** ("Failed to apply CustomUI HUD commands"). Validate every string before sending it.
2. **No colour with an alpha at run time.** `#ffffff(0.5)` is `.ui` syntax; sent from the server it fails with `Failed to convert JSON value (String) to specified type (UInt32Color)`. Send plain
   `#rrggbb`; do opacity with `Visible` on groups whose alpha is fixed in the document.
3. **`Bar.Color` does not tint a textured `ProgressBar`** (the fill showed grey). `Background: (TexturePath, HorizontalBorder, Color)` on a `Group` does. A fill's length is two groups sharing
   `FlexWeight` (value and 1000 minus value).
4. **Safe run-time sets (proven):** `.FlexWeight`, `.Visible`, `ProgressBar .Value`, `.Background.Color` (`#rrggbb`), `.Anchor` objects. Texture paths are not changed at run time; use separate documents.
5. **A relative texture path cannot leave the pack.** A pack document naming `../../../InGame/Hud/...` made the client reject it, and the client registers pack documents in path order and stops at the first
   it cannot accept, so every later document was "not found" and the join failed. Never ship such a document; append only a document the pack has.
6. **A custom HUD cannot address elements of the built-in client UI.** `#ActiveItemNameLabel.Anchor` gave "Selected element in CustomUI command was not found" and ended the session. Vanilla labels
   cannot be moved, hidden or restyled from the server; make room with our own layout.
7. **A byte-identical copy of a vanilla file counts as a "Duplicated Asset"** on the server (the count went from 725 to 727). Re-encode the pixels instead.
8. **The category `Items.Weapons` drives the client's vanilla weapon ability HUD** (the ability status and the buttons on the right). An Arrakis weapon listing only `IslandCraft.Weapons` lost it;
   listing both keeps it. (Found through the community mod WeaponCustomHUD and a field-by-field diff of the Crude daggers against ours: categories, Ability4, `maxDurability 0` and
   `durabilityLossOnHit 0` were the only differences. The category was the first suspect; this is the change that was made, with the client result still to be confirmed in the test sheet.)
9. **Engine behaviour found by reading bytecode:** the default `InteractionRules` make every standard input type blocked by every other while a chain runs, so an Ability4 root needs
   `Rules {"BlockedBy": []}` to work mid-swing; a killed NPC keeps its entity for the death animation time only in vanilla (the extractor module now stretches it to `CorpseSeconds`, see MODULES.md); a `Projectile` asset is not removed by `ModuleAssets` when a module is off; the asset pack
   loads every asset whatever the config says, and one asset whose interaction type nobody registered stops the whole boot.

10. **Asset validator rules found while building the Thumper (0.7.0):** a `Server/Models` asset (the model of an entity) may only point at `Characters/, NPC/, Items/, VFX/` ("must be within the root"; the
    torch model is under `Blocks/`, so the standing Thumper has a re-encoded copy); an item `Icon` must be under `Icons/Items/` or `Icons/ItemsGenerated/`. A sound event's `Volume` (dB) and `Pitch`
    (semitones) are kept by the loaded asset as a gain and a ratio, so writing the config values over it needs the conversion (10^(dB/20), 2^(semitones/12)); the first version wrote the raw numbers.
    A deployable config with `LiveDuration` 0 has no despawn timer, `MaxLiveCount` removes the oldest instead of refusing a new one, and `HitboxCollision` exists only when a
    `HitboxCollisionConfig` is named.

11. **Movement facts for the Suspensor Belt (read from the 0.7.0 jar and Assets.zip; the lab module that answered the open questions on a client has been deleted; rule 12 is what it found).**
    - **Gliding is entirely client-side.** `ToggleGliderInteraction.firstRun` on the server is a bare `return`; the interaction only makes a packet for the client. `GlidingCondition` just reads
      `MovementStates.gliding`, which the client sends. The glider numbers live on the item: an item JSON `Glider` block (`TerminalVelocity` -2, `FallSpeedMultiplier` 6, `HorizontalSpeedMultiplier` 1.1, `Speed` 10,
      the dive values) is sent in `ItemBase.gliderConfig`. The stat `GlidingActive` (0 to 1) only follows the `Gliding` condition (+1 per 0.1 s while gliding, -1 while not) and drives the open/closed model of the
      glider. Nothing in the server starts a glide: `SavedMovementStates` (what `SetMovementStates` carries to the client) has one field, `flying`. The vanilla click is `GroundedCondition` (failed = in the air) then
      `ToggleGlider`, with durability, a tiny stamina cost and `RequireNewClick`. Whether the client glides from a Z-slot item was never answered (the glider route was not pursued).
    - **Fall damage is computed on the server from the vertical speed the client reports at the landing,** not from the height: `DamageSystems.FallDamagePlayers`, on the tick the client says `onGround` with a
      current fall distance above 0, and not in fluid. With v = |client Y velocity| in blocks a second and `MovementConfig` (Default.json) `MinFallSpeedToEngageRoll` 21: nothing at 21 or less; above it
      `floor(maxHealth / 100 * ((0.58 * (v - 21))^2 + 10))` of cause Fall, so v = 22 gives 10 % of max health, 31 gives 43 %, 41 gives 144 %. A roll lowers it (full mitigation up to `MaxFallSpeedRollFullMitigation` 25, partial
      33 % up to `MaxFallSpeedToEngageRoll` 31). `WorldConfig.isFallDamageEnabled` switches it off. So slowing the fall below 21 blocks a second prevents the damage whatever the height; a slow fall that is only
      server-side bookkeeping does not, because the speed that counts is the client's.
    - **Movement is client-authoritative.** The client sends `ClientMovement` (states, position, wish movement) every tick; `Velocity.getClientVelocity()` is what it reports. The server can push back with
      `Velocity.addInstruction(vector, VelocityConfig, Add|Set)`, which `PlayerVelocityInstructionSystem` consumes and (judging by the class and packet names, not read in full) sends as a `ChangeVelocity` packet, with `ClientTeleport` for positions, and with
      `UpdateMovementSettings` for the physics numbers.
    - **MovementSettings** (public fields of the protocol class, pushed with `MovementManager.update(packetHandler)`; `getSettings()` is the live copy, `getDefaultSettings()` what a reset returns to,
      `resetDefaultsAndUpdate(ref, accessor)` restores and pushes): `mass`, `dragCoefficient`, `invertedGravity`, `velocityResistance`, `jumpForce` (11.8), `swimJumpForce`, `jumpBuffer*`, `acceleration`, `airDragMin/Max`
      (0.96, 0.995) and `airDragMin/MaxSpeed`, `airFriction*`, `airSpeedMultiplier`, `airControl*`, `baseSpeed` (5.5), the climb speeds, `horizontalFlySpeed` and `verticalFlySpeed` (10.32 in the default config),
      `fly` (`FlyMode`: Disabled, Allowed, Forced), the walk, run, crouch and sprint multipliers, `variableJumpFallForce` (35), `fallEffectDuration`, `fallJumpForce`, `fallMomentumLoss`, the auto-jump and
      slide values, `minFallSpeedToEngageRoll` (21) and `maxFallSpeedToEngageRoll` (31), the roll values and the slope and wall angles. `MovementConfig.fly` is documented as: Disabled revokes the capability, Allowed
      grants a toggleable fly, Forced keeps the player flying with the toggle disabled; unset it derives from the game mode (Creative: Allowed). The engine's own server-side switch is `Player.setFlying(ref, flying, accessor)`:
      on makes the settings' fly Forced (if it was) else Allowed, off puts the default fly mode back, and it pushes the settings and the flying flag.
    - **The Z slot** is `InventoryComponent.Utility` (an `ActiveSlotInventoryComponent`: `getActiveSlot()`, `getActiveItem()`), readable on the world thread every tick; an item needs `Utility { Usable: true }`. Armor items can carry
      movement settings (`Rocket_Boots`: `Armor.MovementSettings.ExtraJumpCount`); the Rocket Boots are a Legs armor item, not a Z item.

12. **What the movement lab proved (module deleted; the Suspensor Belt is built on this).**
    - **Flight is the client's double tap, gated by the setting.** The double tap of Space is client code gated by `MovementSettings.fly` (Disabled / Allowed / Forced; creative gets Allowed, everyone else Disabled). The server never sees
      the tap, only the resulting `flying` flag in `MovementStates`. `fly = Allowed` with chosen `horizontalFlySpeed` and `verticalFlySpeed`, pushed with `MovementManager.update`, made the double tap fly in Adventure.
    - **Units.** Fly speed 0.5 in Adventure feels like 0.1 in creative, so the belt starts at 1.0 and exposes the numbers.
    - **`jumping` is not "Space held"**: it is true only while rising after a jump and the client clears it the moment flying starts. A driver built on it (the lab's Mk II/III prototype) switched itself off the tick it switched flight on and
      flickered about 18 times a second. Nothing should be driven from `jumping`.
    - **Never push settings or the flying flag every tick.** The prototype did and spammed the client (and drained stamina through the jump spam). Push only on a state change.
    - **Fly speed 0 does not remove jump momentum**: with `verticalFlySpeed` 0 the client velocity was still about +5.5 blocks a second for a moment after the flight began, then the fall read -13.9 (two samples: likely, not proven).
    - **Measured:** the client velocity (not the position delta, which is a half-second average) is the number fall damage uses; at rest it reads about -0.5; the fastest fall seen was -22.08 (just over the 21 threshold). The Z slot item reads
      correctly every tick. The glider route, the settings route, the velocity clamp and fall damage numbers were not exercised and were dropped with the lab.
    - **Engine helper:** `Player.setFlying(ref, boolean, accessor)` starts or stops flying, pushes settings and flag and restores the default fly mode when stopping; it is what ends the belt's flight at 0 Mana.

13. **What the first Mk I test build proved (world "Suspensor Test", logs of 2026-10-19; the first routes are removed).**
    - **`dragCoefficient` and `mass` do nothing to the fall.** Pushing `dragCoefficient` 0.5 -> 15.125 (x30.25) on engage changed nothing: landing speeds stayed -22.1 to -23.6 blocks a second (terminal about -23.58, even after an 84 to 97 block fall) and the damage stayed 10 to 12 health a fall, as the formula of rule 11 gives.
      The client does not use them for falling (the server uses them for its own entities; `PhysicsValues` defaults are mass 1.0, drag 0.5, gravity `PhysicsConstants.GRAVITY_ACCELERATION` 32).
    - **A Set of -4 made the fall faster.** Engaged at -13.33 with `Velocity.addInstruction(Vector3d(x, -4, z), new VelocityConfig(), Set)` and landed at -36.41 (fastest -37.46), health 100 -> 11. `Hytale:SplitVelocity` splits a player's velocity into the client's own physics velocity and an instructed (external) one; the instruction
      becomes a `ChangeVelocity` packet that sets or adds the external part, which is **added on top of** the client's own falling speed. It cannot clamp gravity by itself. (The default `VelocityConfig` also decays: ground 0.82, air 0.96 per step.)
    - **`fallingFar`** is set by the client (the server only copies it, `MovementStatesSystems.TickingSystem`) and flipped after about 1.0 s and 16.2 to 18.4 blocks of falling at a speed of -31 to -35: it is late, a time or distance rather than a speed. `falling` flips with the first tick off the ground, and the speed trigger (12 blocks a second on 2 ticks) fired at -13.3, about 0.4 s into a fall.
    - `fallingFar`, `falling` and `onGround` all return to false/true on the landing tick, with the client vertical speed at that tick equal to the landing speed.
14. **VelocityConfig and the Mk I ways (read from the classes; the client behaviour is to be measured).**
    - `VelocityConfig` (server and protocol): `groundResistance` 0.82, `groundResistanceMax` 0, `airResistance` 0.96, `airResistanceMax` 0, `threshold` 1, `style` Linear (or Exp). The NPC `MotionControllerBase`, which applies the same config to its own applied velocities, multiplies each velocity by `pow(resistance, step)`
      with the resistance blended between the plain and the Max value by the speed relative to the threshold (and uses the plain value when the Max is negative); a velocity under 0.001 squared is dropped. So the values are per-step multipliers: **1.0 means no decay**, 0 would remove the velocity at once. (The brief assumed 0 means no decay; the class says the opposite.)
    - `Player.setFlying(ref, true, accessor)` sets fly Forced if it already is, otherwise Allowed, and pushes the settings and the flying flag once; with the fly fields written first it is a single push. Flying has no gravity on the client, so an external downward velocity should be the whole vertical motion: that is the flyglide way.
    - The airdrag hypothesis: `airDragMin/Max` (0.96, 0.995, with `airDragMinSpeed/MaxSpeed` 6 and 10) are per-tick multipliers; if the vertical speed obeys v = (v + step) * m the terminal speed is step * m / (1 - m) and the measured -23.58 at m = 0.995 gives step 0.1185, so m = 0.9712 gives -4.
      These fields also shape horizontal air motion. Pending a client run.

## Tried and abandoned

- **Flame Cutter and Flame Beam Cutter (the experiments module).** A flamethrower-style tool and a beam tool with a calibration rig (about 17 `exp cal` commands, particles, a measured angle unit) to place a
  beam on the player. They answered what the engine allows for held-item particles and beams; the Cutter Ray took the useful part (a vanilla-gun look, no projectile). Removed in 0.3.0 with its 34 assets
  and generators.
- **The Suspensor Belt as three Z-slot items with a jump-driven prototype (the lab).** Replaced by two picker toggles and the client's own double tap (rule 12); Mk I (slow descent) is not built.
- **The Z-wheel / Z slot idea.** A utility slot as the way to select equipment; replaced by the picker page (right click on any tool) and the H key for the shield.
- **The Reference art route of the HUD.** Documents whose textures pointed at the vanilla files by relative path (`../../../InGame/Hud/...`), so a texture pack would restyle the bars. The client rejected
  them (rule 5). The Copy route (`VanillaDerived/`) is the only route; the `ArtSource` setting and `/arrakis hud art` are gone.
- **The held-item name label.** The client draws it (`Label #ActiveItemNameLabel` in `Hotbar.ui`, Bottom 126, Height 40, centred). A dev command and an `ItemNameMove` setting tried to move it; the client
  refused the selector (rule 6). The fallback is `BarsLift` (50 or 64).
- **The bisect that found the join failure.** Four build variants (`dupicons`, `refinpack`, `reference`, `probe`, added with `-PhudExtra`) were set up to separate the cause; the evidence and the fix
  (Reference documents out of the pack, icons re-encoded) were enough, so none was needed. Removed with the flag.
- **The first HUD art.** Bars drawn by a pixel rasteriser (`make_hud.js`); they looked crude and were rebuilt from the vanilla Health and Mana art.
- **The ammo gate of the guns.** v1 consumed arrows; now `Arrakis_Gun_Gate` has `Cost 0` and `GunCost.charge` is the place to put an ammo system.
- **Investigation commands:** `weapons diff` (a field-by-field item comparison), `weapons trace`, `shield diag`, `hud probe`, `hud itemname`, `hud art`: removed in 0.3.0.
