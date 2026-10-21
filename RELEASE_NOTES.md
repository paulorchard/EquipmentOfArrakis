# Release notes

## Next (not yet released)

- **Suspensor Belt** (module `suspensor`; **add `"suspensor"` to `Modules` in an existing config file**): one toggle on the picker page. With it on, a double tap of Space starts creative-style flight (Space up, crouch down, a further double tap ends it), at 2 for both fly speeds in a fresh config (a saved config keeps its numbers; tune with `/arrakis suspensor set`), costing 10 Power a second while flying (a full pool lasts 10 to 15 s); at 0 Power the flight ends and the belt waits for 5. Walking and jumping are normal. A **tap gate** keeps flight to a rapid double tap: the belt holds the fly setting off except for `TapWindow` (0.35 s) after a jump start (`TapMode` afterJump) or after `ReopenSeconds` on the ground (groundOpen); `/arrakis suspensor window <seconds> [afterJump|groundOpen] [reopenSeconds]` tunes it live, 0 turns it off. New `Suspensor` config section (`HorizontalSpeed`, `VerticalSpeed`, `ManaPerSecond`, `ResumeMana`, `Hum`); commands `/arrakis suspensor state`, `set <horizontal> <vertical> [mana]` and `off`. The test builds' marks (Mk I, Mk II, Mk III), glider proof, probes and `Mk1`/`Mk2`/`Mk3` settings are gone: an old config loads, ignores them and is rewritten (the old speeds and 1 Mana a second are not carried over). A saved `Arrakis_Suspensor_Glider` in an inventory is removed when the player joins a world (kept for this release). The toggle registry has exclusive groups (generic, unused for now).

- **Bodies of killed mobs stay `CorpseSeconds` (120) on the ground** instead of the death animation time, so the Blood Extractor is easy to use; a finished blood draw makes the body vanish in `ExtractedSeconds` (1). At most `MaxCorpsesPerChunk` (5) per chunk (the newest stay), `ExcludedRoles` keeps a role on the vanilla timer. New `Extractor` config section (old files load; the section is added on the next save). Only mobs whose death animation holds its last frame are extended (`ExtendNonHolding` allows the rest). New commands `/arrakis blood corpses` and `corpses clear`; `/arrakis blood corpse [role]` now kills the mob a second after it spawns, so its death animation plays. The Blood Extractor poke uses the Handgun `Interact` animation (`PokeAnimation` in the config; `GuardBash` is the old one).

- **The Thumper** (`Arrakis_Thumper`, IslandCraft tab, Tools; `/arrakis thumper give|state|clear`): a consumable unlit torch. Right click on sand plants an invulnerable, collision-free deployable at your feet that thumps (a soft double thump every second, the vanilla sand footstep half an octave down) and draws the sandworm of Worms of Arrakis, which this mod registers as a totem when that mod is loaded (an optional dependency; without it the Thumper only thumps and stands). At most `MaxLivePerPlayer` (3) per player; sand is whatever Worms counts as sand. The Thumper needs a crouch, is planted on the block you face (one of the eight around you), one per slot, and every placement is judged by exactly the rule Worms uses at the exact spot. **The Blood Extractor now needs you to be crouching already** (`RequireCrouched` true, `ForceCrouch` false by default; the settings keep their names). New `Thumper` config section; **add `"thumper"` to `Modules` in an existing config file.** The manifest now requires `Hytale:Deployables`. Derived art: the icon (flame removed) and the re-encoded torch model (see MODULES.md).

## 0.3.0: cleanup release

No behaviour change for the player. Item ids, stack metadata keys, interaction type names, asset ids and existing config files are unchanged: a config written by 0.2.0 loads, ignores the keys that
were removed and is rewritten without them the next time it is saved.

### What the mod contains

- **Tools and the picker:** Cutter Ray, Compactor, LitreJon, Dew Harvester, Blood Extractor, Vehicle Summoner and Build/Repair Tool (placeholders), Blood Bag. Right click on any tool opens the picker
  page: swap to another carried tool, or switch a toggle.
- **Weapons and the H key:** ten weapons (Crude, Atreides, Choam, Harkonnen swords and daggers; Maula Pistol and Dartgun). They list `Items.Weapons` as well as `IslandCraft.Weapons`, which is what
  makes the client draw the vanilla weapon ability HUD. The H key (Ability4) toggles the Holtzman shield on the weapons and the seven hand tools.
- **Toggles:** Holtzman Shield and Personal Light, on the picker page; the shield also on H.
- **The four-bar HUD:** Power, Health, Heat Exposure and Water above the hotbar, drawn from the vanilla Health and Mana art, with the low alert, creative fills, nudge commands and the link to the Water mod.
- **Power and the IslandCraft creative tab** underneath.
- The held-item name overlaps the bottom bar row and cannot be moved from the server; `BarsLift` 50 or 64 makes room (default 0).

### What was removed

- The whole **experiments module** (Flame Cutter, Flame Beam Cutter, the calibration rig): 10 Java files, 5 test files, 34 assets, its generators, its config section, `Modules` entry and language keys.
- The **HUD investigation code**: the probe, the item-name label mover, the Reference art route, the bisect variants and the `-PhudExtra` build flag. The settings `ArtSource`, `AlertColor` and
  `ItemName*` are ignored when read.
- **Investigation commands:** `weapons diff`, `weapons trace`, `shield diag`, `hud probe`, `hud itemname`, `hud art` (and the item comparison class behind `weapons diff`).
- **Dead code:** `ToggleRegistry.flip`, the `BloodBag` API class, `ArrakisIds.isOurs/itemDescriptionKey/categoryKey`, `WorldAccess.chunkStore`, `HudRuntime.exposureTint`, `WeaponIds.isWeapon`,
  `BeamMath.entryDistance`, the dev trace flags of the toggle, shield shots and darts, and the `Arrakis_Dev_Marker` item (a creative-menu item of no use).
- `EXPERIMENT_LOG.md`, replaced by `MODULES.md` (module notes and the client test sheet) and `ARCHIVE.md` (what was tried and abandoned, and the client rules learned).

### New

- `AssetReferencesTest` fails on a resource file or a language key that nothing refers to.

The old code is recoverable from the git tag `equipment-v2.1-baseline`.
