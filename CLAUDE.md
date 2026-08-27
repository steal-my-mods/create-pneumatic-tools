# Create: Pneumatic Tools — repo guide

Create addon for **Minecraft 1.21.1 / NeoForge 21.1.219+ / Create 6.0+**. Nine handheld tools that
run off a Create backtank.

## Commands

```bash
./gradlew build              # compile + jar
./gradlew runClient          # dev client
./gradlew runServer          # dev dedicated server (needs run/eula.txt)
./gradlew runGameTestServer  # automated in-world tests -- the real check
./gradlew publishMods        # upload to CurseForge and GitHub Releases
./gradlew publishMods -PdryRun=true   # ...or rehearse it without uploading anything
python3 tools/generate_models.py       # every 3D model: chassis, heads, moving parts
python3 tools/generate_textures.py     # the eight material panels
python3 tools/generate_logo.py         # the in-jar badge at 256
python3 tools/generate_logo.py branding/icon-512.png --size 512   # ...and the 512 CurseForge wants
python3 tools/generate_structures.py   # the empty GameTest template
python3 tools/generate_recipe_advancements.py  # one per recipe, or nothing reaches the recipe book
python3 tools/check_lang.py            # every tooltip key resolves, and nothing is orphaned
python3 tools/check_config_docs.py     # every config option is documented in the README, both ways
```

JDK 21 required. `gradle/gradle-daemon-jvm.properties` pins the daemon to it, so the commands work
without setting `JAVA_HOME` even when the default `java` is newer — don't delete that file, or
`./gradlew build` dies with "Could not create task ':test' ... Type T not present" on a newer JVM.

`runClient` loads **JEI** as well, and only in the dev runs — the vanilla recipe book lists a recipe
only once an advancement has granted it, and cannot show the Borer's at all, because mechanical
crafting is not a vanilla recipe type. See the `devMods` configuration in `build.gradle` for why it is
declared the way it is; two more obvious routes silently do nothing.

There is no unit-test suite; correctness is covered by GameTests in
`com.createpneumatictools.test.PneumaticToolGameTests`. Run them after any change to a tool's
behaviour or its air cost.

## Build quirk worth knowing

Create declares Registrate / Ponder / Flywheel as Maven dependencies, but **no 1.21.1 build of any of
them is published to a public Maven** — Create ships them jar-in-jar. So `build.gradle`:

1. resolves Create with `transitive = false`,
2. unpacks `META-INF/jarjar/*.jar` out of Create's jar (`unpackCreateJij` task),
3. puts those on the compile classpath as **`compileOnly`**.

`compileOnly` is deliberate: at runtime FML loads them from Create's own jar, and a second copy on
the runtime classpath makes each mod load twice. Catnip is not a separate artifact — it lives inside
the Ponder jar.

## The one idea

Every tool is rated in **uses per tank**, never in air units. That is the unit Create already states
its own equipment in — the Extendo Grip is 1000 actions, the Potato Cannon 200 shots — so this mod's
config can be read against Create's without converting anything, and
`BacktankUtil.canAbsorbDamage` turns a rating into a cost identically for all of them. A rating of
**0 means free**, matching what Create's own config comments promise. Everything goes through
`air/AirSupply`; nothing else in the mod touches a backtank.

## Architecture landmarks

| Path | Role |
|---|---|
| `air/AirSupply` | The only place this mod touches `BacktankUtil`. `isPowered` (both sides, no cost) and `spend` (server only) |
| `item/PneumaticToolItem` | Base: hands the durability bar to the backtank, and owns `refuse` — the one "no air" sound |
| `item/PneumaticDiggerItem` | Base for the five block-breakers. Owns the `TOOL` component and the `mineBlock` entry point |
| `item/BurstDiggerItem` | Base for the two that take a whole square down at once. The Tunnelling Drill and the Borer differ only in `radius()`, their rating and their speed |
| `tool/DiggingHandler` | `PlayerEvent.BreakSpeed`. **All** of a digger's speed comes from here, and only while the tank has air |
| `tool/Excavation` | The re-entrancy guard that stops the tunnelling drill and the saw breaking blocks forever — and the veto that keeps the extra blocks inside spawn protection and the world border |
| `client/CPTTooltips` | Registers each item with Create's `ItemDescription` so they get Create-style tooltips |
| `source/PneumaticSourceBlock` | The Pneumatic Wrench's invisible, temporary generator |
| `source/PneumaticSourceBlockEntity` | ...and its lease, which is the whole safety argument for it |
| `client/PneumaticToolRenderer` | Draws one tool: static body, then its moving parts. One class for all of them |
| `client/CPTPartials` | Every moving piece and where it is mounted. Cross-checked against the generator |
| `client/ToolAnimation` | The shared clock: one eased throttle and one free-running phase |
| `client/CPTModelCheck` | Dev-only startup check that the whole 3D chain actually wired up |
| `client/CPTPhotoShoot` | Dev-only: fills the hotbar and photographs itself, for README shots |
| `tools/generate_models.py` | Every 3D model — chassis, heads, moving parts, display transforms |
| `tools/generate_textures.py` | The eight material panels the models are skinned with |
| `tools/generate_logo.py` | The Create-family badge |
| `tools/generate_recipe_advancements.py` | One advancement per recipe — without them nothing appears in the recipe book |

## Things that will bite you

- **A digger's `TOOL` component must stay at mining speed 1.** All the speed is added by
  `DiggingHandler`, and that is not a style choice: the component is baked into `Item.Properties` at
  registration, which happens long before the config file is read, so a speed put there could never
  be configurable. It also makes "empty tank" mean "the handler adds nothing" rather than a
  subtraction that has to be got exactly right.
- **Efficiency is applied by `DiggingHandler`, because vanilla refuses to.** `Player.getDigSpeed`
  adds the `MINING_EFFICIENCY` attribute only `if (f > 1.0F)`, where `f` is the tool component's own
  speed — pinned at exactly 1 above, so vanilla's bonus is always skipped. Raising the component to
  let vanilla in is the trap: it could never be configurable, and it would make an empty tank
  permanently faster than bare hands, which is the promise the whole design rests on. So the bonus is
  applied on this side of the gate, next to the rest of the speed.
  - **Multiplicative, not vanilla's additive**, and the jackhammer is why. Vanilla adds `level² + 1`
    to a tool's own speed, which works when that speed *is* a speed. The jackhammer's is solved
    backwards out of a *time*, so a fixed addition shortens the break by an amount that depends on
    hardness — Deepslate gains far more than Obsidian, which is the exact difference the tool exists
    to erase. `anEfficientJackhammerStillIgnoresHardness` covers it, and was mutation-checked with the
    additive model: Obsidian goes to 6.52 speed-per-hardness and Deepslate to 14.67.
  - **0.2 per level is vanilla's, not invented** — it is the constant in `getDigSpeed`'s Haste term,
    so one level of Efficiency is worth one level of Haste and Efficiency V doubles the speed. That is
    less than the 4.25x a diamond pickaxe gets, deliberately: an Efficiency V jackhammer at vanilla's
    rate would shatter Obsidian in a single tick.
  - Efficiency does nothing where the air does nothing. The handler returns before applying it when
    `poweredSpeed` is 1, so a jackhammer on soft stone stays slow and free with a book on it.
  - Read off the stack's enchantments rather than the `MINING_EFFICIENCY` attribute, which would
    otherwise be tidier: the attribute carries `level² + 1`, and a rule written in levels wants the
    level.
