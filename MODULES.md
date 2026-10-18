# IslandCraft - Equipment of Arrakis: module notes

Current state of the mod (0.3.0). What each module does, how it is built, its settings and commands, and the human test sheet that still applies.
History, abandoned ideas and the client rules learned the hard way are in `ARCHIVE.md`; the release summary is in `RELEASE_NOTES.md`.

Labels used: **confirmed** (a headless boot, a unit test, javap), **inferred** (reasoned from bytecode or vanilla data), **needs a client** (only a player can show it).

## How the mod is put together

- One jar, one package and one asset folder per module (`Equipment_of_Arrakis/<Module>` under `Server/...` and `Common/...`). A module imports only its own package, `core` and `power`; `core`
  imports no module and `power` imports `core` only; only the root package (plugin and config) lists modules. `ModuleBoundaryTest` enforces it.
- `EquipmentModule { id(); setup(ModuleContext); start(); stop(); selfCheck() }`. A module that throws in `setup` is logged at SEVERE and the others go on. The `Modules` list in the config chooses
  which load (`core` always loads). The asset pack loads every asset whatever the config says, so a module that is switched off removes its own assets at `BootEvent` (`ModuleAssets`).
- Config file: `Equipment_of_Arrakis.json` in the plugin data folder, written with defaults when missing, one section per module. A key that no longer exists (for example `Experiments`, `ArtSource`,
  `AlertColor`, `ItemName*`) is ignored when read and dropped the next time the file is saved. A module id in `Modules` that is unknown is ignored.
- Never edited: the Water of Arrakis mod and any vanilla asset. Vanilla art, models, sounds and animations are referenced by path; the only art shipped is ours, plus the derived and re-encoded
  copies under `Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/VanillaDerived/` (the documented exception, see `hud`).
- `core.water` (`WaterApi`, `WaterBridge`, `WaterLink`) is the optional link to the Water mod: compiled against its jar (`compileOnly`, never packed; `-PwaterJar=` points at another copy), new methods
  found by reflection. The manifest lists Water as an optional dependency.
- Test tooling: `tools/headless/run.sh "wait 30" "arrakis modules" ...` boots the server with this jar and types console commands; `/arrakis selfcheck` re-runs every module's checks and prints them
  (`PROBLEM` marks a failure). The generators are `tools/assets/weapons/make_weapons.js` and `tools/assets/hud/make_hud.js`.

## Commands (all operator-only, under `/arrakis`)

| Command | What it does |
|---|---|
| `modules` | each module, enabled or not, how its setup went |
| `selfcheck` | re-runs every module's self-check |
| `worldtest` | writes and reads a block at 0, 200, 0 through `WorldAccess` |
| `tools give` / `state` / `open` | the tools and containers; the held item, tools found, toggles and Mana; opens the picker |
| `weapons give` / `state` | the ten weapons; the held item, its Ability4, the Holtzman toggle |
| `power get` / `set` / `add` / `fill` / `spend` / `max` / `modifier add|remove|list` / `selftest` | the Mana stat (each takes an optional player last) |
| `shield state` / `hit <n>` / `shot` / `cooldown [s]` | the Holtzman field |
| `blood add <n>` / `corpse` | fills the first Blood Bag; spawns and kills a cow two blocks ahead |
| `light on` / `off` / `state` | Personal Light |
| `hud on` / `off` / `state` / `demo <bar> <percent>` / `component hide|show <Name>|reset` | the four bars |
| `hud lb|rb|lt|rt [x[,] y]` / `... reset` / `hud nudge reset` | nudge a bar (lb Health, rb Water, lt Power, rt Heat Exposure) |

## core

