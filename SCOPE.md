# MineScore: Project Scope (v5.1, FINAL)

> **Status:** scope signed off on 2026-11-18. M0–M8 are built. Records are called **Blank Vinyl** and **Vinyl Record** (renamed from Blank CD and Encoded CD on 2026-11-19; the item IDs stay `blank_cd` and `encoded_cd`). On 2026-11-19 the Multi-Effects Unit (§3.10) became the new **M6**, so power, redstone and everything after moved up one number.
> **Open questions:** none. §16 records the final decisions, S1–S8.
> **Target:** Forge 47.x, Minecraft 1.20.1, Java 17 first. NeoForge, Fabric and newer Minecraft versions come later.
> **Sides:** client + server mod. Singleplayer and multiplayer are supported equally.
> **Art:** all original: models, textures, animations, GUIs, particles, sounds, logo, manual art and advancement icons, drawn in code (`tools/textures.py`), never vanilla textures.
> **Recipes:** designed in `tools/recipe_maker.py` (§3.12).
> Items marked *(proposed)* are defaults that may still change.

---

## 1. Names

| Kind | Display name | Registry ID | Notes |
|---|---|---|---|
| Item | Blank Vinyl | `minescore:blank_cd` | Stacks to 16 |
| Item | Vinyl Record | `minescore:encoded_cd` | Stacks to 1. Shows the song title. Can be dyed and trimmed |
| Item | MP3 Player | `minescore:mp3_player` | |
| Item | 12 Gauge Speaker Wire | `minescore:speaker_wire_12` | |
| Item | 16 Gauge Speaker Wire | `minescore:speaker_wire_16` | |
| Item | 20 Gauge Speaker Wire | `minescore:speaker_wire_20` | |
| Item | Wire Cutters | `minescore:wire_cutters` | |
| Item | Blank Score | `minescore:blank_score` | |
| Item | Score | `minescore:score` | Shows *Untitled Score* or its custom title |
| Item | MineScore Manual | `minescore:manual` | Built-in guidebook |
| Block | Disc Cutting Lathe | `minescore:disc_cutting_lathe` | 1 × 0.5 × 1 · pickaxe |
| Block | Audio Mixer | `minescore:audio_mixer` | 3 × 1.5 × 1 · pickaxe |
| Block | Channel Manager | `minescore:channel_manager` | 1 × 1 × 1 · pickaxe |
| Block | Amplifier | `minescore:amplifier` | 1 × 1 × 1 · axe |
| Block | Tweeter Speaker | `minescore:tweeter_speaker` | 0.5 × 0.5 × 0.5 · pickaxe |
| Block | Middle Speaker | `minescore:middle_speaker` | 1 × 1 × 1 · pickaxe |
| Block | Subwoofer | `minescore:subwoofer` | 1 × 2 × 1 · pickaxe |
| Block | Writer's Desk | `minescore:writers_desk` | 2 × 1 × 1 · axe |
| Block | Cable Anchor | `minescore:cable_anchor` | Any tool or hand |
| Block | Vinyl Player | `minescore:vinyl_player` | 1 × 0.5 × 1 · axe *(proposed)*. Plays Vinyl Records and can act as a system source (replaces the jukebox plans) |
| Block | Vinyl Display | `minescore:vinyl_display` | Like an item frame, for Vinyl Records only · any tool |
| Block | Music Cabinet | `minescore:music_cabinet` | 1 × 1 × 1 · axe. Holds 16 Scores and 8 Vinyl Records (§3.11) |
| Block | Multi-Effects Unit | `minescore:multi_effects_unit` | 1 × 1 × 1 · pickaxe. Six effect-card slots (§3.10) |
| Item | Effect cards ×6 | `minescore:card_drive`, `card_modulation`, `card_delay`, `card_reverb`, `card_amp_cab`, `card_dynamics` | Stack to 1. Keep their settings |
| Item | Preset Chip | `minescore:preset_chip` | Stacks to 1. Holds one MEU preset |

**Creative tab order** (2026-11-19): Blank Vinyl, Vinyl Record, Disc Cutting Lathe, Vinyl Player, Vinyl Display, Music Cabinet, MP3 Player, Audio Mixer, *Multi-Effects Unit, the six effect cards, Preset Chip* (once built), Channel Manager, Amplifier, Subwoofer, Middle Speaker, Tweeter Speaker, 12/16/20 ga wire, Cable Anchor, Wire Cutters, Writer's Desk, Blank Score, Score, MineScore Manual.

Every block faces the direction the player was looking when they placed it.

---

## 2. Items

### 2.1 Blank Vinyl
- **Lathe input slot:** cuts a song onto the Blank Vinyl.
- **Lathe MP3 slot:** the Blank Vinyl receives a copy of the Vinyl Record in the input slot.

### 2.2 Vinyl Record
- **Name:** defaults to the file name without its extension. Renaming it at the Lathe is free.
- **Tooltip:** title, length, who cut it, and **Original** or **Copy #n**.
  - *n* counts **every copy ever made from the original**, including copies of copies.
  - There is no "copy of a copy" label. Copied scores are numbered the same way.
- **Dye:** works like leather armor, so any mix of dyes gives a custom colour.
- **Trim:** applied at a smithing table.
  - Both the **pattern** (16 templates) and the **material** (every trim material) matter.
  - The **template is used up**.
  - A record can have **one trim** at a time.
  - The dye and trim **show on the record inside the Lathe and the Vinyl Player**.
- **Playing it:** in a **Vinyl Player** (§3.8). Vanilla jukeboxes don't take Vinyl Records.
- **Furnace erase:** needs fuel, takes the normal smelt time and gives XP. It works on copies too. The result is a Blank Vinyl.
- **Copying:** at the Lathe. It takes the same time as cutting, and copies can be copied.

### 2.3 MP3 Player
- **Loading songs:** at the Lathe, with the Vinyl Record in the input slot and the player in the MP3 slot. The record stays where it is.
- **Library:** unlimited songs, with playlists and every normal MP3 player feature.
- **Removing songs:** done in the player's GUI. Removed songs are deleted.
- **Handheld use:** only the holder hears it.
  - It plays from anywhere in the inventory and stops when it leaves the inventory.
  - It keeps its playback position, even when passed between players.
- **EQ:** its own 10-band EQ for handheld listening.
  - When the player is docked, the EQ tab shifts **every** Amplifier in the system **by the same amount**, so their individual differences are kept.
- **Volume:** the player's volume and the Mixer's volume are one shared slider.
- **Bluetooth mode:**
  - **Start and stop:** right-click a speaker with the MP3 Player. Right-click again, or break the speaker, to stop.
  - **Who hears it:** **everyone** nearby, through the speaker only. The holder doesn't also hear it in their "headphones".
  - **Sound:** the speaker plays only its own band, at 60% volume, heard up to 60% of the hearing range (about 19 blocks at the default 32).
  - **Range:** it stops if the holder goes more than 16 blocks away. If the MP3 Player is in an **item frame within 8 blocks**, it keeps playing through the queue.
  - **Chains:** daisy chains of one speaker type only, losing 15% per speaker.

### 2.4 Speaker Wire and Wire Cutters
- **Cables:** hang between their ends with sag. Wire isn't used up by length, and there's no maximum length.
- **Distance warning:** a chat message appears if the straight line from the previous connection to the player's head is longer than the hearing range. The connection still works.
- **Through blocks:** cables cross chunk borders and pass through blocks.
- **Invisible-wire option:** in config. Wires then show only while you hold wire or Wire Cutters.
- **Wire Cutters:** remove a connection and give the wire back.

### 2.5 Blank Score and Score
- **Editing:** only at the Writer's Desk. Anywhere else, scores are read-only and can't be played back.
- **Name:** *Untitled Score* in italics until named. After that, the custom title in the normal font.
- **Copying:** done at the desk. Copies are numbered **Copy #n**, and scores can't be locked.
- **Where the notes live** (M9, 2026-11-19): on the server, like songs (`<world>/minescore/scores/`). The item carries only the score's ID and title, so big scores never bloat items or the network. A copy gets its own ID, so editing it never changes the original.
- **A Blank Score** becomes a new *Untitled Score* as soon as it's put on a desk (built that way in stage 1, so a score exists before the editor does).
- **Size limit:** 16 parts and 2,000 measures.

---

## 3. Blocks

### 3.1 Disc Cutting Lathe (1 × 0.5 × 1)
| Input slot | MP3 slot | Result |
|---|---|---|
| Blank Vinyl | — | Pick a file from `blkcdsongs`, then **Cut**. Produces a Vinyl Record (Original) |
| Score | Blank Vinyl | Renders the score to audio and cuts it onto the Blank Vinyl. The Score stays. Produces a Vinyl Record (built in M10) |
| Vinyl Record | MP3 Player | Adds the song to the player. The record stays in place |
| Vinyl Record | Blank Vinyl | Makes **Copy #n** |

- **Song folder:** `.minecraft/blkcdsongs/` in each player's own instance.
  - Accepts `.mp3`, `.ogg`, `.wav`, `.flac`, `.mid`/`.midi`, `.nbs` and MineScore scores.
  - No size or length limit.
  - The file list has a Preview button, a search box, and sort buttons for **Name, Date modified, Type and Size** (click the active one again to reverse it). The row shows the sorted detail, and hovering a song shows all of them (added 2026-11-19).
  - Pressing **Cut** (or Copy) uses up the Blank Vinyl straight away; it comes back only if the cut is abandoned.
- **Power:** **20 FE/t** or **Create** rotation. Without power, the Lathe can still **preview**, but can't cut, copy or load songs.
- **Cut and copy time with FE:**

| Amplifier band wired to the Lathe | Cut time |
|---|---|
| none | 10 s |
| 20 ga | 5 s |
| 16 ga | 2 s |
| 12 ga | instant |