- **Haste, Mining Fatigue, the water penalty and the airborne `/5` all land *before* the event.**
  `EventHooks.getBreakSpeed` is the last line of `Player.getDigSpeed`, so `getOriginalSpeed()` already
  has all of them in it and the config multiplier compounds with them. A beacon is therefore worth
  x1.4, or x1.6 at Haste II, on top of the air — including on the jackhammer, whose "five ticks" is
  the time on a bare player rather than a floor. The config comment says so.
- **Vanilla's spawn protection and world border only ever guard the block named in the packet.**
  Both are checked in `ServerGamePacketListenerImpl`, against the position the client sent, so they
  cover the block a player actually swung at and nothing else. Every extra block the tunneller and
  the saw take down was described to nobody. Claim mods were always fine — Create's
  `destroyBlockAs` posts `BlockEvent.BreakEvent` and honours a cancellation — but vanilla's own two
  gates are not listeners, so `Excavation.aCascadeStaysInsideTheRules` asks `level.mayInteract` on
  their behalf. `BurstDiggerItem.breakIfWorthIt` asks a second time before calling Create at all,
  which is not redundant: `destroyBlockAs` throws its break particle *before* it posts the event, so
  a veto alone leaves a puff of debris on a block that then does not break.
  `theTunnellingDrillStopsAtTheWorldBorder` covers it, by shrinking the border to one block wide
  around the block being drilled so two of the eight neighbours stay inside it and six do not — a
  blanket refusal would pass a test that only checked that something survived. Spawn protection
  itself cannot be GameTested: a test server has no ops, and `isUnderSpawnProtection` returns false
  on that before it looks at a position.
- **The saw charges per *cut*, not per tree actually felled, and that is not an oversight.** Making
  the charge conditional on the felling having found something looks fairer and is a hole: a tree
  taken down from the top is a run of cuts that each fell nothing, so the whole thing would come
  down at saw speed for no air at all. One use per cut is the bargain the Hand Drill strikes, and
  "once per tree, not per log" is kept by the cascade guard rather than by counting. This was
  written the other way first, on the belief that cutting into the middle of a trunk paid a whole
  tree for nothing — `TreeCutter.validateCut` does refuse a log with more log underneath, but not in
  that geometry: a three-log column cut in the middle still fells the log above it, which is what
  the test showed.
- **The wrench finds its source through the chunks' block entities, not by reading block states.**
  `PneumaticWrenchItem.driving` runs every tick the button is held, on the client as well as the
  server. Sweeping `BlockPos.betweenClosed` over the reach is `(2r+1)^3` palette lookups a tick —
  4,913 at the default `wrenchRange` of 8 and 274,625 at the config maximum of 32, which is a
  meaningful slice of a server tick for one player. A source always has a block entity, so walking
  `LevelChunk.getBlockEntities()` over the nine chunks in range finds the same block for a few dozen
  map reads. Ask for the chunks with `load = false`: a source is only ever placed within arm's reach
  of somebody standing there, so an unloaded chunk cannot hold one, and asking would generate
  terrain to answer a question about a block that lasts half a second.
- **A source belongs to whoever last clicked its face**, and only their wrench renews it. Without
  that, `driving` answered with the nearest source whoever put it there — so two people wrenching in
  one workshop drove each other's blocks: one player's renewals kept the other's alive while their
  own lease ran out, and letting go removed a generator somebody else was still using. The owner
  narrows a search and nothing more; it is emphatically not what keeps the block alive, so a null
  driver fails closed and the lease still takes the block away. Placing over an existing source
  re-stamps it, on purpose: the block is invisible, and a player who could not take a spot someone
  else had claimed would be holding a wrench that silently did nothing.
  `aSourceAnswersOnlyToTheWrenchThatPlacedIt` covers it.
- **The client must not end the wrench's use just because it cannot see the block.** `place` is
  server side, so the source does not exist on the client for at least a tick and for as long as the
  connection costs. `LocalPlayer.stopUsingItem` sends no packet, so the server keeps driving — but
  the client's `isUsingItem` goes false with the button still down, and vanilla then re-fires
  `startUseItem` every four ticks for the whole window, re-placing and re-hissing all the way.
  Invisible in single player, obvious at 120 ms. `useOn` only plays its steam when the source was
  not already standing there, for the same reason.
- **`refuse()` sits behind the item's own cooldown, and has to.** A refusal is the one thing a tool
  says while a button is held, a `useOn` that returns FAIL has vanilla re-firing it every four
  ticks, and `playOnServer` broadcasts — so an empty tank held against a block was ten sound packets
  a second to everybody in earshot, and the person it was meant for learned nothing after the first.
  Using the item cooldown rather than a timestamp of ours also greys the icon, which is the visible
  half the mod otherwise lacked. It gates `useOn` for those ten ticks, which costs nothing: a tool
  with no air had nothing to do with the click.
- **`ItemStack.mineBlock` runs *before* the block is removed.** That is what lets the saw hand the
  still-standing log's position to `TreeCutter.findTree`, which reads the block above it. Move the
  work to a break event and the tree search gets a different world than Create's own saw gives it.
- **Create's `BlockHelper.destroyBlockAs` calls `usedTool.mineBlock` for every block it takes down.**
  A tool whose own `mineBlock` calls back into it therefore recurses — the 3x3 driller would tunnel
  to the world border and the saw would fell a forest one log at a time, charging for each. Both go
  through `Excavation.cascade` and both refuse to expand while `Excavation.cascading()` is true.
  `theTunnellingDrillDoesNotTunnelForever` covers it, and was mutation-checked by deleting the guard,
  which drains the whole 900-air tank on one click.
- **A block's `useItemOn` runs before the held item's `useOn`, and Create cancels the click for some
  blocks outright.** Create listens on `RightClickBlock` with `ItemUseOverrides`, which blocks opt
  into at registration (`.onRegister(ItemUseOverrides::addBlock)` — the Hand Crank does). For any
  block on that list it calls the block's own `use` and cancels the event, so a held item's `useOn`
  is never reached: right-click a Hand Crank with the Pneumatic Wrench and you turn the crank by
  hand, not place a source. Anything in this mod that must beat a Create block to a click has to be
  a `RightClickBlock` listener at `EventPriority.HIGH` — at equal priority Create's runs first,
  because Create loads first, and a cancelled event never reaches the listeners behind it.