- `ToolRegistry` (picker tools by item id; detection is by this list, never by a prefix), `ToggleRegistry` (passive toggles: id, order, label key, callbacks; `forget(uuid)` resets), `GiveRegistry`
  (what `tools give` hands out), `ToolUse.fired(PlayerRef)` with listeners (the shield listens; only the Cutter Ray calls it), `ContainerItem` (an amount in stack metadata mirrored to the
  durability bar), `ContainerInventory` (carried containers, first with room: hotbar, then main inventory, never the backpack or the utility slots), `Notice` (a short chat line, at most one a
  second per player and key), `ModuleAssets`, shared command groups (`CommandRoot.group`), `WorldAccess`.
- **The H key.** `ToggleRegistry.press` is the one way a player flips a toggle: the picker page calls it with no debounce, the key through interaction type `Arrakis_Toggle` with 0.2 s (outcomes
  UNKNOWN / DEBOUNCED / ON / OFF). Asset `Arrakis_Toggle_Holtzman` (`ToggleId Holtzman`) and root `Arrakis_Root_Toggle_Holtzman` (`RequireNewClick`, `Cooldown 0.2`, `Rules {"BlockedBy": []}`, so H
  works mid-swing and mid-shot and cancels neither). `Ability4 = Arrakis_Root_Toggle_Holtzman` is on the seven hand tools and the ten weapons, not on the Blood Bag. Toggles reset on disconnect,
  world change and death (`ToggleResetSystem`).
- `WorldAccess` threading: the raw `ChunkStore` futures complete off the world thread; loaders hop back with `thenApplyAsync(..., world)`. NPC dummies cannot be spawned headlessly, so
  `power selftest` prints SKIPPED there.

## power

The vanilla **Mana** stat is Power. No custom stat, HUD or recharge; vanilla regeneration is untouched (logged at start). Service `Power.get()`: `get/max/fraction/has/isCreative`, `spend` (all or
nothing, creative spends nothing, tolerance 0.001), `spendUpTo`, `add`, `set`, `fill`; arithmetic in `PowerMath`. `PowerModifiers`: `putMax/removeMax/list` keyed by the caller's id; ADDITIVE adds to the
max, MULTIPLICATIVE multiplies. `ManaRefusal.say`: "This needs N Mana.", once a second per player (setting `ShowMessages`). Settings: `LogStatDefinition`, `ShowMessages`.
**Needs a client / by hand:** raising the max does not raise the current value (`power set 80`, `modifier add t add 16`, `get`); lowering the max clamps; a modifier survives equipping gloves; the bar follows
`power set`.

## catalog

Creative tab `IslandCraft` (`Server/Item/Category/CreativeLibrary/IslandCraft.json`, order 4 after the vanilla tabs) with children Tools, Weapons, Armor, Resources (vanilla tab icons by path; our own
`Arrakis.png` / `ArrakisActive.png`, the only art of the module). Convention: an item lists `"Categories": ["IslandCraft.Tools"]` (or Weapons / Armor / Resources) by string, so no module imports catalog.
Weapons also list `Items.Weapons` (see `weapons`). Armor and Resources are empty tabs (whether the client hides empty children needs a client).

## picker