- **Wiring an Amplifier:** the Lathe has a 12, 16 and 20 ga input jack, and connects to the **matching Amplifier band output** (jacks only join matching colours). Which band is used sets the speed in the table above, in place of the wire gauge.
- **Empty or loaded:** with no record inside, the Lathe shows an empty, recessed platter; a record sits visibly on the platter once loaded.
  - The Amplifier and Lathe can work **without being part of a speaker system**, but both still need power.
- **Cut and copy time with Create:**
  - **Time** = **3 × 64 ÷ RPM seconds**. 64 RPM takes 3 s; 1 RPM takes 192 s.
  - **Stress:** 1 SU per RPM, so 64 RPM puts 64 SU on the network.
  - **Speed limits:** no minimum RPM, and a maximum of **64 RPM**. Above 64 RPM, the **Blank Vinyl breaks and is deleted**.
  - How this combines with an Amplifier is S3.
  - **How it's built** (M7, 2026-11-19): with Create installed (6.0 or newer; optional, never bundled), the Lathe is a full Create kinetic block. It takes rotation from a **shaft or cog underneath it**, turning its platter on the vertical axis, and puts real stress on the network. Rotation needs no FE. Each tick the faster of rotation and FE does the work, so speed changes mid-cut take effect at once. Over the maximum the record being cut or copied onto **shatters**, with record shards and the shatter sound. An overstressed network counts as not turning.
  - No extra messages for rotation (removed 2026-11-19): the screen shows the cut's progress as usual, and Engineer's Goggles show Create's own speed and stress lines.
  - Config (`minescore-common.toml`, `[create]`): `maxRpm` (64) and `stressPerRpm` (1).

### 3.2 Speakers
| Speaker | Size | Band | Wire | Power |
|---|---|---|---|---|
| Tweeter Speaker | 0.5 × 0.5 × 0.5 | 2–20 kHz | 20 ga | 5 + 5×(n−1) FE/t |
| Middle Speaker | 1 × 1 × 1 | 200 Hz–2 kHz | 16 ga | 10 + 5×(n−1) FE/t |
| Subwoofer | 1 × 2 × 1 | 20–200 Hz | 12 ga | 25 + 5×(n−1) FE/t |

*n* is the speaker's position in its chain.

- **Placement:** a speaker must be supported from **below or above**, never from the side.
- **Stacking flush** (2026-11-20): a Middle Speaker or Tweeter standing on a Middle Speaker, or a Tweeter on a Tweeter, sits flush on it with no gap. It sinks into its own block space by 4 pixels a step, and its jacks, sound and cone move with it. A column keeps stacking flush until the sinking adds up to a whole block (five Middle Speakers or three Tweeters); then the next starts fresh with a gap. Subwoofers are full blocks, so they stack flush already. It can still sit next to a wall. It faces the way the player was facing when they placed it.
- **Hearing range:** **32 blocks** by default, for a more realistic sound (changed from 128 on 2026-11-19; configurable as "Speaker range", in the common config now and the config menu from M11), with volume fading over distance. Boosted past 100%, a speaker carries √gain times further. Distance is measured from the centre of the front face to the player's head.
  - It's loudest in front and **30%** directly behind, scaling smoothly by angle.
- **Channels:** a speaker plays its assigned channel, or mono if none is assigned.
- **Crossover frequencies:** configurable.
- **Speaker GUI:** opened by **right-clicking with an empty hand**. It lets you rename the speaker and shows its channel, power and state. Speakers can also be renamed in the Channel Manager.
- **Jacks:** one input jack and one output jack, for daisy chaining.

### 3.3 Amplifier (1 × 1 × 1)
- **Controls:** a master volume plus a 10-band EQ (31, 62, 125, 250, 500, 1k, 2k, 4k, 8k and 16k Hz).
- **Boost cap:** set by the wires **linking the hubs** on the way to it: the orange Mixer → Amplifier link, or the purple Mixer → Channel Manager link and the teal Channel Manager → Amplifier link. The **thinnest** gauge decides (changed 2026-11-19; band wires no longer count). An Amplifier with no link has no boost (100%):

| Thinnest hub link | Max boost |
|---|---|
| 12 ga | 400% |
| 16 ga | 300% |
| 20 ga | 200% |

- **First Amplifier:** its cap is set by the hub links from the **source** side. Line Amplifiers can't push any speaker past that cap, so stacking amps can't give unlimited volume (S4).
- **Jacks:** inputs for 12/16/20 ga, Audio Mixer and Channel Manager. Outputs for 12/16/20 ga.
- **Main and Line Amplifiers:**
  - At most **one** main Amplifier sits between the source and the first speakers.
  - Any number of **Line Amplifiers** can sit on speaker lines. Each serves one speaker type.
  - A Line Amplifier contains faults: a short after it doesn't affect anything before it. If its input line goes out, everything after it goes out too.
- **Power:** 50 FE/t, drawn from the system. A standalone Amplifier (for example, one wired only to a Lathe) takes its own FE (S6).
- **Redstone control toggle:** in its GUI (§6).

### 3.4 Channel Manager (1 × 1 × 1)
- **Channels:** assigns each speaker to Left, Right or Mono.
- **Speaker list:** shows each speaker's name, type and range, with a Locate button.
- **Trims:** volume trims for speakers wired directly to the last hub. A trim also scales that speaker's chain or branch.
- **Jacks:** inputs for 12/16/20 ga and Audio Mixer. Outputs for 12/16/20 ga and Amplifier.
- **Placement rule:** never placed after an Amplifier.
- **Power:** 50 FE/t.
- **Redstone control toggle:** yes.

### 3.5 Audio Mixer (3 × 1.5 × 1)
- **Role:** the system's source. It holds an MP3 Player.
- **GUI:** the MP3 Player GUI, plus a Mixer tab.
- **Jacks:** 5 outputs: 12/16/20 ga, Channel Manager and Amplifier.
- **Volume:** above 100% needs an Amplifier. Channel controls need a Channel Manager.
- **Power:** 50 FE/t. The system's **power input** is here (§5.3).
- **Redstone control toggle:** yes.
- **Chunk loading:** the whole system stays loaded. Configurable.

### 3.6 Writer's Desk (2 × 1 × 1)
- **Building it:** the desk is made of **two Writer's Desk blocks placed side by side**. A single desk block shows as an unfinished half; the full desk model only appears once two desk blocks are next to each other (added 2026-11-18).
  - Two desk blocks facing the same way, side by side, join. Either half opens the same desk. The halves are **mirror images** (made symmetrical 2026-11-19): each is a drawer pedestal at its outer end under the top, with knee space between them, a back panel across, and one leather-inlaid top with the score sheet in the middle. The **left half** (as you face it) holds the slots, and its back panel the jacks. Breaking either half leaves the other as a lone, unfinished half (M9 decision).
- **Slots:** a **score slot** and a **copy slot**. Put a Blank Score in the copy slot and a copy comes out instantly.
- **Screen:** the two slots, an **Open Editor** button, and Play / Stop / Loop for when it's a source.
- **Editor:** opens the editor (§8). Several players can edit the same score together.
- **As a source:** can replace the Mixer as a system's source.
  - It has the Mixer's 5 outputs and plays the score on the desk.
  - Its volume is set at the Amplifier.
  - It takes the system's power input (S5).
  - Play / Stop / Loop on the desk screen; volume is set at the Amplifier. With no speakers wired it plays from itself, like an unwired Vinyl Player.
  - Playback uses the score as it was when Play was pressed; edits count from the next Play.
  - **Built 2026-11-19 (M9 stage 5):** Play renders the score on the pressing player's client, which uploads it like a cut song; the desk then plays it through its system (MEU, Channel Manager, Amplifiers as for the Mixer) or, unwired, from itself at the hearing range. Stop stops it; Loop starts it again from the top when it ends. The desk screen shows "Playing" meanwhile. A short stops it, and it restarts the usual way (§5.5). Breaking the desk stops it.

### 3.7 Cable Anchor
- **Placement:** anywhere. It has an **input end** and an **output end**.
- **Use:** while laying a cable, right-click the anchor's input end. The cable mounts there and continues from the output end, keeping its original start.
- **Limit:** unlimited anchors per cable.
- **Breaking:** the cable reconnects straight through, from whatever fed the anchor to whatever it fed.

### 3.8 Vinyl Player (1 × 0.5 × 1, built in M4; replaces the vanilla jukebox in every plan, 2026-11-18)
- **What it is:** a record player for Vinyl Records. MineScore no longer touches the vanilla jukebox.
- **Look:** almost exactly the Disc Cutting Lathe (half-height deck, recessed platter, arm), but in the **walnut brown** of the speakers. Its iron parts are swapped for wood and its arm is a tonearm instead of a cutting head.
- **The record:** shown on the platter whenever a record is in it, in its dye colour and trim, and it **spins while playing**. An empty Vinyl Player shows its bare, recessed platter, like the Lathe.
- **Static** (2026-11-20): records played through a Vinyl Player have a faint hiss, a low rumble and the odd crackle, the same on every speaker, as if on the record. It sits about 50 dB under full scale, so you hear it in quiet passages and lose it under the music. Config (client, Audio engine): **Vinyl static** on/off (on by default) and **Vinyl static amount** (100%, 0–400%).
- **Using it:** right-click with a Vinyl Record to put it on (it starts playing); right-click with an empty hand to take it off. "Now Playing: <title>" shows to players in earshot whenever a record starts.
- **On its own** (no speakers wired) *(proposed)*: it plays the full range from itself, the same all round, heard as far as a speaker (the hearing range, 32 blocks by default).
- **As a source** (F13): wired to speakers, it **replaces the Mixer** and the sound comes from the speakers instead.
  - It has **no volume control**.
  - It **can't** connect to a Channel Manager or Amplifier.
  - **Jacks:** 12, 16 and 20 ga band outputs on its back, speaker-band side, following the jack conventions (it has a real front now, so one back panel replaces the four side panels planned for the jukebox).
  - **Channels:** assigned automatically from its orientation. For someone facing its front, speakers on their left play Left and on their right play Right (like a stereo facing you); speakers straight ahead or behind play Mono.
