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
- Documented exceptions to "vanilla by path": the HUD `VanillaDerived/` images, the Thumper icon (the flame taken out) and the re-encoded torch model and texture under `Common/Items/Equipment_of_Arrakis/Thumper/`
  (see `thumper`). Nothing else ships a vanilla asset.
- `core.water` (`WaterApi`, `WaterBridge`, `WaterLink`) is the optional link to the Water mod: compiled against its jar (`compileOnly`, never packed; `-PwaterJar=` points at another copy), new methods
  found by reflection. The manifest lists Water as an optional dependency. `core.worms` is the same for Worms of Arrakis (the Thumper registers itself as a totem; see `thumper`; `-PwormsJar=`).
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
| `blood add <n>` / `corpse [role]` | fills the first Blood Bag; spawns a mob (default Cow) two blocks ahead and kills it a second later |
| `blood corpses` / `corpses clear` | the extended bodies (total, your chunk, busiest chunk, seconds left, cut by the cap, and the nearest five with their Status animation); makes them all vanish |
| `thumper give [n]` / `state` / `clear` | Thumpers into the inventory (default 4); the standing Thumpers, the Worms link and the sand set; removes them all |
| `suspensor glider ...` / `mk1 ...` / `probe on|off` / `drop <n>` | Mk I state and tuning / the fall probe / take yourself n blocks up |
| `suspensor state` / `set mk2|mk3 <h> <v>` / `off` | the caller's belt and the movement settings in force / live speeds, saved / turns the caller's belt off |
| `light on` / `off` / `state` | Personal Light |
| `hud on` / `off` / `state` / `demo <bar> <percent>` / `component hide|show <Name>|reset` | the four bars |
| `hud lb|rb|lt|rt [x[,] y]` / `... reset` / `hud nudge reset` | nudge a bar (lb Health, rb Water, lt Power, rt Heat Exposure) |

## core

