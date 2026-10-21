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
    - **Units.** Fly speed 0.5 in Adventure feels like 0.1 in creative, so the belt starts at 0.5 and exposes the numbers.
    - **`jumping` is not "Space held"**: it is true only while rising after a jump and the client clears it the moment flying starts. A driver built on it (the lab's Mk II/III prototype) switched itself off the tick it switched flight on and
      flickered about 18 times a second. Nothing should be driven from `jumping`.
    - **Never push settings or the flying flag every tick.** The prototype did and spammed the client (and drained stamina through the jump spam). Push only on a state change.
    - **Fly speed 0 does not remove jump momentum**: with `verticalFlySpeed` 0 the client velocity was still about +5.5 blocks a second for a moment after the flight began, then the fall read -13.9 (two samples: likely, not proven).
    - **Measured:** the client velocity (not the position delta, which is a half-second average) is the number fall damage uses; at rest it reads about -0.5; the fastest fall seen was -22.08 (just over the 21 threshold). The Z slot item reads
      correctly every tick. The glider route, the settings route, the velocity clamp and fall damage numbers were not exercised and were dropped with the lab.
    - **Engine helper:** `Player.setFlying(ref, boolean, accessor)` starts or stops flying, pushes settings and flag and restores the default fly mode when stopping; it is what ends the belt's flight at 0 Mana.

13. **Suspensor: what the trials proved** (the movement lab, the Mk I builds of 2026-10-19 and 2026-10-20 and the glider proof; the code is removed, the belt is the double tap flight of rule 12).
    - **Movement.** The double tap of Space is client code, gated by the movement setting `fly` (Allowed); the server only sets the setting and watches the flag. Fly speed 0.5 in Adventure feels like 0.1 in creative. `jumping` is not "Space held" (rule 12). Settings and flags are pushed only
      on a change, never per tick (per-tick pushes spam the client and do not hold). Flying in place with an external downward velocity (the "fly-glide") did not hold either.
    - **Falling.** Fall damage starts above a client vertical speed of about 21 blocks a second; terminal speed is about -23.6 (a fall of 84 to 97 blocks still reads -23.58), which costs 10 to 12 health. `dragCoefficient` and `mass` of the physics values do nothing to a player's fall (x30 changed
      nothing; the client does not use them). `Hytale:SplitVelocity` splits a player's velocity into the client's own physics velocity and an instructed (external) one: a `Velocity.addInstruction(..., Set)` sets the external part, which is **added on top of** the client's own falling speed
      (a Set of -4 made the fall faster: landing -36.4, health 100 -> 11), so it cannot clamp gravity. `VelocityConfig` values are per-step multipliers where **1.0 means no decay** (default ground 0.82, air 0.96; 0 would remove the velocity at once).
    - **Flags.** `fallingFar` is set by the client and flips after about 1.0 s and 16 to 18 blocks of falling (speed -31 to -35): late. `falling` flips with the first tick off the ground (so it is also true at the apex of a hop); a speed trigger of 12 blocks a second on 2 ticks fired at -13.3,
      about 0.4 s into a fall. `falling`, `fallingFar` and `onGround` return to false/true on the landing tick, with the client vertical speed at that tick equal to the landing speed. The client velocity (not the position delta) is the number fall damage uses.
    - **Vanilla levers found, not used.** The entity effect `Immunity_Fall` and a 100% Fall resistance (infinite), untested on players. The glider is data: `Template_Glider` has a `Glider` block (TerminalVelocity -2, FallSpeedMultiplier 6, Speed 10, dive values) and interaction chains
      `Primary` (`GroundedCondition`, `DurabilityCondition`, `Condition { Gliding false }`, `ToggleGlider`) and `SwapFrom`; `ToggleGlider` is a client interaction (no server logic), the glide is client physics and the stat `GlidingActive` only mirrors it. Interaction types include `Held`,
      `HeldOffhand` and `Equipped`. An item `Condition` has no "falling"; `EffectCondition` (`EntityEffectIds`, `Match` All or None, `Entity`) lets the server signal with an entity effect. A child item that declares `Interactions` keys keeps the inherited ones.
    - **The glider proof (client log of 2026-10-20).** A Utility-slot item with a `HeldOffhand` chain (`Condition { Gliding false }`, `EffectCondition`, `ToggleGlider`) does open the glide without a click, driven by the server's `falling` flag through an entity effect, and the client takes the glide
      physics from the Utility-slot item. But with three chains (HeldOffhand, Held, Secondary) the glide closed again within 0.03 s and repeated on a fixed 0.70 s metronome (10 cycles in a 50 block drop, about 5.3 blocks a second on average, landings -5 to -13, no damage). The cause was not settled (two
      chains firing, or one re-running); a single-chain build with a pulse signal was written to settle it and was never run before the pivot.
    - **Engine facts kept.** `EffectControllerComponent.addEffect(ref, effect, accessor)` uses the asset's own duration; the four-argument form takes a duration and an overlap behaviour. The inventory is read through the `InventoryComponent` parts, not the deprecated `Inventory`;
      `PlayerReadyEvent` gives only a `Ref`, `AddPlayerToWorldEvent` a holder. `Player.setFlying(ref, boolean, accessor)` starts or stops flying, pushes settings and flag and restores the default fly mode when stopping. A saved stack of a removed item decodes fine and is kept as `Item.UNKNOWN`.
    - **Decision.** Floating down is parked until the engine or the client allows it. The belt is only what works: the double tap flight with a Mana cost.

## Tried and abandoned

- **Flame Cutter and Flame Beam Cutter (the experiments module).** A flamethrower-style tool and a beam tool with a calibration rig (about 17 `exp cal` commands, particles, a measured angle unit) to place a
  beam on the player. They answered what the engine allows for held-item particles and beams; the Cutter Ray took the useful part (a vanilla-gun look, no projectile). Removed in 0.3.0 with its 34 assets
  and generators.
- **The Suspensor Belt as three Z-slot items with a jump-driven prototype (the lab).** Replaced by one picker toggle and the client's own double tap (rule 12); floating down is parked (rule 13).
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