- **Power:** the Vinyl Player itself needs **none** and has no minimum. Only its speakers draw power, through its power input.
- **Redstone, hoppers:** *(proposed)* a comparator reads 15 while it plays; hoppers can't load it (as before).

### 3.9 Vinyl Display (added 2026-11-19)
- **What it is:** an item frame for **Vinyl Records only**.
- **Look:** a half-record plaque in the record's style: grooves, a bright rim and half a red label. The record stands in a slot along its top, so its lower half sinks into the plaque and its **top half sticks out**, in its own dye and trim.
- **Placing it:** on a wall it hangs flat; on top of a block it stands on a small foot, facing the player. Not on ceilings. It drops if its support goes.
- **Using it:** right-click with a Vinyl Record to put it up; right-click with an empty hand to take it down. The record's title floats above it while you look at it.
- **Comparator:** 15 while it shows a record. Hoppers can't fill it.
- **Colour** (2026-11-19): the display's half label takes the **dye colour of the record** in it, and is red when it's empty.

### 3.11 Music Cabinet (1 × 1 × 1, added 2026-11-19)
A walnut cabinet with an open front for keeping music. Decisions of 2026-11-19.

- **What it holds:** **16 Scores** on the top shelf and **8 Vinyl Records** on the bottom one. Only written Scores and cut records go in: **nothing blank** (no Blank Scores, Blank Vinyls or empty Scores).
- **Look:** walnut, in the speakers' wood, with an open front.
  - Empty, it's **hollow**: just the shell and the middle shelf, dark inside.
  - It shows **how full it is**. Each shelf fills from the left: a cream paper spine per Score, and a **sleeve per record**.
  - Each sleeve is in its **record's dye colour**, and the record and its label show through a hole in the sleeve's side.
- **Putting things in:** right-click the front with a Score or a Vinyl Record. It goes into the first free place from the left (as you face the front) on its own shelf.
- **Taking things out:** **Shift+right-click** with an empty hand on the Score or sleeve you're pointing at, and it goes into your inventory (or drops if that's full). The rest **close up to the left**, so a shelf always fills from the left with no gaps.
  - The one you're pointing at is outlined, and its title floats above the cabinet.
  - A plain right-click with an empty hand does nothing. There's no screen.
- **Comparator:** how full it is, 0–15 across all 24 places. **Hoppers** can't fill or empty it.
- **Breaking it** drops everything inside. Mined fastest with an axe.
- **Recipe:** planks, wooden slabs and a wooden pressure plate (§3.12).

### 3.10 Multi-Effects Unit, MEU (1 × 1 × 1, added 2026-11-19; built in M6)
A hub that runs the whole system's sound through up to six effect cards, like a guitarist's multi-effects unit. Decisions Q1–Q30 of 2026-11-19; where Q1–Q18 were left open, these are the defaults.

- **Where it goes:** always straight after the source, before any Channel Manager or Amplifier (§4.1). One per system. Never after a Channel Manager, Amplifier or speaker, and there's no "line" MEU on speaker lines.
- **Sources:** the Audio Mixer and the Writer's Desk. The Vinyl Player stays speakers-only. Bluetooth and handheld MP3 listening skip it.
- **Look:** a rack unit with six card slots across its front and a small display, pickaxe. *(proposed)*
- **Jacks (back):** hub I/O on the left half, band I/O on the right, as usual.
  - Hub links: an **olive green** input from the source, and outputs to a Channel Manager, an Amplifier and a Lathe. Each still only joins its own match.
  - Its outputs match what they feed (2026-11-19): the MEU takes the Mixer's place in front of a Channel Manager or Amplifier, so its outputs to them are the Mixer's **purple** and **orange**. Its output to a Lathe is olive.
  - The Mixer and Desk get an olive output to the MEU. The Channel Manager gets a purple input from it, the Amplifier an orange one and the Lathe an olive one.
  - 12/16/20 ga band inputs and outputs, passed through like a Channel Manager: a speaker type plays only if its band runs through the MEU too.
  - Its links count toward the Amplifier's boost cap: the thinnest link on the way wins (§3.3).
- **Cards:** six effect cards, one of each at most, in six slots (like upgrades in a machine).
  - Each card is a category: pick **one effect** inside it and set its knobs. A card keeps its settings when it's moved to another MEU.
  - **Order:** the slot order is the processing order; move cards to rearrange the chain.
  - Each slot has a **bypass**, each card a **mix** (dry/wet) knob, and the unit a **master bypass**.
  - **A fresh card changes nothing** (2026-11-19): every mix knob starts fully dry, and effects without one start where they do nothing (Speed 100%, gate fully open, compressor 1:1 with no makeup, Tuner not retuning, Expression sweeping nothing). Turn it up to hear it.
  - Effects work on the **full stereo mix**, before the crossover splits it into bands. Mono speakers get the mono sum of stereo effects.
  - **Blowouts** (§5.5) count the MEU's output: Drive and compression push speakers harder.
- **The effects** *(knobs proposed)*:

| Card | Effects | Knobs |
|---|---|---|
| Drive & Distortion | Overdrive, Fuzz, High-gain | drive, tone, level, mix |
| Modulation | Chorus, Flanger, Phaser, Rotary speaker, Pitch shifter, **Speed** | rate, depth, mix; semitones (Pitch); speed % (Speed) |
| Delay | Straight, Stereo, Ping-pong | time (ms, or note lengths synced to the song's detected tempo), feedback, mix |
| Reverb | Room, Spring, Plate, Hall | size, decay, damping, pre-delay, mix |
| Amp & Cabinet Modeling | made-up amp models (Clean Tube, British Crunch, Boutique Lead, Modern High-gain) and cabinets (1×12 Open Back, 2×12, 4×12 Closed Back), with built-in impulse responses made for the mod | gain, bass, mid, treble, cabinet, level, mix |
| Dynamics & Utility | Wah, Noise gate, Compressor, Tuner, Expression sweep | per effect; the **expression pedal** is a slider in the GUI that sweeps any chosen knob |

- **Tuner** (Q19, decided 2026-11-19: our own code, no outside library): it works out the song's key, its tuning (what it tunes A to) and its tempo, shows them on the MEU's screen, and retunes the song by a key shift and, when Retune is on, a new reference pitch for A (415–466 Hz), using the Pitch shifter's engine. The same analysis gives synced delays their tempo.
- **Speed:** 25%–200%, for the whole system and kept in sync: everyone's playback position and when the next song starts follow it.
  - **Config "Speed algorithm":** **WSOLA** time-stretch (default: keeps the pitch, clean on most music, cheap), **Phase vocoder** (keeps the pitch, smoother on long notes, heavier) or **Tape** (varispeed: the pitch follows the speed, like a record).
- **Presets:** the MEU has **built-in presets** (for example Stadium, Lo-fi Radio, Cathedral, Slowed + Reverb).
  - Changing a built-in preset never alters it: it becomes a new, unsaved preset.
  - Presets are kept on **Preset Chips**: put a chip in the MEU's chip slot to write the current settings onto it, or load the chip's preset. A chip holds the cards' effects, order, knobs and bypasses; loading flags any card the preset needs that isn't in the unit.
- **Mixer tab:** shows the MEU **read-only**: the preset's name, the cards in order and which are bypassed. Effects are changed only at the MEU.
- **Power (M7):** 50 FE/t plus 10 FE/t for each card in use.
- **Redstone (M8):** a signal **disables** the unit (as if on master bypass), with the Redstone Control toggle like other hubs. A comparator reads the number of **active cards** (0–6).
- **Printing effects onto a record:** a Lathe linked to the MEU (olive jack) cuts songs **with the effects in them**, rendered on the cutting player's computer and shared as a new song.
  - While linked to the MEU, the Lathe **can't use an Amplifier** at the same time.
  - Each effect changed from its defaults adds **2 s** to the cut time.
- **Performance:** effects run once per system on each player's computer, not once per speaker, with a **Low/High quality** setting in the config.
- **Art:** the MEU block (cards visible in their slots, a lit display), six card items, the Preset Chip, and the MEU GUI (a pedalboard of six card panels with knobs, the chip slot, bypass switches and the expression pedal).

---

## 3.12 Recipes (2026-11-20)
Made in `tools/recipe_maker.py` and exported to `tools/recipes_export.json`. `gen_resources.py` turns that into `data/minescore/recipes/` and a recipe-book unlock for each (once the player has any ingredient). A recipe that's only another one flipped left to right is left out: Minecraft accepts every shaped recipe mirrored anyway. No clashes with vanilla or each other.

- **No crafting recipe:** Vinyl Record (cut at the Lathe), Score (from a Blank Score at the Desk), Creative Power Source (dev only).
- **Speakers:** Subwoofer = planks round leather. Middle Speaker = wooden slabs in the corners, planks on the sides, leather in the middle. Tweeter = wooden buttons in the corners, wooden slabs on the sides, leather in the middle.
- **Hubs:** each is two Tweeters over two Middle Speakers and a Preset Chip, over three Subwoofers, with its own top-middle piece: TNT for the Amplifier, a Vinyl Display for the Audio Mixer, a sticky piston for the Channel Manager. The Multi-Effects Unit: iron nuggets and bars, a slime block, a Preset Chip, a clock and iron blocks.
- **Preset Chip** (×2): copper, gold, redstone and quartz. **Effect cards:** a Preset Chip between two of: TNT (Amp & Cabinet), repeaters (Delay), pistons (Modulation), slime blocks (Reverb); wool and stone either side (Drive); a goat horn and any tool, used up, since it's the utility card (Dynamics).
- **Records and players:** Blank Vinyl = four smooth quartz slabs in a diamond, plus the furnace eraser. Disc Cutting Lathe = iron ingots, an iron hoe and iron blocks. Vinyl Player = a wooden button and iron bars over wooden slabs and a copper ingot. Vinyl Display = a carpet over three quartz. MP3 Player = a glass pane over stone buttons and a Preset Chip.
- **Wiring:** Speaker Wire ×4 from three on a diagonal: cut copper (12 ga) or cut copper slabs (16 ga), unoxidized, waxed or not; copper ingots (20 ga). Cable Anchor ×4 = an iron ingot between two nuggets, in a row or on a diagonal. Wire Cutters = iron ingots and sticks.
- **Writing:** Blank Score = leather, paper, a feather and an ink sac. Writer's Desk = planks, a wooden slab and a pressure plate in an L. Manual = a book (or a book and quill) with a Blank Score, or with another Manual to copy it.
- **Music Cabinet:** planks, wooden slabs and a wooden pressure plate.

## 4. Wiring and topology

### 4.1 Allowed chains
```
Source ──► Speakers
Source ──► Channel Manager ──► Speakers
Source ──► Amplifier ─────────► Speakers
Source ──► Channel Manager ──► Amplifier ──► Speakers
Source ──► MEU ──► Speakers
Source ──► MEU ──► Channel Manager ──► Speakers
Source ──► MEU ──► Amplifier ──► Speakers
Source ──► MEU ──► Channel Manager ──► Amplifier ──► Speakers
                  Speakers ──► Speaker ──► … (daisy chain, may branch)
                  Speakers ──► Line Amplifier ──► Speakers (one type)
Source = Audio Mixer · Writer's Desk · Vinyl Player (Vinyl Player: speakers only)
MEU ──► Lathe (prints the effects onto records; not with an Amplifier at the same time)
Never: Amplifier ──► Channel Manager · anything ──► MEU except a Mixer or Desk
```

### 4.2 Connecting
- **Jacks:** players click **specific jacks** on the models.
- **Hub → speaker, and speaker → speaker:** **right-click** with the **matching gauge**.
- **Hub → hub:** **shift-right-click** from jack to jack, with **any gauge**.
  - Exception: the gauge of the **link wires** (purple, orange, teal) leading to an Amplifier sets its boost cap.
- **Band paths:** a speaker type plays only if its band jacks are linked at every hub back to the source.
- **Feeding hubs vs. speakers:** a band output that feeds another hub can't also feed speakers. Speaker outputs can fan out without limit.
- **Removing:** Wire Cutters remove a connection and give the wire back.

### 4.3 Full system
```
            shift (any ga)        shift (any ga)
  MIXER ─────link────► CH.MGR ─────link────► AMP
    12 ─(shift)──────►  12 ──(shift)──────►  12 ──(12 ga)──► SUB ──► SUB …
    16 ─(shift)──────►  16 ──(shift)──────►  16 ──(16 ga)──► MID ──► MID …
    20 ─(shift)──────►  20 ──(shift)──────►  20 ──(20 ga)──► TWT ──► TWT …
              links on the way to the AMP: thinnest gauge sets its cap
```

---

## 5. Volume, power, EQ and failures

### 5.1 Gain at a speaker
gain = Mixer/MP3 volume × Amplifier master × Amplifier EQ × Channel Manager trim × power/chain factor × Line Amplifier settings

- Gain is capped at the first Amplifier's limit.
- Above 100%, a speaker is louder **and** can be heard from farther away.

### 5.2 Power modes
| Mode | Behaviour |
|---|---|
| **Energy** (default) | FE costs, power budget, minimum and cap all apply. Daisy-chain loss **only starts once the budget runs out** |
| **None** | No power anywhere, no minimum, no limits. **No daisy-chain loss**, so speaker count is unlimited |

### 5.3 Energy mode
- **Power input:** only at the **source**: Mixer, Desk or Vinyl Player.
- **Demand:**
  - Every hub (Mixer, Channel Manager, Amplifier, Line Amplifier): 50 FE/t. The MEU: 50 FE/t plus 10 FE/t per card in use.
  - Speakers: base cost + 5×(n−1), by their position in the chain.
  - Lathe: 20 FE/t, powered on its own.
- **Minimum:** the system needs at least **the hubs' total cost** to run. For example, Mixer + Channel Manager + Amplifier needs 150 FE/t. A Vinyl Player source has no minimum.
- **Cap:** a system draws at most **500 FE/t** by default. Configurable.
- **Volume:** stays **full** while supply covers demand, so small and mid-sized systems aren't penalised.
  - Speakers beyond the budget follow the daisy-chain curve (S1).
- **Example:** Mixer + Amplifier (100), 2 Subwoofers (25 + 30), 4 Middle Speakers (10 + 15 + 20 + 25) and 4 Tweeters (5 + 10 + 15 + 20) = **275 FE/t**. That's under the 500 cap, so everything plays at full volume.
- **How it works in game** (built in M7, 2026-11-19):
  - **Taking FE:** the source, the Lathe and a standalone Amplifier take **Forge Energy on any side** from cables, pipes or generators. MineScore only takes FE; it never gives any out. They store it: a source holds 10,000 FE, a Lathe or Amplifier 4,000.
  - **Budget:** while a system plays, its source draws the system's demand (up to the cap) every tick. The budget is what it actually managed to draw over the last second, so a weak generator gives a smaller budget. An idle system draws nothing.
  - **Below the minimum** the system is silent, and the Mixer tab says how much its hubs need. The Mixer tab shows the budget against the demand, and each speaker's screen shows its cost and whether it's at full volume or past the budget.
  - **Resetting after a short (Energy mode):** the source must go a whole second with **no FE coming in**, then Play starts it again.
  - **Lathe:** without power it won't start a cut, copy or load, and a cut in progress **waits** until power comes back. Its screen has an FE gauge. An Amplifier speeding it up must be powered too: a standalone one spends its own 50 FE/t while the Lathe works; one inside a system needs its source to have power.
  - **Config** (`minescore-common.toml`, `[power]`): `mode` (ENERGY/NONE), `systemCap`, `hubCost`, `meuCardCost`, `tweeterCost`, `middleCost`, `subwooferCost`, `chainStep`, `latheCost`. The curve's 5% and 10% join the config menu in M11.

### 5.4 Daisy-chain curve (Energy mode, past the budget; single-speaker mode)
- **Loss:** each speaker plays at 95% of the previous one, down to a 10% floor.
- **Past the floor:** adding speakers lowers the first speaker by 5 points, with the speakers in between evenly spaced.
- **All at 10%:** each speaker plays at 10% × N₀ ÷ n, where N₀ is the number of speakers when they all first reached 10%.
- **Position:** counted across every branch.
- **Reset:** a Line Amplifier resets the curve.
- **Single-speaker mode:** loses 15% per speaker.

### 5.5 Blowouts and shorts
- **Trigger:** the gain setting **and** the actual loudness of the music.
- **Order:** bass blows first, then mid, then tweeter. Proposed thresholds: Sub +9 dB, Mid +10 dB, Tweeter +11 dB.
- **Distortion:** off by default, with a toggle and thresholds in config.
- **When a speaker shorts:**
  - It **pops off as an item**.
  - Everything after it loses its link to the hub, but those speakers stay connected to each other.
- **Fixing it:**
  - **Energy mode:** turn the **source's power** off and back on.
  - **None mode:** wait 5 s, then press **Play** on the Mixer.
  - How the line's gap gets closed is S2.

---

## 6. Redstone
- **Toggle:** each hub's GUI has a **Redstone Control** toggle. It controls **redstone inputs only**; comparator outputs always work. **Off by default** on newly placed hubs, configurable.
- **Sides:** left and right are the **block's own** left and right.
- **Power types:** "soft" means weak power and "hard" means strong power.
- **Speakers required:** redstone only does anything on hubs connected to speakers.
- **How it's built** (M8, 2026-11-19):
  - The toggle sits in each hub's screen: the Mixer tab, the Channel Manager and Amplifier screens (top right) and the MEU screen (under the pedal). Its tooltip explains what that hub's redstone does. Config: `redstoneControlByDefault` in `minescore-common.toml`.
  - **Left and right** are the block's own: as if you were the block, looking out of its front.
  - **Strong power** is what a repeater, comparator, torch, lever or button gives. Redstone dust only gives weak power.
  - **Comparators read each face separately**, so a Channel Manager's left and right, or an Amplifier's band groups, can give different signals. MineScore hooks the vanilla comparator for this; vanilla never says which face it reads.
  - Hubs read their neighbours whenever one changes, and again once a second, so they also catch up after the world loads.

### 6.1 Audio Mixer
| Input | Effect |
|---|---|
| Signal on the front, back, top or bottom | **Signal on: play** the queued music. Nothing plays if the queue is empty. **Signal off: pause** |
| Pulse on the left side | Next song (swapped with the right on 2026-11-19) |
| Pulse on the right side | Restart if more than 2 s has played. Otherwise go to the previous song, or restart if there isn't one. (The screen's Previous button now uses the same 2 s.) |
| Comparator | 15 while playing, 0 otherwise |

### 6.2 Channel Manager
| Input | Effect |
|---|---|
| Weak power on the left or right side | Turns that channel off while the signal lasts |
| Strong power | Forces mono, then restores the previous setting when the power stops |
| Comparator | 15 from the left side while Left plays, from the right side while Right plays, and from every side while Mono plays. "Mono plays" means the Channel Manager is forced to mono, by its switch or by strong power. All 0 while its system isn't playing |