- **`useOn` returning FAIL swallows the click; PASS lets it through to `use`.**
  `Minecraft.startUseItem` only falls through to `use` when the block click was *not* consumed and
  *not* FAIL, and a swallowed click is also a click the offhand never sees. So the Grinder and the
  Buffer both return PASS for "this block is not one I treat" and FAIL only for "this block is one I
  treat and the tank is empty" — the second found something to do and could not do it, the first
  found nothing and has no business eating the click. The Buffer used to need both hooks, because it
  polished a stack out of the other hand on `use`; that is gone, and every tool here now works on a
  block in the world.
- **The tunneller re-casts the player's aim to find the face it drilled.** `mineBlock` is handed a
  position and no face, and the nearest axis of the *look* vector is not the same thing: cutting a
  trench you break the top of a block while looking mostly forwards, and a look-axis slice stands up
  on end and digs a hole instead. `BurstDiggerItem.plane` clips from the eyes with
  `getPlayerPOVHitResult` and only trusts the answer when it lands on the block being broken —
  otherwise it falls back to the look axis, which is what the tool used everywhere before.
  `theTunnellingDrillFollowsTheFaceYouDrilled` is built on exactly the geometry where the two
  disagree, and `theTunnellingDrillFollowsWhereYouLook` still covers the fallback, because a test
  calling `mineBlock` directly never satisfies the cast.
- **A changed config *default* does not reach a world that already has the config file.** Raising the
  wrench's RPM failed its own new test at first, reporting the old 256 SU, because
  `run-gametest/config/createpneumatictools-server.toml` had been written by an earlier run. Delete
  the file — in `run/` and `run-gametest/` both — after changing a default, or the test suite is
  reading last week's numbers.
- **A `usesPerTank` above 900 does nothing, and nothing says so.** `BacktankUtil.canAbsorbDamage`
  charges `max(maxAirWithoutEnchants() / usesPerTank, 1)`, so once the integer division reaches 1 a
  larger rating buys no more uses — at Create's default 900 air, 900 uses is the ceiling however big
  the number in the config. Every default here divides 900 exactly (900/1, 450/2, 300/3, 100/9, 90/10,
  60/15, 50/18), which is deliberate: a rating that does not divide it is silently rounded down by
  that same integer division. The config comment on every rating now says so.
- **Create's Capacity enchantment is the one thing that goes past that ceiling**, and it works because
  the *cost* is computed from `maxAirWithoutEnchants()` while the tank's size comes from `maxAir()`.
  Capacity III is +900 air at Create's defaults and no change to the price, so every tool in this mod
  gets exactly twice the uses. Nothing else on a backtank touches air: the tank is in
  `minecraft:chest_armor` so Unbreaking and Mending will go on it, but `consumeAir` writes the
  `BACKTANK_AIR` component directly and never calls `ItemStack.hurt`, so neither can see it. A
  Netherite backtank holds no more air than a Copper one either — `maxAir` reads the config and the
  enchantment, never the item.
- **The five diggers can be enchanted, but only at an anvil.** They are in
  `#minecraft:enchantable/mining_loot`, so Fortune and Silk Touch apply and are worth having — those
  two are the *only* vanilla enchantments whose `supported_items` is that tag, and no vanilla tag
  includes it, so nothing else is inherited. `onlyTheDiggersTakeLootEnchantments` asserts the whole
  arrangement off `CPTItems.all()`, so a tool added without its tag entry fails there rather than
  shipping unenchantable; it tests `ItemStack.supportsEnchantment`, which is the call `AnvilMenu`
  actually makes, rather than `Enchantment.canEnchant`. They are also in
  `#minecraft:enchantable/mining`, for Efficiency, which needed `DiggingHandler` to apply the bonus
  by hand because vanilla's gate refuses it — see above. Neither tag is referenced by any vanilla
  tag, so those three books are exactly what a digger accepts and nothing is inherited. An enchanting
  *table* refuses all nine regardless — `Item.isEnchantable` requires a `MAX_DAMAGE` component and
  these have no durability — which is the correct answer for a tool that runs on air, and the same
  answer Create's own Extendo Grip gives.
- **`mine()` in the tests is `ServerPlayerGameMode.destroyBlock`, not `Level.destroyBlock`.** The
  latter drops with `ItemStack.EMPTY` as the tool, so the loot table never sees the enchantments or
  the tier. Nothing noticed until a Silk Touch drill was asked for Stone and produced Cobblestone.
- **Instantly-breaking blocks are free, on purpose.** `PneumaticDiggerItem.mineBlock` returns early
  when `getDestroySpeed == 0`. Without it a walk through a meadow or a torch-lit cave drains a tank.
- **The Vacuum Wand leaves items that still have a pickup delay.** Pulling one back would undo the
  player's own Q press while they hold the button. And a pulse that catches nothing is free, or the
  wand is a slow leak in the pocket of anyone who leans on the right mouse button.
- **Entities dragged by the wand need `hasImpulse = true`.** Without it the server never decides the
  velocity is worth a packet, and the client draws the entity drifting on its own physics while the
  server has already moved it.
- **A GameTest mock player is never ticked, so it believes it is falling** — and vanilla divides the
  dig speed of anyone in the air by five. `worker()` calls `setOnGround(true)`; without it every
  speed assertion is off by that factor and reads as a broken multiplier.
- **GameTest mocks must be `GameType.SURVIVAL`.** A creative player short-circuits
  `BacktankUtil.canAbsorbDamage` before it has looked at a tank, so a test using a creative mock
  passes with the whole air system deleted.
- **The backtank goes in the chest slot.** `BacktankUtil`'s own supplier walks the *armour* slots for
  anything tagged `create:pressurized_air_sources`. Put one in the inventory and every test reads as
  an empty tank.
- **A bare `new ItemEntity(level, x, y, z, stack)` has an upward hop and no pickup delay.** Both
  matter in the wand's tests: the hop reads as motion the wand caused, and the missing delay makes
  the "leave fresh drops alone" test assert against the wrong entity. Zero the motion and call
  `setDefaultPickUpDelay()`.
- **The jackhammer's config is a *time*, not a speed.** Vanilla break time is
  `hardness * 30 / speed`, so quoting a speed makes Obsidian at 50 hardness take seventeen times as
  long as Deepslate at 3 — which is exactly the difference the tool exists to erase. Asking for ticks
  and solving for the speed is what makes "five ticks" mean five ticks on both.