- `ToolRegistry` (picker tools by item id; detection is by this list, never by a prefix), `ToggleRegistry` (passive toggles: id, order, label key, callbacks, optional exclusive group; `forget(uuid)` resets), `GiveRegistry`
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
- **Long-lived bodies** (same module, config section `Extractor`: `CorpseSeconds` 120, `ExtractedSeconds` 1, `MaxCorpsesPerChunk` 5, `ExcludedRoles` []). The engine's `NPCSystems.OnDeathSystem` adds a
  `DeferredCorpseRemoval(death animation time, deathParticles)` to a killed NPC; `CorpseTimerSystem` (a `RefChangeSystem` on that component, so it always runs after the engine's) replaces it with one of
  `CorpseSeconds` and the same particles. `DeathSystems.TickCorpseRemoval` counts it down and `CorpseRemoval` removes the entity, so loot, experience, kill feed and the death effect are untouched.
  The corpse rule is the one `CorpseAim.isCorpse` the Extractor uses; players are not in the query; a role on `ExcludedRoles` (case-insensitive, `NPCEntity.getRoleName()`) keeps the vanilla timer;
  `CorpseSeconds` 0 or less switches the extending off. `Corpses.LEDGER` (`CorpseLedger`) remembers every body handled, so the replacement is never extended again, counts bodies per chunk column
  (`ChunkUtil.indexChunkFromBlock`) and cuts the oldest of a chunk to `ExtractedSeconds` when it would hold more than the cap; refs of removed entities are pruned; nothing is saved. Bodies on a chunk
  border can add up to 5 in each neighbouring chunk; there is no server-wide cap. A finished draw (blood really added) sets the body to `ExtractedSeconds`; a refused or cut-short poke leaves its timer.
  A mob whose role has death animation time 0 gets no timer from the engine and is removed at once as before. `/arrakis blood corpse` kills through `DeathComponent.tryAddComponent`, the normal path.
  `blood corpses` prints total, your chunk, busiest chunk, seconds left (nearest ten) and the number cut by the cap; `blood corpses clear` gives every extended body the quick removal.
  **Only bodies that hold their pose are extended.** A death clip is a `.blockyanim` with a `holdLastKeyframe` flag; with false the body stands up when the clip ends. `DeathClips` finds the clip the death
  system would play (`Entity.DefaultAnimations.getDeathAnimationIds(states, cause)`, the first id the model binds, the clips of its animation set; every clip must hold), reads the flag from the bytes in
  `CommonAssetRegistry` (vanilla assets included: confirmed, the self-check prints the Cow and Squirrel clips) and caches per model. The first body of each model logs
  `corpse: <role> model <id> death clip <path> holds=<b> -> extended <s> s | vanilla timer`. A non-holding mob keeps the vanilla timer and is not in the ledger, unless `ExtendNonHolding` is true.
  Fallback if the bytes cannot be read: `corpses_nonholding_models.txt`, generated by `node tools/assets/corpses/make_hold_list.js` (needs `unzip`): 26 models do not hold (Archaeopteryx, Crawler_Void,
  Eye_Void, Gecko, Ghoul, Goblin_Duke, Goblin_Duke_Large, Goblin_Gobliterator_Phase3, Golem_Firesteel, Grooble, Hyena, Lobster, Meerkat, Molerat, Moose_Bull, Moose_Cow, Raptor_Cave, Rex_Cave,
  Scarak_Defender, Spark_Living, Squirrel, Tortoise, Trillodon, Turkey, Zombie_Aberrant, Zombie_Aberrant_Big). Cause-specific clips (Death_Fly and the like) are not on the list; the run-time route looks at them.
  **Crouch:** the gate needs the player already crouching (`RequireCrouched` true, `ForceCrouch` false by default; "Crouch to draw blood.", before any animation, nothing spent, the body keeps its timer); both settings keep their names, so an old config that sets `ForceCrouch` true still reads and then forces the crouch as before. **Test command:** `blood corpse [role]` spawns the mob, waits `TestKillDelaySeconds` (1; a scheduled hop back to the world thread) so the client has the entity, then kills it with
  `DamageSystems.executeDamage` (cause Physical, the weapon path); only if that did not add a `DeathComponent` is it added directly. It prints the role, the death animation id, bound or not, and how it killed.
  `blood corpses` also lists the 5 nearest extended bodies within 16 blocks: role, model, seconds left, the Status animation (`ActiveAnimationComponent.getActiveAnimations()` at the Status slot) and holds.
  **Needs a client:** the fall, whether the body stays lying for the 120 seconds, the vanish effect.
- **The poke:** `Arrakis_Extractor_Poke` plays the Handgun item animation `Interact` (the dry-fire click vanilla `Gun_Shoot_Fail` uses; defined in `Server/Item/Animations/Handgun.json`, clips
  `Handgun/Interact.blockyanim` third person and `Interact_FPS.blockyanim` first person, 20 frames, not looping, third person holds its last frame, blending 0.1 s; vanilla gives it 0.2 s). The animation
  runs for the interaction RunTime (the gate PokeSeconds, 1 s), so the pose holds after the short motion; `ClearAnimationOnFinish` ends it. Config `PokeAnimation` (default `Interact`; the old
  pistol bash is `GuardBash`) and the gate PokeSeconds are written over the asset's two values at boot (reflection; the cached packet is dropped), so a server restart applies them.

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

## thumper (Thumper)

- **What it is.** `Arrakis_Thumper` (IslandCraft tab, Tools; `/arrakis thumper give [n]`): a consumable, MaxStack 16, no recipe, no durability, not a picker tool. Right click, **crouching**, on the block next to you in the direction you look (one of the eight around your own block) plants an invulnerable
  deployable (`Arrakis_Thumper`) on the sand there that thumps softly until something removes it. MaxStack 1: one Thumper per slot (`/arrakis thumper give 3` fills three slots; consuming one removes the item). The Worms of Arrakis mod already treats a registered deployable id as a totem (aggro, the 10 s stalk,
  the breach that removes it); this module registers the id there (see the Worms link below) and owns nothing of that: the aggro numbers (`TotemStartAggro`, `TotemAggroPerSecond`), the ripple
  and the sand disturbance stay in Worms.
- **The item (look).** The vanilla Temple Emerald torch by path: `PlayerAnimationsId Torch`, `Primary Root_Unarmed_Attack_Swing_Left`, the vanilla swing interaction vars, the same icon properties.
  Because the vanilla torch is a block item whose held model comes from its BlockType, ours carries a `BlockType` with the same `CustomModel` / `CustomModelTexture` (draw type Model, hitbox
  Torch) and nothing else: no Light, no Particles (the green flame is the particle `Torch_Fire_Green` on the Flame node; the model has no geometry there), no Support rules, no State or Use
  interaction, no ambient sound. Its Secondary is `Arrakis_Thumper_Secondary`, not `Block_Secondary`, so it is never placed as a torch block. **Not seen on a client:** that this gives the same held
  pose and model as the vanilla torch. The torch model's root node is `L-Attachment` and the Torch animation clips live under `Off_Handed/Torch/`, so the vanilla torch is held in the left hand by
  the animation set; ours uses the same set, so no `R-Attachment` node should be needed and no copy of the held model was made. If the hand shows nothing, the alternative is `Model` /
  `Texture` on the item instead of a BlockType (what the Blood Extractor does with the pistol). The vanilla icon shows the lit torch, so the icon is derived (below).
- **The chain.** `Arrakis_Thumper_Secondary` (RequireNewClick, 0.5 s cooldown): `Arrakis_Thumper_Gate` (our interaction type; refuses with a chat line and nothing consumed, in this order: not
  crouching "Crouch to plant the Thumper."; `MaxLivePerPlayer` reached; no ground or no room in the chosen column "There is no room to plant it there."; the ground is not sand by Worms' rule "A Thumper needs
  sand to stand on."; another Thumper within 0.8 blocks "A Thumper is already there."), `Arrakis_Thumper_Place` (our interaction type, spawns at the spot the gate chose),
  `Arrakis_Thumper_Consume` (the vanilla `ModifyInventory`, RequiredGameMode Adventure, AdjustHeldItemQuantity -1: nothing is used up in creative, the same test as `Power.isCreative`).
- **Crouch.** Read by `core.Crouch.isCrouching` from the server's `MovementStatesComponent`: `crouching` (the client sends its movement states every tick) and not `forcedCrouching`, which is a
  crouch something else set (a low ceiling, or a mod writing the state; the Extractor's old `ForceCrouch` did) and is not the player's choice. The pure part (`Crouch.counts`) has a unit test. The same helper
  serves the Blood Extractor. Creative mode: the same rule. **Not yet seen on a client:** that the flag is set while the key is held (the Extractor already read it the same way).
- **Where it goes (the eight cells).** `ThumperSpot.neighbour` takes the horizontal part of the look vector (`TargetUtil.getLook`; pitch is ignored, looking down still gives the direction) and rounds its
  angle to the nearest of the eight compass directions (45 degree sectors, edges at 22.5 degrees, wrap-around at 360), giving (dx, dz) with each in -1, 0, 1, never both 0. The target column is the player's
  block column (`floor(x), floor(z)`) plus that. The ground is the first block that is not air looking down that column from `feetY + 1` to `feetY - 2` (`feetY = floor(y + 0.05)`); the two blocks
  above it must not be solid (the model is two blocks high); with no ground or no room the placement is refused ("no room"). The Thumper stands at the centre of the column, at `ground + 1` exactly.
- **The Worms rule, mirrored at that exact position.** `ThumperSpot.wormsOnSand` is Worms' `AggroManager.onSand`: floor x and z, start at `floor(y - 0.1)`, go down `ceil(tolerance)` blocks to the first block
  that is not air (any id above 0), which must be in the sand set; a column that is not ticking is "not sand" too (checked in the gate). The tolerance is Worms' `SandTolerance`, read by reflection when linked, else 1.0.
  Because the Thumper stands at `ground + 1`, the block the rule looks at first is the ground itself, so a Thumper the gate accepts is accepted by Worms. One Thumper per spot: another live Thumper (or a placement
  not yet seen) within 0.8 blocks refuses it.
- **How it is spawned at a computed position (option a).** `Arrakis_Thumper_Place` is our own interaction type (`ThumperPlaceInteraction`) whose JSON `Config` is the same deployable config the vanilla
  `SpawnDeployableAtLocation` takes (the deployable `Arrakis_Thumper` is unchanged). It calls `DeployablesUtils.spawnDeployable(buffer, store, config, owner, position, rotation, "UP")`, the call the vanilla
  interaction makes, with the position the gate stored (`ThumperPending`, one spot per player, valid 2 s) instead of a fixed offset. **Owner:** `context.getOwningEntity()`, the entity itself if there is none, as in
  the vanilla one; `DeployableComponent.init` stores that ref and its uuid, so `getOwnerUUID()` (what Worms reads) is the placing player. `spawnDeployable` also makes sure the owner has the
  `DeployableOwnerComponent` that counts live Thumpers, so the engine's live count (`MaxLiveCount`, written from `MaxLivePerPlayer` at boot) works as before.
- **The ignored Thumpers (Worms log of the first test).** Worms logged "not on sand: ignored" for Thumpers at about 50 91 10, 50 92 8 (twice) and 50 92 7. What I could establish: the old gate and Worms use the same
  block reads, the same "first block that is not air" walk, the same two-block depth (tolerance 1.0) and the same sand set (the log shows Worms' set is only `Arrakis_Sand`, id 6138, and the gate read that same set), so
  at the same position they cannot disagree; Worms prints its positions rounded (`%.0f`), so the exact ones are not in the log; and the old gate never logged where it judged. The two differences left are (1) the position:
  the old gate judged the player's position at the click and Worms judges the entity's position at least 1.5 s later (it waits for the entity to be still), so a Thumper that ended up in another column or at another height
  than the player (a dune edge, an entity nudged out of a block) is judged by different blocks; (2) Worms answers "not sand" for a column that is not ticking, which the old gate did not check. I could not prove which
  happened. The fix removes the first by judging the exact spawn position with the mirrored rule (and the second is now checked), and the plugin now logs at INFO every spawn ("Thumper spawned at x y z ... Worms rule with
  tolerance 1.0 says sand", with the ground block and the sand set) and, when the entity is first seen, where it really stands, how far that is from the intended spot and what the Worms rule says there; a difference or a
  "NOT sand" is a WARN starting "Thumper MISMATCH". If one appears, that line names the cause.
- **The deployable.** A `Type Aoe` config inline in `Arrakis_Thumper_Place`: Id `Arrakis_Thumper`, DamageAmount 0 (the engine skips damage at 0 or less; it still looks for entities in a 0.1 radius
  every `DamageInterval`, set to 5 s), no effects, no particles, no sounds, Invulnerable true (the engine adds the `Invulnerable` component), model `Arrakis_Thumper`.
  **Collision:** the engine adds a `HitboxCollision` component only when the config names a `HitboxCollisionConfig`; ours names none (the vanilla totems use `HardCollision`), so it cannot block or
  push the player. **LiveDuration:** the engine adds a despawn timer only when `liveDuration x 1000 > 0`, so `0 is forever` (no huge number needed). **MaxLiveCount:** the engine does not refuse a
  fourth; it removes the oldest, so the limit is enforced in the gate and the JSON value is overwritten at boot from `MaxLivePerPlayer` (and the live duration from `MaxLiveSeconds`; `ThumperSetup`,
  reflection, the same method as the Extractor poke).
- **The look of the standing Thumper.** Model asset `Server/Models/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.json`, MinScale = MaxScale = 1.0. The prompt said the placed torch block uses
  `CustomModelScale 0.6`; the vanilla item JSON has no such key (its `Scale 1.4` is the held and dropped size, and 0.6 is the scale of the flame particle), so 1.0 is the size of the placed block.
  Change `MinScale/MaxScale` in that file if the standing torch looks wrong. **Not seen on a client** (size, and that the model stands upright as an entity).
- **The beat.** `ThumperSystem` (an entity-ticking system over deployables) moves one `BeatClock` per Thumper (a map by uuid in `ThumperBook`, pruned two seconds after the entity stops ticking, nothing
  saved) and, for each hit due, plays `SFX_Arrakis_Thumper_Thump` with `SoundUtil.playSoundEvent3d(index, SFX, position, accessor)` on the world thread: one world sound at the Thumper, every player in range
  hears it with the event's falloff (nothing is sent to players far away). A beat is `BeatSeconds` (1.0) long: a hit at its start, a second hit `DoubleThumpGapSeconds` (0.25) later, then a rest; a
  gap of 0 or less is a single hit. The first beat is at the first tick after the placement. A long pause (lag, an unloaded chunk) gives at most one beat, never a burst.
- **The sound.** The vanilla clip `Sounds/PlayerActions/Footsteps/Block_Steps/Walk_Sand_01.ogg` by path (the one picked in the audition). **Units**, read from the codec and confirmed by the self-check on
  the first boot: the event's `Pitch` is in **semitones** (range -12 to 12) and its `Volume` in **decibels** (-100 to 10); the loaded event stores them as a playback-rate ratio and a gain (-6 became
  0.7071, +3 became 1.4125, so the audition's rate of 0.7 is -6 semitones). A layer has its own `Volume` (dB) and only a *random* pitch offset (`RandomSettings MinPitch/MaxPitch`), so a fixed pitch is
  set on the event. `Parent SFX_Attn_Loud`: full volume to 15 blocks, silent at 45. `MaxInstance` 16, `PreventSoundInterruption` true. The defaults are in the JSON (-6, +3); `ThumpPitchSemitones`
  and `ThumpVolumeDb` in the config are converted and written over the loaded event at boot. AudioCategory is `AudioCat_SFX` (the category of the attenuation parents; the other Arrakis events use
  `AudioCat_Weapons`, which is the wrong slider for a footstep). **Not heard yet:** how it compares with the audition.
- **The Worms link** (`core.worms`: `WormsApi`, `WormsBridge`, `WormsLink`, the same shape as `core.water`). Compiled against the Worms jar (`compileOnly`, never packed; `-PwormsJar=` points at
  another copy; default `../WormsOfArrakis/build/libs/IslandCraft-WormsOfArrakis-0.1.0.jar`); the manifest lists `IslandCraft:IslandCraft - Worms of Arrakis` as optional (the plugin name is the manifest name;
  the Worms javadoc writes `WormsOfArrakis`, so both are looked up) and `Hytale:Deployables` as required (the interaction type of the placement). The bridge is the only class naming Worms classes and is
  loaded only after the plugin is found, inside a handler for linkage errors. **Load order:** nothing is assumed. The module registers `Arrakis_Thumper` with `WormAggro.registerTotem` in `start()`, at
  `BootEvent` and every 5 seconds after (a scheduled check that looks the plugin up again); if the plugin instance changed (Worms reloaded) the link is rebuilt, and an id missing from `totemIds()` is
  registered again. The log says what changed ("registered Arrakis_Thumper as a totem; Worms reports [...]"). `stop()` unregisters. Without Worms the log says once that the Thumper places, thumps and
  stands. Headless result: with the Worms jar, "registered ... Worms reports [Arrakis_Thumper, Healing_Totem, Slowness_Totem]"; without, the single line and no error.
- **The sand set.** Worms uses `SandBlocksOverride` if set, else `Arrakis_Sand` when Dunes of Arrakis is installed, else the four vanilla sands (`Soil_Sand, Soil_Sand_Ashen, Soil_Sand_Red,
  Soil_Sand_White`); it checks the first non-air block at or below the feet by `SandTolerance` (1.0: two blocks). Worms has no API for the set, so: `SandBlocks` in the Thumper config if not empty, else
  the ids Worms itself reports (its package-private `sandBlockIds()` read by reflection when Worms is linked: the override is then matched exactly), else the same default as Worms. The gate looks at the
  two blocks under the feet the same way. It does not reuse the Compactor's rule (that one excludes Arrakis_Sand). Headless: with Worms the set was read from Worms (four ids), without it the vanilla four.
- **Derived art (documented exceptions to "vanilla by path").** (1) `Common/Icons/Items/Equipment_of_Arrakis/Arrakis_Thumper.png`, made by `tools/assets/thumper/make_icon.js` from the vanilla icon
  `Furniture_Temple_Emerald_Torch.png`: no unlit icon of this torch exists, so the flame (saturated green, yellow-green and yellow pixels, hue 53 to 170 degrees and saturation 0.45 or more, plus the
  almost white core touching them) is removed, the space above the cup is transparent and the hole in the bowl is filled with the darkest stone pixel below it. 181 flame pixels removed, 34 filled; no
  green or yellow pixel is left (a unit test checks it). It reads as the stone torch with an empty dark bowl. An item icon must live under `Icons/Items/` or `Icons/ItemsGenerated/` (the asset
  validator says so), hence the path. (2) `Common/Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.blockymodel` and `Arrakis_Thumper_Texture.png`, made by `tools/assets/thumper/make_model.js`: the vanilla
  torch model and texture, re-encoded with nothing changed (the model as compact JSON, the texture as the same pixels written again; a byte-identical copy would be a "Duplicated Asset", ARCHIVE rule 7).
  A model asset (`Server/Models`) may only point under `Characters/, NPC/, Items/` or `VFX/`, and the torch is under `Blocks/Decorative_Sets/`, so the standing entity cannot use the vanilla path
  (the held item can, and does). The vanilla file `Items/Torch/Torch.blockymodel` is a different (wooden) torch.
- **Not built:** the Thumper is not removed at the end of a worm event (Worms removes one that is swallowed; one left standing when an event ends some other way stays, which is accepted for now);
  `MaxLiveSeconds` (default 0 = off) is the safety net for a server that wants dead Thumpers to expire.
- **Commands:** `/arrakis thumper give [n]` (default 4), `state` (the standing Thumpers: owner, position, age, beat timer; the Worms link and the totem ids it reports; the sand blocks in use),
  `clear` (removes them all). **Settings (`Thumper`):** `MaxLivePerPlayer` 3, `SandBlocks` [] (Worms' set), `BeatSeconds` 1.0, `DoubleThumpGapSeconds` 0.25, `ThumpPitchSemitones` -6,
  `ThumpVolumeDb` 3, `MaxLiveSeconds` 0 (the 8 directions and the 1 block distance are fixed, not settings). **An old config file** has a `Modules` list without `"thumper"`, so the module is off until that word is added (or the file is deleted to be written again).
- **Checked here (no client, no player):** the build and 204 tests; the headless boot without the Worms jar and with it, and with the module switched off ("removed 7 of its assets"): no PROBLEM line, no asset
  error, the Duplicated Asset count unchanged. Not testable headless (a player is needed to place, entities land in unloaded chunks): the placement, the beat, the sound and the worm.

## suspensor (Suspensor Belt Mk II and Mk III)

Module id `suspensor`, package `suspensor`, config section `Suspensor`; **add `"suspensor"` to `Modules` in an existing config file.** No item: two toggles on the picker page (right click any tool), ids `suspensor3`
(Suspensor Belt Mk III, order 31) and `suspensor2` (Mk II, order 32), icon item `Rune_WindStrike`, one exclusive **group** `suspensor`: turning one on turns the other off first, so the ON mark is the
mark in operation. Mk I (slow descent) is the test pair below.

- **What the belt does.** While a mark is ON the movement setting `fly` (`FlyMode`) is `Allowed` with `horizontalFlySpeed` and `verticalFlySpeed` of the mark, pushed once
  (`MovementManager.update`). The **client's own double tap of Space** then starts creative-style flight (Space rises, crouch sinks, a second double tap ends it); the server never calls
  `setFlying(true)`. Mk III: `Mk3.HorizontalSpeed` 1.0, `Mk3.VerticalSpeed` 1.0. Mk II: `Mk2.HorizontalSpeed` 1.0, `Mk2.VerticalSpeed` 0.0 (flat flight). Units: the lab found 0.5 in Adventure
  feels like 0.1 in creative, hence 1.0 to start; tune with `/arrakis suspensor set`. The client's own flight settings (Flight Mode hover/directional, Flight Drift, Camera-Relative Flight, Move Speed) apply
  to this flight as to creative flight; the server does not control them.
- **Mk I, the glider route (the default, a proof).** `Mk1.Route` `glider` (an older saved `flyglide`, `airdrag` or `counter` stays as saved: run `mk1 route glider`). The truth it proves: the server can tell that a player is falling, and that can open a glider. Nothing else is
  done to the player: no flight, no velocity, no settings push, no Mana. **The item** `Arrakis_Suspensor_Glider` (parent `Template_Glider`: the vanilla model, texture, Glider block, and the inherited Primary and SwapFrom, which are the control, since a left click in the air with it in hand is the
  vanilla glide; no recipe, no durability, `Utility` usable and compatible, not hidden from the creative list) has three added chains, each `Condition { Gliding false }` then `EffectCondition { EntityEffectIds [Arrakis_Suspensor_Falling], Match All }` then `ToggleGlider`: `HeldOffhand` (Utility slot),
  `Held` (main hand) and `Secondary` (right click), so one test shows which the client honours; the extra ones go once it is known. **The signal** is the effect `Arrakis_Suspensor_Falling` (plain, no visuals, 0.5 s, Overwrite, not a debuff). **The trigger** is `Mk1.GliderTrigger`: `falling` (default: `MovementStates.falling`
  true for `Mk1.FallingTicks` (1) ticks, never on the ground, flying, in fluid or outside Adventure, no speed threshold: it fires at the apex of every hop, which is wanted for the proof), `speed` or `farfall` (the existing rules); the value applies to both Mk I marks. ARMED to ENGAGED: the effect is applied and refreshed every 0.25 s while the trigger holds
  and the player is not gliding. END: 2 ticks on the ground, fluid, flying, toggle off, death, world change, disconnect, leaving Adventure: the effect is removed (a glide in progress is not touched). **The slot:** turning a Mk I mark on puts the glider into the Utility slot when it is empty (chat line), leaves an occupied slot alone and says
  "Empty your utility slot for the glider." (the mark stays on, the effect still applies); the other Mk I mark keeps the item, Mk II/III, off, death, world change and disconnect take only that item out, and a glider found in the inventory at join is removed. The Utility slot is not selected by code (the log prints the active utility slot when the item is placed). Commands:
  `suspensor glider give hand|utility`, `glider take`, `glider effect on|off` (10 s by hand), `mk1 set trigger falling|speed|farfall`, `mk1 set fallingticks n`; `mk1 state` shows the trigger, whether the item is in the slot, the signal, the gliding flag and the stat GlidingActive; the probe also logs flips of `gliding` and `GlidingActive`, the
  effect applied (once) and removed, and at each landing the speed and the health change. The older routes below are unchanged and unused by default.
- **Mk I, the slow descent (two test marks).** Toggles `suspensor1f` (Suspensor Belt Mk I (far fall), order 29) and `suspensor1s` (Mk I (speed), order 30), same group and icon, registered only while `Mk1.Listed` is true
  (default true for this test build; false: not registered). Nothing is sent and nothing charged while ARMED. **Triggers** (never while on the ground, flying, in fluid or outside Adventure): *far fall* engages when
  `MovementStates.fallingFar` is true (a client flag that flips after about 1.0 s and 16 blocks of falling, so it is late); *speed* engages when `falling` is true and the client's downward speed (`Velocity.getClientVelocity().y`) is above `Mk1.SpeedThreshold`
  (12 blocks a second) on `Mk1.SpeedTicks` (2) ticks in a row, which on a client was about -13.3 blocks a second and 0.4 s into a fall. `falling` alone is never a trigger. **ENGAGED** until: 2 ticks with `onGround`; for flyglide also six ticks
  with no vertical movement (after a 0.5 s grace; the stall rule, in case the ground flag is not set while flying) or six ticks without the flying flag (flight lost); fluid; for the other two ways flying or more than 0.5 s of not falling; toggle off, death, reset, a Mana cost running out. Then the belt re-arms.
  `Mk1.ManaPerSecond` (0 here) goes through the same charge as Mk II/III. Switching from Mk II/III while flying ends the flight and restores the snapshot first; switching from an engaged Mk I to Mk II/III ends the descent first. Death, world change, disconnect and leaving Adventure reset the movement
  settings to the defaults and clear any velocity instruction, as for Mk II/III.
- **The three ways** (`Mk1.Route`, `/arrakis suspensor mk1 route flyglide|airdrag|counter`). The first test build had a *settings* way (dragCoefficient and mass pushed 30 times heavier) and a *velocity* way (Set -4); both failed, see ARCHIVE.md rules 13 and 14; old config values read as flyglide.
  *flyglide* (default): at ENGAGE the fly fields (fly Forced, `horizontalFlySpeed` `Mk1.GlideHorizontal` 1.0, `verticalFlySpeed` 0 so Space and crouch do nothing) are written and `Player.setFlying(true)` pushes them with the flag once (Forced is kept because it is already Forced),
  then one external velocity instruction Set (0, -`DescentSpeed`, 0) with a config that does not decay. While ENGAGED it is refreshed only when the client vertical speed is more than `Mk1.HoldTolerance` (0.75) from the target and at most every `Mk1.PushTicks` (5) ticks; counted. At END the instruction is
  cleared (Set (0, 0, 0), default config), the snapshot is written back and `setFlying(false)` pushes it once: two settings pushes per fall. *airdrag*: at ENGAGE `airDragMin` and `airDragMax` are both set to `Mk1.AirDrag` (default derived: m = x / (1 + x) with x = DescentSpeed / `Mk1.GravityStep` 0.1185, which is 0.9712 for 4)
  and pushed once; at END the snapshot is pushed back once. Hypothesis, to be measured: the terminal speed is v = step * m / (1 - m). *counter*: an upward external velocity of (own downward speed - target) = `-target - (vy - lastInstruction)` when the client reports the total (`Mk1.ReportsTotal` true), sent at ENGAGE and
  refreshed like flyglide when the fall is faster than target + tolerance; cleared at END. May jitter; **dead end if the impulse probe says so.**
- **The no-decay config.** `VelocityConfig` defaults: ground 0.82, ground max 0, air 0.96, air max 0, threshold 1, Linear. The NPC motion controller, which applies the same config on the server, multiplies the instructed velocity by `pow(resistance, step)` each step, the resistance blended between the plain and the Max value by speed and threshold, so
  the numbers are per-step multipliers where **1.0 means no decay** (the Max values are set to 1.0 too, so any blend gives 1.0) and 0 would kill the velocity at once. This is read from the class, not yet seen on a client: `suspensor impulse <y> [resistance]` tests it (default resistance 1.0; pass 0 to see the other reading).
- **Commands:** `suspensor mk1 state` (route, numbers, the trigger of your mark, ARMED or ENGAGED, engages, ends and why the last one ended, settings pushes, instruction refreshes, whether an instruction is active, last landing speed), `mk1 route flyglide|airdrag|counter`, `mk1 total true|false`,
  `mk1 set speed|threshold|ticks|glide|tolerance|push|airdrag|gravity|mana <n>` (saved; airdrag 0 = derived), `suspensor probe on [chat]|off` (INFO lines for every flip of falling, fallingFar, onGround and flying with the height above the last ground, the time since onGround and the client vertical speed, and every 0.25 s while
  ENGAGED the vertical speed, the flying and onGround flags and the number of queued instructions; with `chat` also in your chat), `suspensor impulse <y> [resistance]|clear` (sends one Set (0, y, 0) and logs the client vertical speed, the server vertical velocity, the queued instructions and the flags every 0.1 s for 2 s; **a downward y adds to a fall: use it from a safe height**),
  `suspensor drop <blocks>` (teleports you straight up; refuses 0 or more than 250, anything above row 319, a block or unloaded column in the way plus two rows of headroom). **Logs:** each ENGAGE and END at INFO (trigger, speeds, blocks fallen, landing speed, fastest, health before and after, the instruction refreshes), at most one of each a second.
  The 0.3.0 config had the text `"Mk1": "not read yet..."`, and the first Mk I build the keys `DragFactor`, `Field`, `TerminalSpeed`; all still load (the old keys are ignored) and the file is rewritten. A saved `PushTicks` of 2 from the first build stays 2: set it to 5 with `mk1 set push 5`.
- **Cost.** `ManaPerSecond` 1.0 times the tick time through `Power.spendUpTo` (the same helper as the Compactor, so the regen idle rule is shared), only while the flying flag of `MovementStates` is set.
  Not flying: no cost, nothing sent. Creative: the toggle does nothing (a chat line says so) and creative flight is untouched; if the game mode leaves Adventure while the belt is ON it lets go.
- **At 0 Mana** (nothing left to take while flying): `Player.setFlying(ref, false, accessor)`, then fly is pushed `Disabled` with the snapshot's speeds, the toggle stays ON but **unarmed**, and "The belt needs Mana." is said
  (`Notice`, once a second). It arms again, with one push, when Mana is at `ResumeMana` (5) or more, or when the toggle is turned off and on. Switching on with Mana under `ResumeMana` starts unarmed.
  If the client still reports flight after the cut-off, it is stopped again at most once a second.
- **Off** (toggle, the other mark never drops you, `/arrakis suspensor off`, death, respawn, world change, disconnect, leaving Adventure): if flying, `setFlying(false)`; the snapshot of the three fly fields
  taken at switch-on is written back and pushed once. **After death, a world change, a disconnect or a game-mode change the snapshot is not trusted and `MovementManager.resetDefaultsAndUpdate` is used instead**
  (the log says which: "restored the snapshot" or "reset to the defaults"). Switching Mk III to Mk II while flying keeps the flight, keeps the first snapshot and pushes the new speeds once.
- **The packet rule.** `BeltMachine` (no engine types, unit tested) decides; `SuspensorSystem` carries out. A settings write happens only when the settings that should be in force differ from the last
  pushed (arming, cut-off, re-arming, a mark change, a speed change from `suspensor set`, off): never per tick. The log shows only arm/cut-off/push/off lines, at most one of a kind per second per player.
- **Hum.** The lab's hum is moved here as `Arrakis_Suspensor_Hum` (vanilla loop `SFX_Ability_Wind_Strike_Imbued_Loop`, refreshed every tick while flying and paid, removed when flight ends; `Hum` false turns it off).
  It is the Compactor's mechanism and loads in the headless boot; whether it sounds right is **needs a client** (the lab never got a verdict on it).
- **Commands:** `suspensor state` (mark, armed or not, flying, Mana, the speeds, the movement settings now, last pushed, the snapshot, how many settings writes), `suspensor set mk2|mk3 <h> <v>` (saved to the config; players with
  that mark ON get the new speeds on their next tick), `suspensor off`.
- **Toggle groups (core).** `ToggleRegistry.Toggle` has an optional `group` (the old constructor leaves it null; the Holtzman Shield and the Personal Light are ungrouped and unchanged). ON switches every other ON member of the
  group OFF first through the same hook (a hook that throws is skipped), then switches the toggle ON; OFF touches nothing else. `ToggleRegistry.set(id, on, ...)` is the same path for commands. The picker page reads each
  toggle's state when it rebuilds, so the mutual switch-off shows in the same rebuild; its chat line now reports the state after the press (a switch-on refused in Creative says OFF).
- Fall damage, the Water/stamina rework and the Holtzman field are not touched; landing from flight uses the engine's rules (ARCHIVE.md rule 11: no damage below a client fall speed of 21 blocks a second).
- **The lab is gone.** The movement lab (module `lab`, items `Arrakis_Lab_*`, `/arrakis lab`) was removed with `tools/lab/remove_lab.js` once it had answered its questions; what it found is in ARCHIVE.md rules 11 and 12. The hum effect
  moved here; the MovementSettings snapshot and restore are `BeltMachine` and `Suspensors.read/write`.

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
   with no animation; no Mana spent. `/arrakis blood add 495` fills a bag to 500. The body vanishes about a second after the draw, with the vanilla death effect; an interrupted poke leaves
    it with its remaining time and usable.
    **Bodies (18):** `/arrakis blood corpse`: the cow appears, about a second later it falls and stays lying, and is still there after two minutes (`blood corpses` shows seconds left and the Status animation); `blood corpse Squirrel` falls and vanishes as in vanilla (log: `holds=false -> vanilla timer`). The Blood Extractor poke shows the Interact motion, not the gun-butt bash; with `PokeAnimation` set to `GuardBash` in the config the old one is back. Then kill a cow, a skeleton and a goblin with a weapon: each body stays about two minutes, then goes with the death effect. **What does it look like for those two minutes** (lying in its death
    pose, standing up again, gone)? Loot and drops come at death; the body does not attack, move or block. Kill 10 mobs in one spot: the newest five in that chunk stay, the older go in about a
    second; another chunk is unaffected; `blood corpses` shows the numbers, `corpses clear` removes them. A player's death and respawn work as before; an excluded role disappears with the vanilla
    timer; nothing is left after a restart.
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
17. **The old test world** (`Arrakis Equipment Test`) with its old config file: it loads, everything above works, and the file is rewritten without the removed keys. Add `"thumper"` to its `Modules` for 19 to 21.
18. **Crouch:** standing, right click with a Thumper: "Crouch to plant the Thumper.", item kept; the Blood Extractor on a body: "Crouch to draw blood.", no animation, no Mana, the body keeps its timer. Crouched, both work (creative too).
19. **Thumper item:** `/arrakis thumper give`: it is in the IslandCraft tab (Tools) with the unlit icon (stone torch, empty dark bowl, no flame). In the hand: the vanilla torch model, no flame, no light, held and swung like the vanilla torch (compare with `Furniture_Temple_Emerald_Torch` from the creative menu). If the hand is empty or wrong, see the item notes in `thumper` (BlockType vs Model).
20. **Thumper placement:** crouching on flat sand, face each of the 8 directions in turn and right click: the Thumper appears on the neighbouring block in that direction, on the sand, never on your own block; looking up or down changes nothing. It is the size of a placed torch, one item is used up (not in creative) and you can walk through it. Next to a wall, a steep dune face, on a non-sand block or with a drop of more than two blocks: refused with a message, item kept. A second Thumper on the same spot: "A Thumper is already there." A fourth (three standing) is refused; clicking fast does not slip a fourth past. Try the edge of a dune and the border of sand and rock: the Worms log must never say "not on sand: ignored" for one of ours, and ours must not log "Thumper MISMATCH". MaxStack 1: `/arrakis thumper give 3` fills three slots, planting one consumes exactly one. It cannot be broken by hand, weapon or mob.
21. **Thumper beat and worm:** thump-thump, a rest, thump-thump every second, audible from tens of blocks and quieter with distance (silent past about 45); several Thumpers do not cut each other off; compare the sound with the audition (half an octave down, louder). Relog, death and a world change leave no beat sound and no stuck state; `/arrakis thumper clear` stops it at once. With Worms of Arrakis and the Thumper on sand: within about 10 seconds a worm event starts around it and the worm breaches at the Thumper, which is gone; the owner standing on sand nearby gets the pulses; a Thumper placed during a stalk makes the worm come for it. Without Worms: it places, thumps and stands.
22. **Suspensor toggles and group** (Adventure; `"suspensor"` in `Modules`): the picker lists Suspensor Belt Mk III and Mk II with the wind rune icon, OFF. Mk III on shows ON; then Mk II on shows Mk II ON and Mk III OFF in the same rebuild. Holtzman and Light still toggle independently.
23. **Suspensor flight:** Mk III on (chat line "ready: double-tap Space"): walking and jumping unchanged; double-tap Space flies, Space rises, crouch sinks, all directions at about the configured speed (1.0; try `/arrakis suspensor set mk3 2.0 2.0` live); Mana drops about 1 a second only while flying; a further double tap ends the flight and you fall normally. Is 1.0 a good speed? Does the flying pose look right and the hum sound right?
24. **Suspensor Mana:** while flying `/arrakis power set 3`: the flight ends, you fall, "The belt needs Mana." (once a second at most), a double tap does nothing; once Mana is back at 5 or more it works again. Note the damage if you fall from height (a fall speed under 21 blocks a second costs none) to judge whether Mk I is needed.
25. **Suspensor Mk II and switching:** Mk II on: the double tap flies but Space and crouch do nothing vertically (flat glide). Turning the toggle off while flying drops you at once; switching Mk III to Mk II while flying keeps you flying and locks the height.
26. **Suspensor resets:** death, respawn, relog and a world change leave both OFF, `suspensor state` shows the settings back to normal (fly Disabled, 10.32 / 10.32 in Adventure) and a double tap does nothing; creative is unaffected and the toggle says it does nothing there. Server log: only arm, push, cut-off and off lines, no line per tick; stamina is not drained.
27. **Mk I toggles and group** (Adventure; `Mk1.Listed` true): the picker lists Mk I (far fall), Mk I (speed), Mk II and Mk III with the wind rune; any one on turns the others off in the same rebuild. Mk I on: "ready: a long fall will be slowed". Jumping and hopping on flat ground: nothing changes (`suspensor mk1 state` still ARMED, engages 0).
28. **Mk I (speed), flyglide (the default):** `suspensor probe on`, then `suspensor drop 20` (also 50 and 100) and fall. The log: falling and onGround flips, "descent ENGAGED" at about -13, `flying false -> true` shortly after, then engaged lines every 0.25 s with the vertical speed holding near -4, the flags and `instructions 1`; "descent END (landed)" with the landing speed and health before and after. You should stop accelerating and drift down at about 4 blocks a second, steering with the movement keys (`mk1 set glide 2` for faster), Space and crouch doing nothing; a soft touchdown, no damage; walking and jumping normal afterwards. Does `onGround` turn true while flying (the END reason says "landed" or "landed (stalled...)")? Then `suspensor off` and fall from the same height: the damage is back.
29. **Mk I (far fall):** the same with Mk I (far fall) on: it should engage about 1.0 s and 16 blocks into the fall (late: the speed is already -31).
30. **airdrag and counter:** `mk1 route airdrag`, repeat: the log says the landing speed; try `mk1 set airdrag 0.98`, `0.97`, `0.95` and write down the landing speed of each, to fit the formula; what does it do to jumping, walking and sideways air movement? `mk1 route counter`, repeat: smooth or jittery? **Impulse tests** (from a safe height; `suspensor impulse 8` standing still, in the air after a drop, and while flying with Mk II/III): does an upward Set hold or decay with resistance 1 and with 0, is the client velocity the total or only its own part (`mk1 total false` if only its own), does it persist across a landing; `suspensor impulse clear` sends Set (0,0,0). `mk1 state` counters: two settings pushes per fall on flyglide and airdrag, instruction refreshes counted.
31. **Mk I clean-up:** toggle off while descending, die while descending, relog, change world, switch to Mk II/III while gliding: no flight, no stuck velocity, no stuck settings (`suspensor state` shows fly Disabled, airDrag 0.96/0.995 in Adventure), walking and jumping normal; the log shows two settings pushes per fall and nothing per tick.
32. **Glider route: the tests** (Adventure; `Mk1.Route` glider, `probe on`; each from `suspensor drop 20` unless noted). **Control:** `glider give hand`, no mark on, drop, left click in the air: does the vanilla glide open and at what speed does it fall (the item says -2)? **Manual signal:** `glider give utility`, `glider effect on`, drop: does the glide open without a click (HeldOffhand
    in the Utility slot)? Repeat with `glider give hand` (Held) and with a right click (Secondary). **Automatic:** Mk I (speed) or (far fall) on (the glider appears in the Utility slot), `GliderTrigger` falling: a plain hop, a long walk off a ledge, `drop 20`, `drop 50`: when does the glide open (apex of a hop?), at what height and speed, does it end at the ground, the descent speed while gliding, the landing speed and damage.
    **Triggers:** `mk1 set trigger speed` and `farfall`, repeat once each. **Edge cases:** toggle off while gliding, die while gliding, relog, change world, Creative, another item in the Utility slot (the chat line, the mark stays on). Afterwards: no item, no effect, no glide state left (`mk1 state`).