### 6.3 Amplifier
| Side | Bands |
|---|---|
| Back | 1–3 |
| Left | 4–5 |
| Right | 6–7 |
| Front | 8–10 |

- **Comparator output:** proportional to the loudest band in that group, on the same scale as the inputs (15 = 400%, so a flat band reads 4). There's no output if no speaker type for that group is connected: back = Subwoofers, left and right = Middle Speakers, front = Tweeters. The top and bottom give none.
- **Side input:** signal 1–15 sets those bands' volume, where 15 = 400%. Signal 0 hands control back to the GUI value.
- **Bottom input:** signal 1–15 sets the **loudest band** to signal ÷ 15 × 400% and scales the other bands to keep their ratios. The ratios are remembered even when a band is held at its cap. Signal 0 returns to the GUI values.
- **Top input:** does nothing.

### 6.4 Multi-Effects Unit
| Input | Effect |
|---|---|
| Any signal | Disables the unit, as if on master bypass, while the signal lasts. Its display reads BYPASS |
| Comparator | The number of active (not bypassed) cards, 0–6 |

---

## 7. Audio engine and songs
- **Decoding:** each client decodes and caches songs. MP3 and FLAC need bundled decoder libraries.
  - MIDI, `.nbs` and scores are rendered to audio on the client of the player who cuts them.
- **Crossover:** splits the audio into 3 bands × 2 channels.
- **Speakers:** directional OpenAL sources.
  - Volume fades out over the hearing range (32 blocks by default).
  - They're loudest in front and 30% directly behind.
- **Sync:** the server keeps playback in sync. Players who join late start mid-song.
- **Song distribution:**
  - The source files stay in each player's `blkcdsongs/` folder.
  - When a player cuts a record, the song is uploaded and cached in `<server>/minescore/cache/`.
  - Listeners download it once and keep it in their instance's cache.

### 7.3 Sound through blocks (added 2026-11-18; built in M5)
Blocks between a speaker and a listener muffle it, more for harder blocks.