- **A flat break time already *is* an inverted hardness scale**, and mistaking the two for separate
  options wastes a design discussion. `poweredSpeed` grows *linearly* with hardness — 18x for
  Deepslate, 300x for Obsidian — and multiplying vanilla's `hardness * 30` by something linear in
  hardness cancels hardness out exactly. So "harder blocks get a bigger boost" and "every hard block
  takes the same time" are one behaviour described twice. To make harder blocks genuinely *quicker*
  the multiplier has to grow **faster** than linearly, which is what `jackhammerHardnessBias` does:
  `break time = breakTicks * (minHardness / hardness) ^ bias`, so the exponent has to go **up** from
  zero, not down. An exponent below 1 applied to the *speed* pushes the other way and hands back the
  hardness dependence the tool exists to remove.
  - Default **0**, which is the flat tool, so the shipped identity and the tooltip are untouched and
    the knob only ever moves toward "harder is faster".
  - Anchored on `jackhammerMinHardness` rather than a constant of its own: the softest block the tool
    bothers with is the one that still takes `breakTicks`. That also means the bias has to be
    **ignored when `minHardness` is 0** — with no threshold there is no softest qualifying block, and
    dividing by it would hand `Math.pow` an infinity and the network a speed of `Infinity`.
  - Past about bias 1 there is nothing left to give: a block cannot break in under one tick, so
    everything from roughly fifteen hardness up lands on that floor and Obsidian, Ancient Debris and
    a Netherite Block all feel identical. The config range stops at 2 and the comment says why.
  - The `bias <= 0` early return is not only for the hot path (this runs on every break-speed event on
    both sides): it keeps the default *bit*-identical to the pre-knob tool rather than
    identical-in-theory via `Math.pow(x, 0)`.
  - `aBiasedJackhammerGetsQuickerOnHarderBlocks` sets the config and restores it in a `finally`, the
    way the world-border test does — a test that only passes on a config somebody edited first is
    worse than none. It asserts the Obsidian-to-Deepslate ratio rather than a tick count, so it
    carries none of the constants, and it was mutation-checked by making the bias a no-op: 1.0 against
    an expected 4.08.
- **Adding a third-party mod to the dev runs goes through `runtimeClasspath`, and two better-looking
  routes do nothing.** `additionalRuntimeClasspath` is MDG's own hook and is documented as
  dependencies "that should not be considered boot classpath modules" — it drops the jar among gson
  and netty in `build/moddev/clientLegacyClasspath.txt`, where FML never scans it, so the mod is on
  the classpath and absent from `ModDiscoverer`'s list. `RunModel.getAdditionalRuntimeClasspathConfiguration()`
  reads like a per-run version of the same thing, but MDG 2.0.144 never registers it on the project,
  so adding to it from a `runs { client { } }` block is a no-op with no warning. What works is what
  Create already does here: be on `runtimeClasspath`. The `devMods` configuration extends it rather
  than using `runtimeOnly`, because this project publishes `from components.java` and `runtimeElements`
  extends `runtimeOnly` — a dev-only recipe viewer would be published as something consumers need.
  Diagnose this class of problem by reading the `Mod List:` block FML prints at startup, not by
  grepping the log for the mod's name.
- **The recipes and their unlock advancements are covered by `everyToolHasALoadedRecipe`**, because
  both halves fail silently and nothing else in the suite crafts anything — the whole `data/`
  directory could go missing and the tests would stay green. It also pins the rule that a
  `RecipeType.CRAFTING` recipe has an advancement and anything else does not, which is the same rule
  the generator follows.
- **The Borer's recipe is `create:mechanical_crafting`, and that means no recipe advancement.**
  `generate_recipe_advancements.py` writes one per `minecraft:crafting_shaped` recipe and skips
  everything else, which is right and not an oversight: the vanilla recipe book only shows vanilla
  recipe types, so an advancement granting a mechanical crafting recipe would unlock a page the book
  cannot draw. Create ships none for the Extendo Grip or the Crushing Wheel either. JEI and EMI read
  the recipe manager directly and show it regardless.
- **A burst charges once at any width, and the guard is what makes that true.** `BurstDiggerItem` is
  one class for the 3x3 and the 5x5 because everything hard about a burst — finding the drilled face,
  not recursing through Create's break helper, asking `level.mayInteract` on twenty-four blocks
  vanilla never heard about — is the same problem at any radius. `theBorerChargesOncePerBurst` is not
  a copy of the Tunnelling Drill's test for the sake of symmetry: a 5x5 that recursed would clear the
  whole GameTest box on one click rather than merely overcharging.
- **The saw has no cap on tree size.** Create's own saw has none either, and a capped saw that
  silently leaves half a canopy standing is worse to use than a slow one. If a giant jungle tree ever
  becomes a real problem, the fix is a config cap that refuses to fell rather than one that fells
  half — and `TreeCutter.Tree` does not expose its block lists, so counting first means not using
  `TreeCutter`.

## The 3D models

The tools are real geometry with moving parts, not sprites — `tools/generate_models.py` writes a base
model and one or more animated parts per tool, and `PneumaticToolRenderer` draws them through Create's
`CustomRenderedItemModelRenderer`. The things that will bite you:

- **The tool points along +Y, its grip hangs toward -Z, and +X is across it.** That frame comes from
  vanilla's `crossbow`, the one vanilla item held pointing *away* from the player, whose transforms
  are written for a model whose forward direction is +Y. An earlier pass authored these on a diagonal
  like a pickaxe, and the result was a drill held up beside your ear with the bit pointing at the sky
  — which is what "the moving parts are at the top instead of facing the direction you are using
  them" means, and it is a display-transform problem, not a geometry one.
- **The Z term of a display rotation is a yaw, and its sign decides whether you can see the tool
  work.** Negative turns the muzzle away from the crosshair; since the right hand already sits at the
  right edge of the screen, that hides the head behind the barrel and the tool animates where nobody
  can see it. Positive brings it round into a three-quarter view.
- **The display translations are not vanilla's, and the Z is why.** A sprite is one pixel deep and
  sits wherever it is put; this model is sixteen long, so after the tip forward half of it is *behind*
  the pivot, pointing at the camera. At vanilla's +1.13 the butt of the tool fills the corner of the
  screen.
- **The barrel is above the middle of the model** so the grip can hang under it, which is why every
  mount carries an `up` as well as a `forward`. A part left at zero spins inside the grip.
- **A model file's rotation is `Rx * Ry * Rz`, not `Rz * Ry * Rx`.** It is fed to JOML's
  `rotationXYZ`, whose Z term is innermost — so the 45 degrees that turns an axis-aligned model into a
  diagonal one is applied to the geometry *first*, adjacent to that Z term, and therefore composes by
  plain addition. `display()` is nine lines because of that. It was first written as a full
  compose-and-decompose on the opposite assumption, which produces different angles that still look
  like plausible numbers — verified against JOML rather than remembered.
- **A solid tool needs about two thirds the hand scale of a sprite.** Vanilla's scales are written
  for something 16x16x1; these are 5x16x13. `BULK` is that factor.
- **Every moving part is authored centred on (8, 8, 8).** The renderer's pose origin is the middle of
  the item, so a part centred there needs no translate-and-undo around its rotation. Author one
  anywhere else and it orbits the tool instead of spinning.
- **Translate before you rotate.** In `PneumaticToolRenderer` the part is moved out along the tool and
  *then* spun. The other order swings it around the tool — a fairground ride, not a drill.
- **The mount distances live in two files and are checked.** `generate_models.py` lays the geometry
  out from its own copy of `PLACEMENTS` and then greps `CPTPartials.java` for every number, failing
  the build if they disagree. It folds `BARREL_UP` into literals first — including expressions like
  `BARREL_UP - 2.0F` — and checks the constant itself agrees, since folding in a wrong value would
  make every other comparison pass.