`ToolPicker` (page + swap adapter), `ToolSwap` (pure rule), `PickerCommands`; assets `Arrakis_Picker_Open` / `Arrakis_Picker_Secondary` and `Arrakis_Picker_{Page,Entry,Toggle}.ui`. Every tool's
`Interactions.Secondary` is `Arrakis_Picker_Secondary`. Right click on a tool opens a page listing the registered tools in the hotbar and main inventory in registry order (the held one marked "In hand",
not clickable), then every registered toggle with ON / OFF. A toggle click flips it and rebuilds; a tool click does one `ItemContainer.swapItems` (the chosen tool into the active slot, the old one
into the chosen one's slot; metadata travels; no free slot needed) and closes; if the inventory changed since, it says so and swaps nothing. The page opens only from a held Arrakis tool.

## cutter (Cutter Ray)

`Arrakis_Tool_Cutter_Ray`: the vanilla `Weapon_Gun` by path. Primary = [`Arrakis_Cutter_Shot`, `Arrakis_Cutter_Effects`]. The shot (type `CutterShotInteraction`) needs `ManaPerShot` 10, spends it
(creative pays none), casts from the eye `Range` 5 and hits the block with `BlockHarvestUtils.performBlockDamage`, an unbreakable stand-in `Tool_Pickaxe_Crude`, `DamageScale 1` (one shot = one swing
of the crude pickaxe by construction). Calls `ToolUse.fired`. Settings are data in `Arrakis_Cutter_Shot.json` and `Arrakis_Cutter_Effects.json` (`RunTime 0.25`). Not measured: shots-to-break against the
real pickaxe.

## compactor

Model, texture, aim animation, `Arrakis_Compactor` animation set, hum effect, sand-dust particle, `CompactMath`, `BeamMath`, `CompactorInteraction`, `CompactorTrail`, `CompactorSystem`. Primary (left
click held), 1 Mana per second while compacting, stops at 0 (creative never), range 3, `CompactSeconds` 1, groups `Sand`, exclusions `Arrakis_Sand`, `Arrakis_Worm_Ripple_Layer_`, `Arrakis_Worm_Sunk_`
(all data in `Arrakis_Compactor_Aim.json`). The block is never broken: the crack is the block health lowered to at most 90 % and given back at the end of each cycle.

## litrejon and harvester

- `Arrakis_Tool_Litrejon` (separate from the Water mod's own `Arrakis_Litrejon`, which is untouched): metadata `Arrakis_Water_Units`, settings `Capacity 500`, `StartsFull false`, `PourPerClick 50`.
  Left click (`Arrakis_Litrejon_Pour`, every 0.5 s while held) moves min(flask, 50, what the player lacks) into the player's water through the Water link; right click opens the picker. Without
  Water: "Water of Arrakis is needed to pour from the LitreJon."
- `Arrakis_Tool_Dew_Harvester`: the vanilla `Tool_Sickle_Crude` by path. Primary = gate / swing (1 s) / finish. The gate refuses (nothing spent, a short message) unless the block under the crosshair
  within 3 is `Arrakis_Primrose`, a LitreJon is carried and has room, and there are 10 Mana; the finish puts 10 water into the first LitreJon with room.

## bloodbag and extractor

- `Arrakis_Blood_Bag`: parent `Potion_Template`, Red texture by path; metadata `Arrakis_Blood_Units`, settings `Capacity 500`, `StartsFull false`; Primary and Secondary do nothing; not a picker tool.
  `blood add <n>` fills the first bag with room.
- `Arrakis_Tool_Blood_Extractor`: the vanilla Handgun by path, no shot. Primary = gate / poke (1 s, the `GuardBash` animation) / finish. Refusals in order, nothing spent: no corpse in range and
  aim; already used; no bag; all bags full. No Mana. A corpse is a non-player entity with a `DeathComponent` (and an `NPCEntity`, data `RequireNpc`); found with `getAllEntitiesInSphere` then a ray
  test (`CorpseAim.pick`: `Range` 2.5, `AimRadius` 0.9, `AimHeight` 0.5). One body, one use (a runtime set of claimed refs). The gate forces the crouch through the server `MovementStatesComponent`
  (data `ForceCrouch`; fallback `RequireCrouched`). The finish gives `BloodPerBody` 10. **Needs a client:** whether the owner's client fights the forced crouch.

## placeholders and light

- Placeholders `Arrakis_Tool_Vehicle_Summoner`, `Arrakis_Tool_Build_Repair`: the crude pickaxe's look by path, no Primary, Secondary = the picker, "Not built yet."
- Personal Light: a toggle on the picker page, no item. The light is the entity component `DynamicLight` added while the toggle is ON and removed when OFF by `Reconcile` (a system that makes the entities
  match the ON set every tick, so a death or disconnect cannot leave a stuck light). Colour from the torch block (`Furniture_Crude_Torch`): radius 0, brightest channel 11, tinted cool white
  (final red 9, green 10, blue 11). Settings (`Light`): `TorchItemId`, `Radius/Red/Green/Blue` (-1 = from the torch), `TintRed/Green/Blue`. Reset to OFF on relog, death, respawn and world change.

## shield (Holtzman Shield)

A toggle (`Holtzman`: picker page or the H key), no item. Entity effects, ModelVFX, particles and eight sounds (vanilla textures and sounds by path). `HoltzmanSystem`, `HoltzmanDamageSystem` (engine
filter damage group, after armour, cancels the hit), `HoltzmanField`, `ShieldCooldown` (with a modifier hook that nothing uses yet), the shot hooks `ShieldShotLegacySystem` / `ShieldShotSystem` (any
projectile a player creates) and `ToolUse.fired` (the Cutter Ray). Power is Mana (`ManaPerDamage 1.0`; creative pays nothing). A hit that takes Mana to 0 or is at least the Mana left is absorbed whole and
the field breaks (the toggle stays ON); it returns at `RearmMana` 100 (or the maximum if lower). A shot of the player's drops the field for `CooldownSeconds` 1.0. The toggle is OFF after a relog, a
world change and death. Settings (`Shield`): `EffectId`, `ImpactEffectId`, `ManaPerDamage`, `RearmMana`, `FieldRadius`, `FieldCenterHeight`, `CooldownSeconds`.

## weapons

- **Melee (8):** `Arrakis_Weapon_{Sword,Daggers}_{Crude,Atreides,Choam,Harkonnen}`, children of `Weapon_Sword_Crude` / `Weapon_Daggers_Crude`, so every damage number, sound and swing is the Crude item's.
  Overridden by `tools/assets/weapons/make_weapons.js`: `Model`, `Texture`, `Icon`, `Quality` (Atreides = Thorium, Choam = Cobalt, Harkonnen = Adamantite), `Recipe: null`, `MaxDurability 0`,
  `DurabilityLossOnHit 0`, `MaxStack 1`, the names, and the `Interactions` map (the template's Primary, Secondary, Ability1, plus Ability4).
- **Guns (2):** `Arrakis_Weapon_Maula_Pistol` (parent `Weapon_Handgun`) and `Arrakis_Weapon_Dartgun` (parent `Weapon_Assault_Rifle`). One click one dart, `RequireNewClick`, cooldown 0.33 s, damage 1,
  speed 100, smoke only, the vanilla bash on Secondary. Range rule (`DartMath`, `DartFlight`, `DartFlightSystem`, `DartRangeSystem`, `DartRangeDamageSystem`): a dart is removed 40 blocks from where
  it was launched and a hit past 40 does no damage (settings `Range`, `ProjectileIds`). Each Primary root runs `Arrakis_Gun_Gate` with `Cost 0` through `GunCost.charge`: the place for an ammo system.
- **Categories:** `["Items.Weapons", "IslandCraft.Weapons"]`. `Items.Weapons` is what makes the client draw the vanilla weapon ability HUD (see `ARCHIVE.md`); the weapons also appear in the vanilla Weapons tab.
- Firing a dart drops the Holtzman field like any projectile; a melee swing never does.

## hud (the four bars)

- Two rows above the hotbar: Power (teal, bolt) over Health on the left, Heat Exposure over Water on the right. The vanilla Health and Mana parts are hidden through `HudManager.hideHudComponents`
  (re-hidden every tick; nothing else reads them). Our HUD is a `CustomUIHud` (`HudView`, key `EquipmentOfArrakisBars`) built from `Arrakis_Hud_Copy.ui` / `Arrakis_Hud_Copy_Creative.ui` (written by
  `make_hud.js`); `HudRuntime.tick` reads the four values and sends only what changed. The module appends only a document the pack has (`HudDocs`).
- **Sources:** Power = `Power.fraction`, Health = the `Health` stat, Heat Exposure and Water = the Water mod (`getExposure`, `getWater`); a bar that cannot be read is not drawn (no Water: only the left half).
  At start the module switches the Water bars off (`setHudHidden`, if the Water jar has it); `stop` switches them back on. Exposure colour: orange up to `ExposureRedStartPercent`, then a blend to red
  (the Water mod's rule when its jar has the accessors, else the Hud settings).
- **Layout:** the numbers are the vanilla ones read from the client files (`tools/assets/hud/vanilla-metrics.json`, compared with `HudMetrics.java` by `HudMetricsTest`): hotbar 702 x 78, bar row bottom
  120, bar 318 x 12, icon 24 (Top -8, gap 4), inner gap 5, `RowGap` 26 between the rows. A fill is two groups sharing `FlexWeight` (value and 1000 minus value), tinted by `Background.Color`;
  the 9 pixel border keeps the slanted end. Creative mode uses the creative documents (rebuilt when the game mode changes).
- **The low alert:** below `FlashBelowPercent` (5) a bar whose switch is on (Health and Water by default) shows a red fill layer and flips every `AlertFlipSeconds` (0.25) between an opaque and a dimmed
  outline, by showing and hiding overlay groups (never a colour with an alpha).
- **Nudges:** `hud lb|rb|lt|rt [x[,] y]`: the step is (value - 1) x 10 virtual pixels (1.1 = +1, 0.9 = -1, 1.5 = +5, 1 = 0), x + right, y + up, accumulating; saved in `Hud.Nudges` through
  `ModuleContext.saveConfig`; applied to everyone at once. A left bar cannot be pushed past the middle of the screen.
- **The held item name** (client label at Bottom 126, Height 40) overlaps the bottom row and cannot be moved from the server. `BarsLift` raises both rows: **50** clears the name by 4 pixels (and lands the
  bottom row on the stamina bar), **64** clears the name and the stamina bar. Default 0.
- **Art in `VanillaDerived/`** (comes from Hypixel client art and Assets.zip; the user decides what ships when the mod is published): derived, `StaminaIconWhite`, `Neutral{Health,CreativeHealth,Mana,CreativeMana}`
  fills (grey copies so the Power and Heat Exposure bars can be tinted), `ErrorLeft`, `ErrorOutlineLeft` (mirrored overlays); plain copies of the Health / Mana art; `Misc.png`, `Natural-Fluid.png` re-encoded
  from Assets.zip (a byte copy raises the duplicate-asset count).
- **Settings (`Hud`):** `BarsLift`, `RowGap`, `PowerColor`, `PowerIconColor`, `WaterIconColor`, `ExposureIconLow/High`, `ExposureRedStartPercent`, `ExposureColorOrange/Red`, `FlashBelowPercent`,
  `AlertFlipSeconds`, `FlashPower/Health/Exposure/Water`, `MinPercentChange`, `DemoSeconds`, `Nudges`. Every colour sent to the client is validated to plain `#rrggbb` first (`HudMath.safeColor`).
- Guard tests: the run-time selector list (`HudSelectorsTest`), plain `#rrggbb` colours, no `Bar.Color` on a textured ProgressBar, overlays shown and hidden (never an alpha), every texture exists at the
  vanilla pixel size, the documents in the pack are the Copy ones only.

## Human test sheet (a client; `/arrakis tools give` and `/arrakis weapons give` first; survival and creative)

1. **Join** the test world and a fresh world, with and without the Water mod: it joins, no error in the client log (`%APPDATA%\Hytale\UserData\Logs\*_client.log`; the server log only says "Crash").
2. **Creative menu**, IslandCraft tab: Tools has the Cutter Ray, Compactor, LitreJon, Dew Harvester, Blood Extractor, Vehicle Summoner, Build/Repair Tool, Blood Bag with icon and name; Weapons has the ten; only
   the two containers show a bar; nothing wears or breaks. The weapons also show in the vanilla Weapons tab.
3. **Picker:** right click any tool: it opens with the tools you carry ("In hand" marked) and then Holtzman Shield and Personal Light with ON / OFF. A tool click swaps (right model and animation, old tool in
   the old slot, works with a full inventory, the LitreJon keeps its water). Escape closes with no change.
4. **Cutter Ray:** hold left click: 4 shots a second, 10 Mana each, stops after 10 at full Mana, "This needs 10 Mana." at 9 or less; mobs are not hurt. Break sand, stone and an ore with it and with the real
   `Tool_Pickaxe_Crude`: shots and swings should match.
5. **Compactor:** hold left click on sand within 3: crack, a drop a second, hum, the block stays; 1 Mana a second; stops at 0; nothing on other blocks.
6. **LitreJon** (Water on): left click gives up to 50, only what you lack (at 81 water: 19), the bar follows, empty and full messages; right click opens the picker. Without Water: the message.
7. **Dew Harvester:** on a Primrose with a LitreJon with room: a swing a second, +10 water in the flask, -10 Mana, Primrose and your own water unchanged; no Mana, no flask, full flask or another block: no
   swing and a message.
8. **Blood Extractor:** `/arrakis blood corpse`, left click within 2.5: crouch (do you and others see it?), the jab, +10 blood in the first bag; the same corpse again: refused; no bag or full bags: refused
   with no animation; no Mana spent. `/arrakis blood add 495` fills a bag to 500.
9. **Weapons:** melee swings with the same damage for all four houses, vanilla guard and bash, the signature move still works, no durability bar. Maula Pistol and Dartgun: one click one dart, at most 3 a
   second, 1 damage, nothing past 40 blocks, smoke only, no ammo or Mana. **Holding any Arrakis weapon shows the vanilla ability HUD on the right** (the Items.Weapons category).
10. **H key** on each of the ten weapons and seven tools: the shield toggles (field, hum, chat line "Holtzman Shield: ON"), the picker page shows the same state; H during a swing or a shot toggles without
    cancelling; holding H does not flicker; nothing on the Blood Bag or an empty hand; OFF after death, respawn and relog.
11. **Holtzman:** `/arrakis shield hit 20` costs 20 Mana; firing the Cutter or a dart drops the field for 1 s and it returns; `/arrakis shield hit 500` breaks it with the shatter, the toggle stays ON and it
    returns at 100 Mana; OFF removes everything.
12. **Personal Light:** ON gives a torch-equivalent cool white light other players see (compare with a held `Furniture_Crude_Torch` in the dark); OFF removes it; relog and death leave it OFF.
13. **The four bars:** Power (teal, light-blue bolt) over Health (vanilla pink-red) on the left, Heat Exposure (amber, yellow glyph) over Water (vanilla blue) on the right; the vanilla Health and Mana bars
    are gone; stamina, oxygen, status icons and the Z slot untouched. `/arrakis hud demo health 3` and `water 3`: red fill, overlay and outline flash every quarter second, stopping at 5; `power 3` and
    `exposure 3` do nothing unless `FlashPower` / `FlashExposure` is true. Colours: are the tinted bars as bright as the vanilla ones? Partly full (10 %, 50 %, 90 %): the slanted end holds its shape.
14. **Creative mode:** switch and back: the left bars use the creative health fill and heart, the right bars the creative mana fill; Power and Heat Exposure keep their colours.
15. **Nudges:** `hud lt 1.1,1` moves Power one pixel right, `lt 0.9,1` back, `lt 1,1.5` five up, `lt` shows the offsets, `lt reset` clears, `hud nudge reset` clears all. A restart keeps them; a second
    player sees them at once; a left bar stops at the middle of the screen.
16. **Water on and off:** Heat Exposure and Water follow the Water mod; with `hud off` the vanilla bars and the Water bars come back; `hud on` reverses it; without Water only the left half shows and the
    log has no error. Resize the window and change resolution: the bars keep their place over the hotbar.
17. **The old test world** (`Arrakis Equipment Test`) with its old config file: it loads, everything above works, and the file is rewritten without the removed keys.