- **The path:** the straight line from the centre of the speaker's front face to the listener's head. The speaker's own block is handled by directivity (§3.2), so it doesn't count here.
- **Loss per block on the path,** in decibels:
  - **loss = (3 + 4 × hardness) × coverage**, capped at 24 dB per block.
  - **hardness** is the block's mining hardness. Examples: leaves 0.2 → 3.8 dB, glass 0.3 → 4.2 dB, dirt 0.5 → 5 dB, stone 1.5 → 9 dB, planks 2 → 11 dB, iron block 5 → 23 dB. Very hard and unbreakable blocks (obsidian, bedrock) hit the 24 dB cap.
  - **coverage** is how much of the block is solid, from its collision shape: 1 for full cubes, about 0.5 for slabs, and less for panes, fences and bars. Air, flowers, torches and other blocks you can walk through count as 0.
  - **Sound-dampening blocks** (wool and carpet, from Minecraft's `dampens_vibrations` tag) lose twice as much.
  - **Water and lava** lose 2 dB per block.
- **Total:** the losses add up along the path, capped at 48 dB in total (about 0.4% volume).
- **Bass gets through walls best.** The loss is scaled per band: Subwoofer ×0.5, Middle Speaker ×1, Tweeter ×1.4. Through a wall you mostly hear the bass, as in real life.
- **No sudden jumps:** each speaker's path is re-checked about 5 times a second, and its volume glides to the new value over a quarter of a second.
- **Config options:** on/off, the base and per-hardness numbers, the per-block and total caps, the band scaling, and how often paths are checked.

### 7.4 Room Sound Physics (added 2026-11-19; built in M5)
A speaker in a sealed space sounds fuller than one in the open; every gap lets sound escape, the way a car stereo loses its punch when a door opens.

- **Measuring the room:** 64 rays go out from the speaker's front, evenly in every direction, up to 16 blocks. A ray counts as closed once the blocks it passes take off 6 dB (one stone block, or a couple of glass blocks), using the same losses as §7.3. Open doorways, missing blocks and open trapdoors let rays out; spaces bigger than 16 blocks count as open air.
- **Sealing:** enclosure = the share of closed rays. Seal = 1 − 4 × (1 − enclosure), at least 0, so small gaps matter a lot: with a quarter of the rays escaping, the effect is gone.
- **The effect:** up to **+4 dB** in a fully sealed space, scaled per band: Subwoofer ×2 (+8 dB, bass builds up in closed rooms), Middle Speaker ×1, Tweeter ×0.6. Open air plays as before.
- **Who hears it:** listeners in the room with the speaker get all of it. It fades out as walls come between them (gone once the path loses 12 dB), so people outside hear the muffled sound of §7.3, not the room.
- **Smooth:** each speaker's room is re-measured every 2 seconds, and the change glides in over a quarter second.
- **Blowouts** (§5.5) don't count the room: a speaker blows from its settings and the music, wherever it stands.
- **Config:** a toggle named **Room Sound Physics** (on by default; in the client config now, and in the config menu's Audio engine category from M11), plus the ray count and range, the 6 dB closed-ray loss, the leak factor, the +4 dB maximum, the band scaling and how often rooms are checked.

## 8. Score editor (Writer's Desk only)
A notation editor modelled on **MuseScore**'s features, drawn in MineScore's own pixel style. All its symbols are original pixel art in one texture atlas, so a resource pack can restyle the whole editor. This replaces the Bravura font (decided 2026-11-19, to keep "all art from scratch").

### 8.1 Decisions (M9 question round, 2026-11-19: all proposed defaults taken, plus "as many of MuseScore's features as possible")
- **Layout:** staves scroll sideways, one row per part, with zoom (MuseScore's "continuous view"). A page view like printed sheet music comes later.
- **Parts:** up to 16, each with one instrument and its own clef. Chords allowed.
- **Entering notes:** mouse and keyboard, both as in MuseScore.
  - Mouse: pick a duration in the palette, click the staff, drag to change pitch.
  - Keyboard (MuseScore's shortcuts, all changeable in the editor's **Preferences**, ⚙ or Ctrl+comma, saved to `config/minescore-editor-keys.json`): `N` note input; `A`–`G` notes (Shift adds to the chord); `1`–`8` 64th up to breve; `0` rest; `.` dot; `+` tie; `-` / `=` / `\` flat, sharp, natural; `↑`/`↓` a semitone, `Ctrl+↑/↓` an octave, `Alt+↑/↓` a line or space in the key; `Alt+2`–`8` add an interval above; `R` repeat the selection; `Ctrl+Z` / `Ctrl+Y`; `Ctrl+C` / `X` / `V`; `Delete`; `Space` play; `Ctrl+B` add a measure; `Insert` insert one; `Ctrl+Alt+1`–`4` voices; `Ctrl+=` / `Ctrl+-` zoom.
- **Hearing notes** (2026-11-19): placing, selecting, moving or re-pitching a note plays it (a chord plays whole), in its part's instrument, heard only by you.
- **Note input preview** (2026-11-19): in note input a see-through note of the chosen value follows the mouse, with its ledger lines and stem, showing where a click will put it. Shift+click adds the note to the chord there.
- **Selecting, as in MuseScore** (2026-11-19): click a note to select it (it plays); click empty staff for its measure; drag across measures, or Shift+drag a box, to select several; Shift+click extends the selection to what's clicked; Ctrl+click picks several separate notes; click a part's name for its whole staff; `Shift+←/→` extends by a note and `Ctrl+Shift+←/→` to the barline; `Ctrl+←/→` jumps a measure; `Alt+Page Up/Down` moves to the staff above or below; `Ctrl+Home/End` the start or end; `Ctrl+A` everything. A range can start and end inside measures; copy and paste keep every chord's place, and paste overwrites the time it covers.
- **Stage 3 additions** (2026-11-19):
  - `Backspace` deletes too. Choosing a shorter value on a rest splits it: the new rest, then the fewest clean rests for the rest of its time, each starting on a multiple of its length (a quarter on beat 1 of a whole rest leaves a quarter and a half).
  - A note too long for what's left of its measure (a dot added at the end of one, say) carries on over the barline, tied.
  - Arrow keys select the **top note** of each chord by default; Preferences can change this to the whole chord or the bottom note.
  - **Every mark can be selected on its own:** dynamics, articulations, ornaments, lyrics, chord symbols, staff text, tempo marks, lines, signs, rehearsal marks and endings. `Delete` removes the selected one. Clicking a dynamic opens a list of all of them, four at a time with ▲/▼ (or the wheel), to change it.
  - Playing with several notes selected across time plays just that stretch.
  - Undo, redo, paste and other edits scroll the view to where they happen if it's out of sight.
  - Dragging in open space (or with the middle button anywhere) moves the view around.
  - **Parts:** "+ Add part…" opens a list of every instrument (the three drums together as a Drum kit); the pencil beside a part's instrument opens the same list to change it. Parts are named after their instrument, numbered when it's already used ("Bass 2"); a renamed part keeps its own name.
  - **Palette search** (`Ctrl+F`, or the box at the top of the palettes): shows only the items whose names match, in every palette at once.
  - **Arpeggios** palette: arpeggio, arpeggio up and arpeggio down, drawn as a wavy line (with an arrowhead for up and down) and played rolled, each note about 45 ms after the one below (from the top for down), less on short chords. They can be selected and deleted on their own like other marks.
  - Palettes on the left and the property sections on the right (Score, Instruments, Measures) all fold up by clicking their headings.
  - **Instruments** (right sidebar) lists every part as a subtab; the open one shows its name, instrument (✎), volume, pan, mute, solo, re-strike, order and Remove. A new part appears at the bottom of the list with its subtab open. Clicking music follows that part again; the panel scrolls with the wheel.
- **Undo/redo:** the last 100 of your own edits.
- **Listening:** play from the cursor, heard only by you (like the Lathe's preview), with a cursor following along. Per-part volume, mute and solo in a mixer panel. Metronome and count-in optional.
- **Pitch range:** a note block covers F♯3–F♯5. Scores may go 2 octaves either side by stretching the samples; notes outside the natural range show orange.
- **Notes last their written length** (changed 2026-11-19; it was "re-struck every beat"):
  - **Held:** a pitched note longer than its sample holds its tone like an organ. Just after the attack, a stretch a whole number of the note's cycles long is looped, its fading level evened out and its join crossfaded, then it fades out over 60 ms when the note ends.
  - **Cut short:** a note shorter than its sample fades out at its end. Staccato sounds for half its length, staccatissimo a third; a tie holds one note through the tied notes.
  - **Drums** always play their whole hit.
  - **Re-striking every beat** (the old way) is still there: per part (the part's "Re-strike long notes" button), or for every part from Preferences and the client config (`restrikeLongNotes`, off by default).
  - **MIDI files** cut on the Lathe use their notes' lengths the same way; Note Block Studio files have none, so they stay hits.
- **Dynamics:** ppp–fff (plus sf, sfz, fp) set the volume, hairpins glide between them, an accent adds 30% to one note.
- **Drums:** a percussion staff, one line per drum sound (bass drum, snare, hi-hat), no pitch.
- **Collaboration:** everyone with the desk's editor open sees changes live. Each player's cursor shows in their own colour with their name; if two people change the same note, the last change wins.

### 8.2 Notation (MuseScore-style)
- **Notes and rests:** breve to 64th notes, single and double dots, ties, chords, **up to 4 voices per staff**, **tuplets** of any ratio (triplets, quintuplets…), **grace notes** (acciaccatura, appoggiatura).
- **Pitch:** proper spelling (sharps, flats, naturals, double sharps and flats); courtesy accidentals shown automatically; transpose a selection by semitone or octave.
- **Clefs:** treble, bass, alto, tenor, treble 8vb, percussion; clef changes mid-score.
- **Key and time:** all 15 key signatures and key changes; any time signature (including common and cut time) and changes; a pickup measure.
- **Tempo:** tempo marks and changes; ritardando and accelerando.
- **Dynamics:** ppp–fff, sf, sfz, fp; crescendo and diminuendo hairpins.
- **Articulations:** staccato, staccatissimo, tenuto, accent, marcato, fermata.
- **Ornaments:** trill, mordent, turn (all played back).
- **Lines:** slurs, 8va / 8vb / 15ma octave lines, glissando.
- **Structure:** repeat barlines, 1st/2nd endings, D.C., D.S., Segno, Coda, To Coda, Fine (all followed in playback); double and final barlines; insert, append and delete measures.
- **Text:** title, subtitle, composer, lyricist; staff text; rehearsal marks; **lyrics** (several verses); **chord symbols**.
- **Editing:** select notes or ranges; copy, cut, paste; palettes; a properties panel; navigate by note and by measure; zoom.

### 8.3 Instruments, files and font
- **Instruments:** the vanilla instruments, plus any added by resource packs (M10).
  - Each pack defines its instruments' pitch ranges.
  - Players choose which instruments are enabled (M10).
  - Missing instruments fall back to harp.
- **Import:** from the **editor at any time**, MIDI or `.nbs` (M10). Importing **overwrites** the score, so the editor first **offers to make a copy**. Files come from `blkcdsongs/`, `minescore/` or a file picker.
- **Export:** `.mid`, `.wav`, `.nbs` or MineScore files (M10). They go to `.minecraft/minescore/` by default, or a folder you pick.
- **Built 2026-11-19 (M10):**
  - **Pack instruments:** a resource pack lists its instruments in `assets/<namespace>/minescore/instruments.json`: `{"instruments": [{"id": "violin", "name": "Violin", "sound": "mypack:violin", "center": 69, "low": 55, "high": 100}]}`. `sound` is a sound event in the pack's `sounds.json`, `center` the MIDI note it plays at as recorded, `low`–`high` its range (notes outside show orange), and `"pitched": false` makes it a drum. They appear in the instrument list after the note block ones, play, hold and audition like them, and a part keeps its pack instrument by id; without that pack it plays as a harp. The list is read again whenever the editor opens.
  - **Choosing instruments:** the instrument list's "Choose instruments…" switches it to ticking which instruments it offers (saved in `config/minescore-editor.json`); a part's own instrument always shows.
  - **Import** (File → Import…): a list of the `.mid`, `.midi`, `.nbs` and `.minescore` files in `blkcdsongs/` and `minescore/`, newest first, or Browse… for the system's file dialog. Then: "Copy, then import" (keeps the score as it was as a numbered copy, on a Blank Score from your inventory), "Just import", or Cancel. Everyone editing the score gets the imported one.
    - **MIDI:** notes on the nearest 16th, keeping their lengths; a part per channel and instrument (up to 16), all drums in one percussion part (bass drum, snare, hi-hat); chords in one voice, each lasting until the next starts; the first tempo, time and key signature, and later tempo changes as tempo marks; low parts get a bass clef; flat keys are spelt with flats.
    - **Note Block Studio:** a tick is a 16th; each note lasts until its instrument's next note, at most a quarter; a part per instrument, drums together; its title, author, tempo and time signature.
    - **MineScore files** come back exactly as saved.
    - Notes too long for their measure are tied over the barline; a note one written value covers (a half on beat 2) stays one note.
  - **Export** (File → MIDI, WAV, NBS, MineScore): written from what playback plays (repeats, ties, ornaments, dynamics as heard), into the export folder (`minescore/` until "Export folder…" picks another), named after the title and never overwriting an earlier export. MIDI: a channel per instrument with its nearest General MIDI sound, drums on channel 10, the score's tempo, time and key. NBS: version 5, a tick per 16th at the score's tempo. WAV: 16-bit stereo at 44.1 kHz. MineScore: the whole score.
  - **Fonts:** the notation symbols' layout now loads from the resources too (`assets/minescore/score_glyphs.json`), so a resource pack can redraw the atlas *and* lay it out its own way.

### 8.4 Building it (M9 stages)
1. The two-block desk, Blank Score and Score, scores stored on the server, and the score format with every feature above, played back to note-block audio. **Built 2026-11-19.**
2. The editor: drawing the score, note input, selection, clipboard, undo, palettes, playback. **Built 2026-11-19:**
   - Opened from the desk's **Open Editor** button; the score is sent in compressed pieces and every edit goes back to the server as "this measure now reads…", so saving, undo and other editors all follow one path. Scores save a few seconds after a change and when the last editor closes.
   - **Toolbar:** note input (N), note values 64th–breve, dot and double dot, rest, tie, flat/natural/sharp, voices 1–4, undo/redo, play/stop, zoom. **Palettes** (left): clefs, key and time signatures, dynamics, articulations, ornaments, tempo marks, repeats, 1st/2nd endings, jumps, barlines. **Properties** (right): title, subtitle, composer, lyricist, tempo; the part's name, instrument, volume, pan, mute and solo; add, remove and reorder parts; insert, append and delete measures.
   - **Mouse:** click a note or rest to select it, empty staff for its measure, Shift-click for a range of measures; drag a selected note up or down the staff. In note input, click the staff to write the chosen value there. **Wheel** scrolls sideways, Shift+wheel through the parts, Ctrl+wheel zooms.
   - **Keys** as listed in §8.1, plus Ctrl+A (select all), Ctrl+B (add a measure), Insert (a measure before), Ctrl+←/→ (by measure), letters over a selection to start note input there.
   - Still to come in stage 3: slurs, hairpins, 8va, glissando and rit./accel. lines; lyric, chord symbol and text entry; tuplets and grace notes from the toolbar (they already save, play and draw); more endings than two.
3. The rest of the notation above. **Built 2026-11-19:**
   - **Lines** from the Lines palette or keys, over a range (first chord to last in each part) or from one note: slurs (`S`, to the next note), crescendo and diminuendo hairpins (`<`, `>`), 8va, 8vb and 15ma, glissando (to the next note), rit. and accel. The same line again takes it off. Lines move with their measures when measures are inserted or deleted.
   - **Text:** lyrics (`Ctrl+L`: Space or `-` to the next note, Shift+Space back, Enter for the next verse, Backspace in an empty box goes back), chord symbols (`Ctrl+K`, Space to the next chord), staff text (`Ctrl+T`) and rehearsal marks (`Ctrl+M`), also from the Text palette. `Esc` finishes.
   - **Tuplets** (`Ctrl+2`–`9`, the toolbar's 3): the selected note becomes the first of the tuplet, the others rests; in note input the tuplet is made at the cursor from the chosen value, and notes entered in it take its ratio.
   - **Grace notes:** acciaccatura (`/`, the toolbar) and appoggiatura, at the chord's top note and then selected so they can be moved; click just left of a chord to select its grace note.
   - **Endings** 1st to 4th, and end repeats played 2, 3 or 4 times.
4. Live collaboration. **Built 2026-11-19:**
   - Everyone with the score open sees each edit as it's made (as before), and now sees each other: every other editor's selection is outlined, or their note-input caret drawn, in their own colour with their name on it, and the status bar lists who else is editing. Cursors are sent up to ten times a second, only when they move; someone who closes the editor, walks away or logs out disappears from the others.
   - Undo keeps working while others edit: undoing puts your own version back, so the last change wins. Only when someone inserts or deletes measures or adds, removes or reorders parts is your undo history cleared, since it no longer lines up.
5. The desk as a source. **Built 2026-11-19** (§3.6).

## 9. Config menu and hotkeys
- **Opening it:** Ctrl+M, rebindable in vanilla Controls and in the mod's own hotkey tab. It also opens from the Mods list.
- **Style:** like Tweakeroo, with search, presets, and import and export. Operators can edit server settings in-game.
- **Extra hotkeys:** unbound by default.
- **Categories:**
  - **Power:** Energy or None mode, every FE/t value, system cap (500), Create maximum RPM (64) and SU per RPM.
  - **Single-speaker mode:** all of its values.
  - **Hearing:** speaker range (32) and volume behind a speaker (30%).
  - **Daisy chain:** loss (5%), floor (10%), and the single-speaker-mode loss (15%).
  - **Blowouts and distortion.**
  - **Wires:** invisible-wire mode and the distance warning.
  - **Chunk loading.**
  - **Copies and erasing.**
  - **Redstone:** the default for the Redstone Control toggle (off).
  - **Audio engine:** including **Room Sound Physics** (§7.4) and sound through blocks (§7.3).
  - **Score editor.**
  - **Instruments.**
  - **Import/export.**
  - **Server limits.**
  - **Performance.**
  - **Accessibility.**
  - **Hotkeys.**
  - **Debug.**
- **Built 2026-11-19 (M11):**
  - **Files:** server settings in `config/minescore-server.json`, each player's own in `config/minescore-client.json` (the old `minescore-common.toml` and `minescore-client.toml` move over by themselves the first time). Both are plain JSON, saved whenever something changes.
  - **The menu:** categories down the left, each setting a row: its name (yellow once changed from the default), the value (on/off, a choice to click through, Shift-click for the one before; numbers and text typed in, taking effect on Enter or clicking away), its unit, and ↺ back to the default. Hovering says what it does, its default, and whether it's a server setting. A search box looks through every category at once.
  - **Server settings** (◆): the server's, sent to everyone who joins and again whenever they change. Operators (and whoever's world it is) change them in-game, and they take effect at once; for everyone else they're locked (◇). From the title screen (the Mods list), the menu changes this computer's own file.
  - **Presets:** MineScore's own (Defaults, No power, Light on resources, Big venues, Forgiving speakers), and any saved from the settings as they are ("Save current", kept in `config/minescore-presets/`, deletable). A preset changes only what it holds; server settings only for operators.
  - **Import and export:** Export copies every setting to the clipboard and saves `config/minescore-settings-export.json`; Import applies settings from the clipboard.
  - **Hotkeys:** Ctrl+M opens the menu; "Invisible wires", "Room Sound Physics" and "Sound through blocks" on/off are unbound until given keys. All are in vanilla Controls (under MineScore) and the menu's Hotkeys tab, where clicking one and pressing keys (with Ctrl, Shift or Alt) changes it, and Delete clears it.
  - **What's in each category:**
    - **Power** (server): Energy or None, system cap, hub, effect card, Tweeter, Middle, Subwoofer, chain step and Lathe costs, Create's maximum RPM and SU per RPM.
    - **Single-speaker mode** (server): volume and range (60%), daisy chain loss (15%).
    - **Hearing** (server): speaker range (32), volume behind a speaker (30%).
    - **Daisy chain** (server): loss past the budget (5%) and the quietest (10%).
    - **Blowouts and distortion** (server): speakers can blow (on), seconds over the limit (2), the None-mode restart wait (5 s), each speaker type's limit (9, 10, 11 dB).
    - **Wires:** invisible wires (each player's), the distance warning (server).
    - **Chunk loading** (server): keep playing systems loaded.
    - **Copies and erasing** (server): copying records, copying scores. Erasing stays the furnace recipe, which a data pack can remove.
    - **Redstone** (server): Redstone Control on by default.
    - **Audio engine:** Room Sound Physics, sound through blocks, the Speed algorithm, the bass and treble crossovers (200 Hz, 2 kHz).
    - **Score editor:** what the arrow keys select, re-strike long notes, and the editor's shortcuts.
    - **Instruments:** which the instrument list offers.
    - **Import/export:** the MIDI import grid (8th, 16th or 32nd), the export folder (typed or picked).
    - **Server limits** (server): parts (16) and measures (2000) a score may have, the largest song (64 MB).
    - **Performance:** speakers at once (48), high-quality effects.
    - **Accessibility:** Now Playing messages.
    - **Debug** (server): log every system starting and stopping.

## 10. Loader structure
- **Code layout:** a shared core, with separate Forge, NeoForge and Fabric modules. Forge 1.20.1 comes first.
- **Animations:** GeckoLib.
- **Create:** **optional** integration, only active if Create is installed.
- **Energy API:** Forge Energy on Forge and NeoForge; Team Reborn Energy on Fabric.

---

## 11. Art plan (all original)

### 11.1 Styles
Confirmed 2026-11-19: the default look plus three built-in packs (Modern, Retro and a 16× Minecraft-style one).

| Style | Ships as | Resolution | GUIs restyled |
|---|---|---|---|
| **Minecraft-style** | Default | 32× | Yes |
| **Minecraft-style 16×** | Built-in resource pack | 16× | Uses the default GUIs |
| **Retro 80s hi-fi** | Built-in resource pack | 32× | Yes |
| **Modern studio** | Built-in resource pack | 32× | Yes |

- **The packs** show in vanilla's Resource Packs screen, off by default, and can be stacked over any other pack.
- **Minecraft-style 16×:** the default art at half the resolution. Each 2 × 2 block of pixels becomes its most typical pixel, so it stays crisp rather than blurred.
- **Retro 80s hi-fi:** brushed-silver faces with chrome knobs and screws, rosewood cabinets, teal fluorescent (VFD) displays, amber lamps and warm VU lights, paper-brown speaker cones. Screens are silver with VFD-teal text and amber highlights.
- **Modern studio:** matte black hardware with flush, screwless panels, pale birch cabinets, white speaker cones, ice-blue and white LEDs, black OLED-style displays. Screens are flat charcoal with ice-blue highlights.
- **Screens drawn in code** (the score editor, config menu, lists) follow the active pack through a colour theme file (`assets/minescore/minescore/theme.json`), which any resource pack can also override.
- **Sounds:** a **separate built-in sound pack** each for Retro and Studio. Turning on a texture pack doesn't change the sounds.
- **Total:** 4 texture sets, 3 GUI sets, 3 sound sets.

### 11.2 Block states
| Block | States |
|---|---|
| Disc Cutting Lathe | idle · cutting (its speed follows the amp gauge or Create RPM) · unpowered · overspeed break (the record shatters) · record on the platter with its dye and trim |
| Speakers ×3 | standing and hanging · 4 facings · idle · playing (cone moves with the audio) · Bluetooth · unpowered · shorted (sparks, then pops off) |
| Amplifier | off · on · VU meters · clipping · redstone override · Redstone Control on or off · labelled jacks |
| Channel Manager | off · on · L/R/Mono lights · forced mono · Redstone Control light · jacks |
| Audio Mixer | empty · docked (built 2026-11-20: the MP3 Player stands in the dock, its screen lit with power and dark without) · playing · paused · unpowered · underpowered warning · faders and meters |
| Writer's Desk | empty · score on the desk · in use · jacks |
| Cable Anchor | input and output ends · on floor, wall or ceiling |
| Vinyl Display | half-record plaque · empty · a record standing half out of its slot, in its dye and trim · on a wall or standing on the floor |
| Music Cabinet | walnut with an open front · hollow when empty · cream Score spines filling the top shelf from the left · record sleeves in each record's dye filling the bottom shelf, the record and label showing through a hole · the pointed-at one outlined |
| Multi-Effects Unit | off · on · each slot empty or holding its card · bypassed slots · display showing the preset · disabled by redstone |
| Vinyl Player | the Lathe's shape in walnut brown · empty (bare recessed platter) · record on the platter in its dye and trim · spinning while playing · back jack panel |

- **Powered and unpowered** (built 2026-11-19): the Mixer, Channel Manager, Amplifier and MEU show their usual face while their system has power, and the same face with **every indicator off** when it hasn't: lamps and LEDs dark, the Amplifier's VU meter unlit with its needle resting at the left, and the MEU's display blank. A system has power when its source gets at least what its hubs need; a hub in no system is dark, except a standalone Amplifier with its own 50 FE. In None mode they're always lit.
- **Undersides** (2026-11-19): every block has its own bottom texture, in its body's material, with rubber feet and something of its own: a bearing with a shaft socket on the Lathe, heat-sink fins on the Amplifier, a vent grid on the Channel Manager, an access panel on the MEU, a bass port on the Subwoofer, felt strips on the Vinyl Display, and a plinth on the Music Cabinet.

### 11.3 Items
- **Blank Vinyl.**
- **Vinyl Record:** a tintable dye layer, plus 16 trim-pattern overlays using vanilla trim palettes.
- **MP3 Player:** off and playing.
- **Wire coils ×3.**
- **Wire Cutters.**
- **Effect cards ×6** and the **Preset Chip**.
- **Blank Score.**
- **Score.**
- **Manual.**
- **Held poses.**

### 11.4 Everything else
- **Cables:** 3 cable textures and plug ends.
- **GUIs:**
  - Lathe, with a Create RPM and time readout.
  - MP3 Player, with EQ.
  - Mixer tab.
  - Amplifier.
  - Channel Manager.
  - Speaker.
  - Multi-Effects Unit (six card panels, chip slot, expression pedal).
  - Score editor, including the import and copy prompt.
  - Score viewer.
  - Desk.
  - Config.
  - Widget kit.
  - HUDs: now playing, wiring, and a power-budget readout.
  - Manual pages.
- **Particles:** notes, shavings, sparks, short-circuit smoke and record shards.
- **Sounds:** made in code (synthesized), with no recordings needed, and played at the Blocks volume at a slightly different pitch each time. Built 2026-11-19 (M12 stage 2):
  - **Record shattering** on an over-speed Lathe: a sharp crack, then shards clattering down.
  - **Jack plugged in** (starting or finishing a cable) and **unplugged** (cancelling one): a click, the plug's knock and a detent; a pop coming out.
  - **Wire cut** with the Wire Cutters: a metallic snap.
  - **Speaker short:** a hard pop and thump, then fizzing crackle.
  - **Needle drop** when the Vinyl Player starts a record: a soft thump and surface crackle.
  - **Docking** an MP3 Player in the Mixer: a latch and a seat.
  - **Sound packs:** Retro is heavier and warmer, with mechanical clunks, more crackle and a touch of mains hum; Studio is clean, short and precise, with a digital glitch on a short. A pack picks the style with `assets/minescore/minescore/sound_style.json`.
- **Branding:** logo, icon, creative tab and advancement icons.
- **Note:** all the art is made with code. It will be clean and consistent but not hand-painted, and you can request revisions to anything.

## 12. Advancements
| Advancement | How to earn it |
|---|---|
| Fresh Cut | Cut your first record |
| Bootleg | Copy a record |
| Mixtape | Load 10 songs into an MP3 Player |
| Throwback | Play a Vinyl Record on a Vinyl Player |
| Plugged In | Wire your first speaker |
| Full Range | Have all three speaker types playing at once |
| Stereo | Set up a Left and a Right speaker |
| Daisy Chain | Chain 10 speakers |
| Too Loud! | Short a speaker |
| Redstone DJ | Control a hub with redstone |
| Composer | Name a score |
| Duet | Edit a score with another player |
| Pressed Score | Cut a score onto a record |
| Bluetooth | Play your MP3 Player through a single speaker |
| Custom Pressing | Dye and trim a record |
| **Overclocked** *(new)* | Break a record by spinning the Lathe above 64 RPM |
| **Power Budget** *(new)* | Run a system that draws 500 FE/t |
| **Pedalboard** *(new)* | Fill all six slots of a Multi-Effects Unit |
| **Slowed + Reverb** *(new)* | Play a song slowed down with reverb on |

## 13. Future versions
- Subwoofer screen shake and vibration particles.

## 14. Milestones
| # | Milestone |
|---|---|
| M0 | Multi-loader setup (needs **JDK 17**) and placeholder registrations |
| M1 | Wiring: jacks, cables, anchors, Wire Cutters, rules |
| M2 | Audio engine: decoding, crossover, directional sources, sync, chunk loading |
| M3 | Lathe, records, upload and cache, preview, copies, erasing, dye and trims |
| M4 | MP3 Player: handheld, EQ, playlists, Bluetooth mode, item frame. Mixer. **Vinyl Player** (§3.8): plays Vinyl Records, record shown and spinning, Now Playing |
| M5 | Amplifier (caps, EQ, line amps), Channel Manager, chains, shorts, sound through blocks (§7.3), Room Sound Physics (§7.4) |
| M6 | **Multi-Effects Unit** (§3.10): the hub, olive links, six effect cards, Preset Chips and built-in presets, speed modulation with its algorithms, printing effects onto records. Its power cost comes with M7 and its redstone (disable, comparator) with M8 |
| M7 | Power: FE budget, None mode, Create integration. A dev-only Creative Power Source (endless FE) already exists for testing. FE, None mode and the Create kinetic Lathe built 2026-11-19 |
| M8 | Redstone and the Redstone Control toggles. Built 2026-11-19 |
| M9 | Score, Writer's Desk, editor (MuseScore-style, §8), collaboration, Desk as a source. The Vinyl Player was built in M4. Built in stages (§8.4); all five built 2026-11-19 |
| M10 | Import/export, score-to-record, resource-pack instruments and fonts. Built 2026-11-19 (§8.3, §3.1) |
| M11 | Config menu, hotkeys, presets. Built 2026-11-19 (§9) |
| M12 | Art in all styles, sound packs, Manual, advancements, JEI category, Jade/TOP, polish. Built 2026-11-20 (stages below) |

**M12 stages** (started 2026-11-19):
1. **Style packs:** the Retro, Modern and 16× built-in resource packs, the screen colour theme. *Built 2026-11-19.*
2. **Sounds:** MineScore's own synthesized sounds (record shatter, plugging and unplugging jacks, cutting wire, a speaker shorting, the needle dropping, docking an MP3 Player), and the Retro and Studio sound packs. *Built 2026-11-19.*
3. **Manual:** the in-game guidebook. *Built 2026-11-19:* right-click it to open an open-book screen: an introduction and clickable contents, then nine chapters (Records, Playing Music, Building a System, Speakers, Hubs, Power, Redstone, Writing Music, Settings), each with its item as an icon. Arrows, the mouse wheel or the arrow keys turn pages; Backspace goes back to the contents. The text is `assets/minescore/minescore/manual/<language>.json`, so packs can translate it.
4. **Advancements:** the 19 in §12. *Built 2026-11-19:* their own tab on a walnut background, under a MineScore root earned by picking up any MineScore record, wire, score, MP3 Player or the Manual. Three branches: records (Fresh Cut first), systems (Plugged In first) and scores (Composer first). Things that happen in the world (a short, a shattered record, a system's sound or power) are earned by every player within 32 blocks; Stereo needs a speaker set to Left and one set to Right.
5. **JEI and Jade/TOP:** a JEI category for the Lathe's recipes; Jade and The One Probe tooltips for the system blocks. *Built 2026-11-19:* JEI shows a Disc Cutting Lathe category (cut a song, press a score, copy a record, load an MP3 Player), with the Lathe as its catalyst, and info pages for the Manual, scores and wires. Jade and The One Probe show a speaker's band, name and channel, the record on a Vinyl Player or Vinyl Display, the Music Cabinet's counts, the score on a Writer's Desk, and whether a hub's Redstone Control is on. All three are optional and never bundled. The dev game runs with JEI and The One Probe; Jade is compiled against but not run in dev (its own mixins don't apply in the dev environment), so its tooltip is untried.
6. **Polish:** particles, HUDs, branding (logo, icon), and a last pass over everything. *Built 2026-11-19–20:*
   - **Particles (built):** MineScore's own, drawn in code. Music notes rise from playing speakers in their band's colour (red bass, yellow mid, blue treble), more often the louder they play. Vinyl shavings curl off the Lathe's cutter while it cuts. Record shards when a record shatters. Sparks and smoke when a speaker shorts.
   - **3D speaker drivers (built, asked for 2026-11-19):** the Subwoofer's and Middle Speaker's cones sink into a recess behind the rubber surround, with a domed dust cap; the Tweeter's dome stands out of its waveguide. While playing, the cone pumps in and out: harder the louder it's driven (the music's loudness at that speaker times its system's gain), faster the higher it's playing, from a slow pump on bass to a fast buzz on treble.
   - **Particles setting (2026-11-20):** "Particles" in the config menu's Performance tab, a client setting, **off by default**. It switches every MineScore particle on or off.
   - **Logo (built):** a record with a red label beside MINESCORE in gold capitals, in the Mods list.
   - **Wiring HUD (built):** while laying a cable, a line above the hotbar shows the block it started at, how far the last fixed point is, and how many anchors it has. It turns amber past the hearing range. Power readout: Jade and The One Probe show a source's power budget, and the Mixer tab shows it against the demand.
   - **Config menu in small windows (fixed):** the tabs, value buttons and top buttons shrink to fit. Long names end in "…" before the unit or the ◆ marker, and the legend wraps onto two lines.
   - **See-through fixes (2026-11-20):** every model was checked for faces showing clear pixels. The Lathe's side texture had a clear top half, and the Vinyl Display's stepped plaque had clear corners. Both are now solid. The rounded edges of every block left a thin channel along each edge, open at both ends, that you could see straight through along the edge. Each channel now has a wall across its middle. The only holes left are deliberate, each with something behind it: the speaker drivers (their pockets) and the record sleeves (the record inside).
   - **More see-through fixes (2026-11-20):** the speaker pockets now reach a pixel past the driver hole all round. The hole's edge pixels poked past the old walls, so you could see into the speaker. The plugs that close the edge channels moved from the middle of each edge, where they showed as a stray pixel, to its two ends, textured like the face they meet.
   - **Breaking sounds:** the axe-mined blocks (Vinyl Player, Writer's Desk, Music Cabinet) already break with the wooden sound.
   - **Config fix (2026-11-20):** server settings changed in a singleplayer world now save. Before, the menu set them in the same game before the server got the change, so the server saw nothing new, didn't save, and leaving the world reloaded the old file.

---

## 16. Final decisions (S1–S8, all settled)

- **S1. Over budget:** speakers are powered in chain order, starting nearest the source.
  - Speakers within the budget play at full volume.
  - The first speaker past the budget starts the 5% daisy-chain curve, and the rest continue it.
- **S2. After a short:** the line **stays open** until a player places a speaker and rewires it. The power cycle (or the 5 s wait in None mode) is also needed.
- **S3. Create plus an Amplifier:**
  - The **faster** cut time applies.
  - The 64 RPM overspeed break also applies when copying.
  - Loading songs into an MP3 Player is instant.
- **S4. Line Amplifier caps:**
  - No speaker can go above the first Amplifier's cap.
  - With no main Amplifier, Line Amplifiers cap at 100%.
- **S5. Writer's Desk as a source:** costs 50 FE/t and takes the system's power input. Editing is still free.
- **S6. Standalone Amplifiers:** an Amplifier outside a system accepts FE directly. Inside a system, it draws from the source.
- **S7. Vinyl Player jacks** (was: jukebox): 12, 16 and 20 ga outputs on its back panel. The jukebox's four side panels were dropped because the Vinyl Player has a real front.
- **S8. Redstone Control toggle:** **off** by default on newly placed hubs. Configurable.