- **`check_clearance` refuses geometry a spinning part would sweep through.** A turning part traces
  an annulus between the nearest and furthest its own material reaches from its axis; static geometry
  standing in that band is cut through twice a turn, and what a player sees is the head of the tool
  flickering. Two arrangements are exempt: a housing that entirely encloses the swept circle (that is
  what a chuck is), and anything inside the bore of a ring-shaped part. It found real clipping on five
  of the eight tools the day it was written, including two nobody had noticed.
- **`check_coplanar` also compares a moving part against its housing, but only on the axes that do
  not move.** A part spinning about Y keeps its Y coordinates for ever, so a shaft whose tail ends
  flush with the back of its chuck z-fights there permanently; its X and Z faces sweep away from
  whatever they line up with and are nobody's problem. A hammering part is the other way round. That
  distinction is the gap the first pass fell through: the bit's tail was flush with the back of the
  chuck on every drill in the mod, `check_clearance` exempted it because the chuck encloses the bit,
  and `check_coplanar` only looked within a group. It shows up worst on the tunnelling drill, whose
  back plate is broad and flat and carries three of them.
- **`check_coplanar` refuses two boxes that overlap *and* share a face plane.** That is the definition
  of z-fighting, and it is the other half of "clipping": the saw's teeth used to be four bars laid
  right across the disc at exactly the disc's thickness, which shimmered over the whole blade. Boxes
  that merely *touch* are fine — their coincident faces point opposite ways, so only one is ever
  drawn. Static geometry and moving parts are deliberately not checked against each other; they move
  relative to one another, so `check_clearance` is what covers that pairing.
- **Detail has to be proud of a surface and inset from its edges.** Everything in `DETAILS` — ribs,
  bolt heads, knurling, the trigger guard, the gauge, the hose — sticks out of a face rather than
  sitting flush in it, and is narrower than what it sits on. A rib flush with the top of the barrel
  shares a plane with it and shimmers; `check_coplanar` will say so.
- **The icon is a three-quarter view, not an elevation.** Create draws every one of its own
  3D-rendered items that way — the Extendo Grip, the Potato Cannon and the Wrench all carry a `gui`
  rotation on all three axes — and a flat side view among them reads as the one item that forgot to
  have depth. `gui_view()` composes it (side view, then a yaw and a pitch in screen space) and
  decomposes the result to Euler, because adding to the middle term instead would roll the tool about
  its own barrel. It checks its own answer by rebuilding the matrix: Euler extraction has two
  conventions that differ by the order of two multiplications, and both give a believable angle.
- **A wheel mounted out to the side cannot be on the centre line.** A disc big enough to look like a
  disc has a radius that reaches back through the body, the collar and the trigger. The saw and the
  grinder carry theirs out to the side on a stub spindle, which is where a circular saw and an angle
  grinder put them anyway.
- **A side-mounted wheel goes on the tool's *left* flank — negative `across` — or nobody ever sees it
  move.** The first-person transform is `Rx(-90) Ry(0) Rz(30)`, which lays the tool pointing away and
  to the left; work model `+X` through it and it lands at `(0.87, 0, -0.5)` — toward the right of the
  screen *and away from the camera*. So a wheel on `+X` is behind the barrel, at the edge of the
  screen, and the one part of the tool that moves is the one part you cannot see. On `-X` it is on the
  near side and turned toward the middle. Both wheels were built on `+X` first and it looked
  reasonable in every still: it is only wrong from where the player stands, which is the one viewpoint
  none of the geometry checks has. The cost is that the *icon* turns the other way — `gui_view` puts
  `+X` toward the viewer — so the wheel is on the far side of the barrel there. It stays perfectly
  legible because a disc of radius 4.5 offset by 4 reaches well past a body 5 wide, and the icon is
  the view that matters less: it is one frame, and the tool is not moving in it.
- **The three wheeled tools were one tool in three colours, and the fix was not more detail.** They
  shared a head box, a spindle, a mount, `SPIN_FACE` and a disc of the same radius; only the disc's
  thickness and material differed, which at sixteen pixels is nothing. What separates them now is
  what separates the real tools: the **saw** is a big thin blade slung *below* the barrel with a hood
  over it and a shoe under the nose; the **grinder** is a small thick puck at the nose behind a half
  guard, with a side handle out the far flank; and the **buffer** is not a side-mounted wheel at all
  — its pad faces forward on the barrel's own axis and turns `SPIN_AXIAL`. A different *motion* is
  worth more than any amount of extra geometry, because it reads in the hand as well as in the slot.
- **A guard has to sit outside its wheel's outermost sweep, and at these radii there is no room.**
  `check_clearance` measures the AABB of a *rotated* tooth, which is larger than the tooth: the saw's
  blade measures 4.5 but sweeps 5.50, so a rim strap round it needs the space between 5.5 and the
  edge of the item box, and there is none above a barrel at z=10.5. Two things follow. The saw's
  blade hangs below the barrel (`BARREL_UP - 2.0`) rather than on it, which is both where a circular
  saw's blade is and the only place that leaves room for a hood; and both guards are built as
  half-discs laid against the wheel's *inboard* face, which the clearance check exempts because they
  do not overlap the wheel's own slab. That is what a real guard's side plate is anyway, and it can
  be as large as it likes.
- **`clipped()` is how a half-disc gets made, and it has to cut across the rows.** A `disc` in plane
  `yz` is built as rows stacked along **y**, each spanning **z** — so filtering whole boxes by their
  centre gives a fore/aft half and can never give a top/bottom one. `clipped` trims each box's
  coordinate instead, which works on either axis. The grinder's guard is the rear half (cut on y, the
  row axis, so whole rows drop); the saw's hood cheek is the upper half (cut on z, so every row is
  trimmed to half its span).
- **Anything a moving part sits inside has to be built as walls.** Cuboids cannot be hollow, and a
  solid box in the same place looks identical from outside while swallowing the part whole — which is
  what the vacuum's flare did to its impeller. `tube()` builds the square annulus, and it takes a
  centre because the barrel is not the middle of the model.
- **`check_bounds` refuses anything leaving the 0..16 box.** Coordinates outside it are perfectly legal
  and do render, which is exactly why it needs a check rather than a glance: an overhanging blade is
  not an error anywhere, it simply draws outside its slot and is clipped, and that reads as a design
  choice.
- **A disc is one box per row of a pixel circle**, so the blade in your hand is the same circle as the
  icon in the slot. Overlapping rotated squares were tried first and are not round — a square's
  corners reach 1.41 times its half-width, so their union is a star with an outer radius half again
  its inner one.
- **The tunneller's three bits are a triangle, not a row.** The icon is a side view, so three abreast
  collapse into one and the tool stops looking like three drills. It and the jackhammer are in
  `NO_CHUCK`: their own heads stand in for the shared chuck, which otherwise either misses two of the
  three bits or overlaps a bigger box in the same place.
