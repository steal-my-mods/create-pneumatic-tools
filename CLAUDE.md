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
python3 tools/check_lang.py            # every tooltip key resolves, and nothing is orphaned
```

JDK 21 required. `gradle/gradle-daemon-jvm.properties` pins the daemon to it, so the commands work
without setting `JAVA_HOME` even when the default `java` is newer — don't delete that file, or
`./gradlew build` dies with "Could not create task ':test' ... Type T not present" on a newer JVM.

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
| `item/PneumaticDiggerItem` | Base for the four block-breakers. Owns the `TOOL` component and the `mineBlock` entry point |
| `tool/DiggingHandler` | `PlayerEvent.BreakSpeed`. **All** of a digger's speed comes from here, and only while the tank has air |
| `tool/CrankDriving` | `PlayerInteractEvent.RightClickBlock` at HIGH priority — the Pneumatic Wrench's whole behaviour |
| `tool/Excavation` | The re-entrancy guard that stops the tunnelling drill and the saw breaking blocks forever |
| `item/BatchProcessingItem` | Base for the Crimper and the Buffer: process the whole stack in your other hand, one click |
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

## Things that will bite you

- **A digger's `TOOL` component must stay at mining speed 1.** All the speed is added by
  `DiggingHandler`, and that is not a style choice: the component is baked into `Item.Properties` at
  registration, which happens long before the config file is read, so a speed put there could never
  be configurable. It also makes "empty tank" mean "the handler adds nothing" rather than a
  subtraction that has to be got exactly right. The visible cost is the Efficiency enchantment —
  vanilla adds `MINING_EFFICIENCY` only when the tool's own contribution already exceeds 1, so it
  never applies. None of these is enchantable at a table and "the air is what makes it fast" is the
  premise, so leave it. Haste and Mining Fatigue still work; they are multiplicative and land after.
- **`ItemStack.mineBlock` runs *before* the block is removed.** That is what lets the saw hand the
  still-standing log's position to `TreeCutter.findTree`, which reads the block above it. Move the
  work to a break event and the tree search gets a different world than Create's own saw gives it.
- **Create's `BlockHelper.destroyBlockAs` calls `usedTool.mineBlock` for every block it takes down.**
  A tool whose own `mineBlock` calls back into it therefore recurses — the 3x3 driller would tunnel
  to the world border and the saw would fell a forest one log at a time, charging for each. Both go
  through `Excavation.cascade` and both refuse to expand while `Excavation.cascading()` is true.
  `theTunnellingDrillDoesNotTunnelForever` covers it, and was mutation-checked by deleting the guard,
  which drains the whole 900-air tank on one click.
- **The Pneumatic Wrench must be at `EventPriority.HIGH`.** Create has its own listener on
  `RightClickBlock` — `ItemUseOverrides`, which the Hand Crank opts into at registration with
  `.onRegister(ItemUseOverrides::addBlock)`. For any block on its list it calls the block's `use`
  itself and cancels the event. At equal priority Create's runs first (Create loads first) and a
  cancelled event never reaches the listeners behind it, so a NORMAL-priority handler here is never
  called at all and the crank turns on hunger exactly as if this mod were not installed. It is not a
  subtle failure to *watch* — the crank still turns — which is why it needs a test:
  `theWrenchTurnsACrankOnAirRatherThanOnFood` asserts the food bar, not the rotation.
- **A block's `useItemOn` runs before the held item's `useOn`.** True in general, not only for the
  crank. Anything that must beat a Create block to the click has to be an interaction event.
- **`useOn` returning FAIL swallows the click; PASS lets it through to `use`.** The Buffer needs both
  hooks — waxing on a block, polishing in the air — and `Minecraft.startUseItem` only falls through
  to `use` when the block click was *not* consumed and *not* FAIL. So "this block will not wax" must
  return PASS, or facing a wall would stop you polishing. "This block would wax but the tank is
  empty" returns FAIL, which is correct: the click found something to do and could not do it.
- **Dirt has a pressing recipe.** `create:pressing/path` turns dirt, coarse dirt, rooted dirt,
  mycelium and podzol into a dirt path. A test that wants something the Crimper will *not* take needs
  something else; a stick works. This cost a test.
- **The tunneller re-casts the player's aim to find the face it drilled.** `mineBlock` is handed a
  position and no face, and the nearest axis of the *look* vector is not the same thing: cutting a
  trench you break the top of a block while looking mostly forwards, and a look-axis slice stands up
  on end and digs a hole instead. `TunnelDrillItem.plane` clips from the eyes with
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
- **A wheel cannot be mounted on the centre line.** A disc big enough to look like a disc has a radius
  that reaches back through the body, the collar and the trigger. The saw, grinder and buffer carry
  theirs out to the side on a stub spindle, which is where a circular saw and an angle grinder put
  them anyway.
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

Two notes from using it. Kill the client with `pkill` often enough and the world's chunks corrupt —
delete the save and re-copy `run-gametest/world`, whose name inside `level.dat` need not match the
folder, since quick play keys off the folder. And a stale `session.lock` in the save will refuse the
next launch.

## Art

The remaining generators write PNGs by hand with `zlib` and `struct`; there are no Python
dependencies, and all of them are byte-deterministic so the CI gate that regenerates everything and
fails on a diff stays honest.

- **The badge convention is shared with the sibling addons, and Create's art is not used.** Create's
  code is MIT but its `assets/` are All Rights Reserved. The badge's white-ringed azure disc is a
  convention, not artwork; the subject is drawn from scratch. The handle on the drill is open, and it
  only survives because `outside_cells()` can tell an enclosed hole from the outside — fill that in
  and the badge reverts to a padlock, which is what three drafts of it were.
- **The badge subject steps in and out four times on the way down** — handle, wider shoulder, body,
  wider chuck flange, then six courses of bit narrowing to a point. Draw the handle and the body the
  same width and the silhouette is a padlock; the taper is what makes it a tool.
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

- **It deliberately out-muscles a Hand Crank rather than matching it.** 64 RPM and 16 SU per RPM
  against the crank's 32 and 8 — twice the speed and four times the torque, which is the difference
  between only just turning one Mechanical Press at 32 RPM and running one at 64 with the belts that
  feed it. Both numbers were checked against Create's own registration (`AllBlocks.HAND_CRANK`:
  `CStress.setCapacity(8.0)`, `setGeneratorSpeed(32)`), and
  `theWrenchOutMusclesAHandCrank` reads the figure back off the live network rather than out of the
  config, so it also fails if the source joins the network without registering its capacity. What
  the wrench does not have is a crank's permanence: it lasts as long as you stand there holding the
  button, and about three and a half minutes of backtank.
- **The lease is the whole safety argument.** A wrench that *removed* its source on release would
  have to also remove it on: releasing the button, switching hotbar slot, walking out of range,
  dying, disconnecting, changing dimension, the chunk unloading mid-hold, and the client crashing.
  Miss one and an invisible generator turns somebody's factory forever — and it is invisible, so
  nobody will find it. Inverted, there is one rule and no list: the block counts down and deletes
  itself, and the wrench pushes the counter back up. Every case above becomes the same case, the
  renewals stopped, including the ones nobody thought of.
  `anUnrenewedSourceRemovesItself` covers it and was mutation-checked by breaking the countdown.
- **`arenewedSourceStays` is the other half of that test.** Without it, the first one passes just as
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