- **The saw's teeth are four boxes, not eight.** A bar laid across the whole disc pokes out at both
  ends; four bars, two on the axes and two of those turned 45 degrees, give eight points. They only
  show when the saw is standing still, which is when a bare disc would read as a grinding wheel.
- **The material textures are grain and nothing else.** Every face takes its UV from its own
  measurements, sampling a panel's top-left corner, so a panel has to look right cropped to any
  rectangle from its origin. Anything with a frame, a border or a feature in it breaks.
- **Every link in the render chain fails silently**, which is why `CPTModelCheck` exists. A tool whose
  `initializeClient` never reached `CPTRenderers` keeps its base model and never animates; a partial
  whose file is missing bakes as the placeholder; a base model that failed to parse falls back to a
  flat sprite. None of the three logs anything and all three survive a glance.
- **The client's selected hotbar slot is not synced from the server.** Which item is drawn in hand
  comes from the client's own `Inventory.selected`, so setting it server-side changes nothing you can
  see. Cost an afternoon of photographs of the wrong tool.

### Animation

- **"Working" is either mouse button held, not `isDestroying`.** Half the tools are worked with the
  right button and half with the left, because breaking a block is an attack. `isDestroying` sounds
  like it covers the left half and does not: it only goes true once a block has actually begun to
  give, so a drill pointed at bedrock, at air, or at a block that pops in one tick never spun up.
  Holding the button is when a real air tool is running, whether or not it is achieving anything.
- **The tools do not swing.** `IClientItemExtensions.applyForgeHandTransform` replaces the entire
  first-person arm transform, so `PneumaticItemExtensions` reproduces vanilla's resting position and
  equip bob and simply omits the attack transform — then adds a push forward and a tip down under
  load, and a judder in time with the chisel for the jackhammer. You hold a drill against the work;
  you do not swing it.
- **That is first person only, and cannot cheaply be more.** The third-person swing comes from
  `attackAnim`, and `HumanoidModel.setupAttackAnimation` runs *after* `poseRightArm` — so a custom
  `ArmPose` via `getArmPose` is overwritten by it and cannot help. Suppressing it needs a mixin into
  vanilla's model or into `Minecraft.continueAttack`; a rendering mixin is a poor trade against other
  mods for something only bystanders see. If it ever becomes worth it, that is where to look.
- **`ToolAnimation.load` is the throttle with the idle part removed**, and it is what the hand
  transform leans on. The parts turn over whenever there is air in the line; the tool should only
  press into the work when there is work. Derived, not tracked, so there is no second clock.
- **The shared phase must not be wrapped to 360, because each tool multiplies it afterwards.** That
  is what made the Saw's blade snap back to a starting position about twice a second: `angle()`
  returns `phase * multiplier`, and `360 * multiplier` is a whole number of turns only when the
  multiplier is an integer, so every wrap jumped the part by `360 * frac(multiplier)` — 216 degrees on
  the Saw at 1.6, 168 on the Vacuum Wand at 1.5, 72 in the Jackhammer's sine argument at 2.2. Seven of
  the nine tools had it and the two set to exactly 1.0 did not, which is why it read as one tool being
  broken. Interpolate, scale, *then* take the whole turns off. The precision the wrap was there to
  protect is why `phase` is now a `double`: a float accumulating 600 degrees a second runs out of
  mantissa in a long session and the spin stutters, and there is no bound that is a whole turn for
  every multiplier at once.

### Photographing the tools

```bash
./gradlew runClient -PquickPlay=<world> -Dcreatepneumatictools.photos=true   # a different tool per frame
./gradlew runClient -PquickPlay=<world> -Dcreatepneumatictools.photos=3      # hotbar slot 3, every frame
```

`CPTPhotoShoot` fills the hotbar, straps on a charged backtank, freezes the clock and weather, and
calls the same `Screenshot.grab` that F2 does, three frames a few ticks apart. Holding one slot for
all three is how the animation gets checked: a still cannot tell a spinning blade from a stopped one,
and three of the same blade four ticks apart can. Pinning a slot also **holds the attack key down**
for the run, which is what makes the frames worth comparing — the parts spool up, the tool leans into
the work, and vanilla fires a swing that the hand transform is supposed to be ignoring. All three of
those are invisible in a photograph of a tool at rest. Shots land in `run/screenshots/`.

**`build.gradle` has to hand the flag across, and for a while it did not.** A `-D` on the gradle
command line sets a property on the *daemon*, not on the forked client, so the documented command ran
the world, logged nothing and took no photographs — a failure that looks exactly like the shoot
deciding there was nothing to do. The `client` run block now forwards
`createpneumatictools.photos` explicitly; without that line this whole section is fiction.

Three notes from using it. Kill the client with `pkill` often enough and the world's chunks corrupt —
the symptom is a "Failed to load chunk" toast in the corner of every photograph, which is worth
noticing before the shot goes in the README. Delete the save and re-copy `run-gametest/world`, whose
name inside `level.dat` need not match the folder, since quick play keys off the folder. And a stale
`session.lock` in the save will refuse the next launch.

The README's two images are crops of one frame — `branding/tools-in-hand.png` and
`branding/the-nine-tools.png`. They are checked in rather than generated, so nothing regenerates
them when a model changes. Retaking them is one run and two crops, at a 1708x960 window:

```bash
rm -f run/saves/Photos/session.lock                 # or the next launch is refused
./gradlew runClient -PquickPlay=Photos -Dcreatepneumatictools.photos=4   # slot 4 is the Saw
S=run/screenshots/<the-first-of-the-three>.png
magick "$S" -crop 1708x610+0+350  branding/tools-in-hand.png
magick "$S" -crop 740x100+484+862 branding/the-nine-tools.png
```

The hotbar crop is not eyeballed: vanilla draws the hotbar 182x22 GUI pixels wide, centred, flush
with the bottom of the screen, so at GUI scale 4 it is 728x88 at x=(1708-728)/2. A few pixels of
margin either side is what the numbers above add. Change the window size and that arithmetic is what
to redo.

**Photograph the Saw, not the Hand Drill.** The hero shot has one job beyond looking like a tool —
showing that the thing which *moves* can be seen — and the Saw's blade is the biggest moving part in
the mod. A drill's bit is four pixels at the end of a barrel and photographs identically whether the
left-flank mount survived or not.

## Art

The remaining generators write PNGs by hand with `zlib` and `struct`; there are no Python
dependencies, and all of them are byte-deterministic so the CI gate that regenerates everything and
fails on a diff stays honest.

- **The badge convention is shared with the sibling addons, and Create's art is not used.** Create's
  code is MIT but its `assets/` are All Rights Reserved. The badge's white-ringed azure disc is a
  convention, not artwork; the subject is drawn from scratch.
- **The subject is the Pneumatic Wrench, in three-quarter view** — brass barrel with a lit top face,
  a bright steel socket with a dark bore on the nose, a pistol grip behind a trigger guard, and a
  copper air fitting at the base of the grip. It replaced a rock drill that was upright and
  symmetrical, and the right angle is what does the work: stood upright the drawing could belong to
  any tool in the mod.
- **The badge is drawn in cabinet projection**, the same argument `gui_view()` makes about the item
  icons: every box shows its front face, with the top and the right end extruded up-right by two
  cells, in the material's light and dark tones. There is no shading model — three tones per
  material and nothing else — so a face painted in the wrong one of the three flattens its box back
  into a rectangle. A flat elevation was drawn first and is perfectly legible; it just reads as the
  one badge that forgot to have depth next to Create's own 3D-rendered items.
- **The head is bright steel and the grip is dark andesite**, which is not what the model is. In
  their true neighbouring greys the two merge into a single shape at thumbnail size, which is where
  a badge does most of its work.
- **The trigger guard's hole is open, and only survives because `outside_cells()` can tell an
  enclosed hole from the outside.** Fill it in and the silhouette is an L, which is a pipe fitting.
  It is 2 cells wide and 3 tall on purpose: the white stroke eats into it from both sides, and
  anything narrower closes up at 256px.
- **`check_fits` refuses a subject whose stroke would reach the ring.** The subject is drawn *over*
  the disc, so overflowing it does not fail or warn — it produces a tool outline lying across the
  white ring, like a sticker applied crookedly. The binding measurement is the distance to the
  furthest *opaque corner*, which is not the width or the height and is not something to eyeball: the
  wrench at `SPRITE_SCALE` 12 overshoots by 14px, at 11 by 4, and fits at 10. It caught this on the
  first redraw the check existed for.
- **`SPRITE_SCALE` must stay a whole number**, or the subject's pixels stop being square. That is why
  `--size` must be a multiple of 256.
- **The badge is still a character grid**, and so were the item sprites before the tools had geometry.
  A 16x16 item is a picture, and a picture is easier to judge and to fix when it is legible in the
  source. The block-texture generators in the sibling repos are procedural for the opposite reason: a
  block face is a pattern, and a function describes a pattern better than a picture of one does.

## The Pneumatic Wrench, and the block it puts down

The wrench is the only tool that registers a block, and the block is the only way the tool can work.
Create's rotation is a graph of block entities: a member is found by its neighbours asking the world
what is at a position, so an item in a hand can never join a kinetic network. While you hold the
button, the wrench keeps a real generator in the empty space against the face you aimed at.

- **It deliberately out-muscles a Hand Crank rather than matching it — on a shaft that is still.**
  64 RPM and 1024 Stress Units against the crank's 32 and 256 — twice the speed and four times the
  output,
  which is the difference between only just turning one Mechanical Press at 32 RPM and running one at
  64 with the belts that feed it. On a network already turning it matches instead and only lends its
  capacity; see `matchOrDrive` below for why that is the only thing it can safely do. Both numbers were checked against Create's own registration (`AllBlocks.HAND_CRANK`:
  `CStress.setCapacity(8.0)`, `setGeneratorSpeed(32)`), and
  `theWrenchOutMusclesAHandCrank` reads the figure back off the live network rather than out of the
  config, so it also fails if the source joins the network without registering its capacity. What
  the wrench does not have is a crank's permanence: it lasts as long as you stand there holding the
  button, and about three and a half minutes of backtank.
- **The source generates *nothing* on its first tick, and that is deliberate.** Create does not
  degrade a generator that disagrees with its network — it destroys the block.
  `RotationPropagator.propagateNewSource` calls `world.destroyBlock` when the conveyed speed's sign
  opposes the neighbour's, and `GeneratingKineticBlockEntity.applyNewSpeed` does the same to one
  overruled into turning the other way. The wrench's direction comes from `convertToDirection(rpm,
  FACING)` and FACING is the opposite of the clicked face, so *which end of the shaft you grab decides
  the sign* — and about half of all clicks onto a moving shaft used to lose. Worse, the wrench
  re-places the source every tick it is held, so the result was a block appearing and being destroyed
  once a tick, with a break sound each time.
  `PneumaticSourceBlockEntity.matchOrDrive` fixes it by not guessing: with `getGeneratedSpeed()`
  returning 0 the block is not a source (`isSource()` is defined as `getGeneratedSpeed() != 0`), so
  the propagator adopts it as an ordinary member and **sets its speed to whatever this position runs
  at** — with the full geometry of gearboxes, cog reversals and speed controllers already applied.
  Reading that back the next tick and generating exactly it cannot conflict, and for a reason rather
  than by luck: for an axis connection Create's modifier satisfies `modifier(a→b) == 1/modifier(b→a)`,
  so a block driven at `s = s_neighbour × m` which generates `s` conveys `s × 1/m = s_neighbour`
  straight back, and equal speeds are the one case the propagator leaves alone. Do not try to compute
  the match instead: `getConveyedSpeed` and `getRotationSpeedModifier` are both **private**, and
  reading the neighbour's speed directly is right only when the modifier happens to be 1.
- **Match on `getTheoreticalSpeed()`, never on `getSpeed()`.** `getSpeed()` returns 0 when the network
  is overstressed or the tick rate is frozen. Matching on it would decide there was nothing to match
  at exactly the moment there was — an overstressed line is the case a wrench lending torque is most
  wanted for.
- **The wrench supplies a fixed number of Stress Units, not a fixed number per RPM, and the override
  that does it is not optional.** Create bills a generator as `per-RPM × |generated speed|`
  (`KineticNetwork.getActualCapacityOf`), so a fixed per-RPM figure is a fixed *torque* whose SU rise
  with speed. That is correct for Create's own generators, because a generator owns its speed — a
  Water Wheel turns at 8 and that is that. It is wrong the moment `matchOrDrive` let this source
  adopt somebody else's speed: at a fixed 16 SU/RPM, matching a network already running at 192 RPM
  was worth **3072 SU for exactly the air that buys 1024** on a shaft of your own, so the faster the
  network you found, the more you were paid for joining it.
  `PneumaticSourceBlockEntity.calculateAddedStressCapacity` therefore holds the *product* fixed —
  `wrenchStressUnits / |speed|` — which is both the fix and what an air motor does: one tank rate is
  one power, and gearing decides whether it arrives as speed or as torque. Half the speed, twice the
  torque.
  - It must return **0** below a whisker of speed rather than divide. `capacity × 0` with an infinite
    capacity is `NaN`, which does not throw — it quietly makes the network's entire capacity NaN and
    every comparison against it false.
  - It must keep `lastCapacityProvided` up to date. That field is `protected` in `KineticBlockEntity`
    and `KineticNetwork.addSilently` reads it back to undo the contribution a source made before its
    chunk unloaded.
  - `BlockStressValues.CAPACITIES` is keyed on the **block**, so it cannot see a speed. What
    `CPTBlocks` registers is the nominal per-RPM figure at the wrench's own RPM — the truth for a
    still shaft and a placeholder otherwise. The live figure only ever comes from the override.
  - `aMatchedWrenchIsWorthItsFullTorqueOnASlowNetwork` and `...NoMoreOnAFastOne` differ only in the
    motor's RPM and must agree; that pair is the regression test, and deleting the override makes them
    report 256 and 3072 against an expected 1024. `theWrenchOutMusclesAHandCrank` pins the same figure
    on the free-running path. All three measure the same network twice — with the wrench, then after
    the lease lapses — so they test capacity reaching Create rather than this mod's arithmetic
    agreeing with itself.
- **Two opposed generators still break one of them, and that is Create's behaviour, not a bug here.**
  Take over an idle network with the wrench and then crank a Hand Crank the other way on it, and the
  crank is destroyed by `applyNewSpeed` — exactly as it would be against a Creative Motor. The
  matching above only covers what is *already* turning when the source arrives; nothing can be done
  about a generator that arrives later and disagrees, short of not being a generator.
- **The lease is the whole safety argument.** A wrench that *removed* its source on release would
  have to also remove it on: releasing the button, switching hotbar slot, walking out of range,
  dying, disconnecting, changing dimension, the chunk unloading mid-hold, and the client crashing.
  Miss one and an invisible generator turns somebody's factory forever — and it is invisible, so
  nobody will find it. Inverted, there is one rule and no list: the block counts down and deletes
  itself, and the wrench pushes the counter back up. Every case above becomes the same case, the
  renewals stopped, including the ones nobody thought of.
  `anUnrenewedSourceRemovesItself` covers it and was mutation-checked by breaking the countdown.
- **`aRenewedSourceStays` is the other half of that test.** Without it, the first one passes just as
  happily with the lease broken shut as with it working.
- **Never give the block `Properties.air()`.** It makes `BlockState.isAir()` true, and a block that
  reports itself as air is skipped by half the world — including the rotation propagator, which
  leaves the source sitting there connected to nothing. It cost an afternoon.
- **A generator has to call `updateGeneratedRotation()` from `initialize()`.** `getGeneratedSpeed` is
  only consulted when something asks the network to recalculate, and placing a block is not by itself
  such a thing. Create's own Creative Motor does exactly this. Leave it out and the block is in the
  world, in the network, and turning nothing.
- **The source is found by looking, not remembered.** No field, no data component, no map keyed by
  player: `PneumaticWrenchItem.driving` searches the blocks near the player. Walking away is then a
  search that comes back empty rather than a piece of state that has to be invalidated.
- **The block is tagged `create:non_movable`**, so a piston or a contraption cannot carry one off and
  strand it somewhere its owner will never return to.

## No Ponder scenes, deliberately

Create does not give its own equipment Ponder scenes — the Extendo Grip, the Potato Cannon and the
Sand Paper have none — because a Ponder scene shows a *machine working*, and a hand tool has no
machine to show. The tooltips (summary plus behaviours behind Hold Shift, via Create's own
`ItemDescription`) are what carries the explanation here instead, which is exactly what Create does
for the same class of item. Don't add scenes just because the sibling repos have them.

## Distribution

Releases go out through `publishMods` (`me.modmuss50.mod-publish-plugin`), driven by
`.github/workflows/release.yml` on a `v*` tag. Things in there that are decisions, not accidents:

- **`minecraft_version_range` is `[1.21.1,1.21.2)`,** not the MDK's default `[1.21.1,1.22)`. This mod
  needs Create 6 for 1.21.1; the wider range would let it install on 1.21.4 and break there instead
  of refusing.
- **The changelog drives the release notes.** `publishMods` reads the `CHANGELOG.md` section whose
  heading names the current `mod_version` and fails if there isn't one — a missing entry should stop
  a release rather than ship the previous version's notes under a new number. It is wired as a lazy
  provider so an ordinary `./gradlew build` never trips over it.
- **The CurseForge token is checked with curl before anything is built.** `publishMods` uploads to two
  sites, and a missing or expired token fails at *upload* — by which point GitHub may already have
  accepted the release, leaving a version published on one site and not the other, with no way to
  rename or replace a file on either. The status codes are the ones the real API returns: 200 valid,
  **400 malformed**, 401 absent. All three fail the release as a bad token; anything else fails it as
  "could not reach CurseForge", because a 502 is not a bad secret.
- **Running the release workflow by hand rehearses by default.** `workflow_dispatch` has a `dry_run`
  input defaulting to true. A tag push always publishes for real.
- **Both workflows re-run the generators and fail on a diff.** The sprites, the badge and the test
  template are generated, so a stale checked-in file would ship in the jar with nothing to notice it.
  The check stages first (`git add -A` then `git diff --cached`) because a bare `git diff` says
  nothing about a file a generator has newly created.
- **The `github` block sets `tagName` explicitly.** Without it the plugin invents its own tag from
  `mod_version`, so pushing `v0.1.0` files the release under a second, bare `0.1.0` tag on the same
  commit.
- **`archivesName` carries the Minecraft version** (`createpneumatictools-1.21.1-0.1.0.jar`). Neither
  site will let you rename a file after upload.
- **`LICENSE` and `NOTICE.md` ship in the jar under `META-INF/`.** This mod calls a great deal of
  Create's MIT code — `BacktankUtil` is its whole power model — and carrying the notice costs nothing.
- **CurseForge and GitHub only — Modrinth is deliberately not a destination.** Modrinth's Content
  Rules gained a section 6 on generative AI in August 2026. Its disclosure requirement is no obstacle,
  but **6.2 flatly bans project images "created or derived from generative AI output"** with no
  disclosure lane, and every sprite here was chosen by this mod's own tooling. CurseForge asks only
  that a *misleading* AI-modified showcase image carry a disclaimer, which a badge of the actual tool
  is not. To restore Modrinth: redraw the art by hand, add a `modrinth_project_id`, re-add the
  `modrinth` block to `publishMods` **and** `MODRINTH_TOKEN` to `release.yml` — an empty token fails
  at upload, not at configuration, which half-publishes a release.

## Design notes

`docs/` holds write-ups of tools that were thought through but not built, including the reasoning
against building them. Read the relevant one before starting such a feature, and update it if the
thinking changes — the point is that the analysis is not redone from scratch.

- `docs/rail-rider.md` — the Track Trolley. Re-examined against Create's actual API: `ITrackBlock`
  and `TravellingPoint` do give a handheld tool a track to follow, so the open questions are moving a
  player smoothly and sharing the graph with Trains, not the ones the first draft named
- `docs/pneumatic-stilts.md` — the best idea in the original list, and **dropped on purpose** after
  the technical blocker came off. The lease made the platform buildable; what killed it is that the
  platform is a real floor other players and mobs stand on, and it vanishes when its owner leaves
- `docs/wrench-as-a-generator.md` — why a handheld source of rotation cannot exist in Create's model,
  and what the Pneumatic Wrench does instead

## Conventions

Tabs for indentation, matching Create's own style. Registry classes are `CPT*` under `registry/`.
Nothing is committed without explicit instruction.
