# MineScore Scope

This is the whole plan for MineScore. It's a Minecraft mod where you cut your own songs onto vinyl, wire up a real hi-fi system with speakers, amps and effects, and write sheet music in game with an editor that works a lot like MuseScore. Everything in here is decided unless it's marked *(proposed)*. Those are defaults that could still change.

- **Where it's at:** scope v5.1, signed off on 2026-11-18. M0 through M12 are all built, and 1.0.0 came out on 2026-11-20. There aren't any open questions left. The last ones were settled as S1–S8 (§18).
- **What it runs on:** Forge 47.x, Minecraft 1.20.1, Java 17 first. NeoForge, Fabric and newer Minecraft versions come later.
- **Client and server:** it's needed on both, and singleplayer and multiplayer are supported equally.
- **Art:** all original. Models, textures, animations, GUIs, particles, sounds, the logo, the manual art and advancement icons are all drawn in code (`tools/textures.py`), never vanilla textures.
- **Recipes:** designed in `tools/recipe_maker.py` (§12).
- **Renames:** records used to be Blank CD and Encoded CD. On 2026-11-19 they became **Blank Vinyl** and **Vinyl Record** (the item IDs stay `blank_cd` and `encoded_cd`). The same day the Multi-Effects Unit (§5.5) became the new M6, so power, redstone and everything after it moved up one number.
- **Languages:** the mod follows whatever language Minecraft is set to, with 19 languages from 15 translation files in `tools/lang/`.

---

## 1. Names

Every item and block, what it shows as in game, and its registry ID.

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
| Item | MineScore Manual | `minescore:manual` | Built-in guidebook (§11) |
| Item | Effect cards ×6 | `minescore:card_drive`, `card_modulation`, `card_delay`, `card_reverb`, `card_amp_cab`, `card_dynamics` | Stack to 1. Keep their settings |
| Item | Preset Chip | `minescore:preset_chip` | Stacks to 1. Holds one MEU preset |
| Block | Disc Cutting Lathe | `minescore:disc_cutting_lathe` | 1 × 0.5 × 1 · pickaxe |
| Block | Vinyl Player | `minescore:vinyl_player` | 1 × 0.5 × 1 · axe *(proposed)*. Plays Vinyl Records and can be a system's source (it replaced the jukebox plans) |
| Block | Vinyl Display | `minescore:vinyl_display` | Like an item frame, for Vinyl Records only · any tool |
| Block | Music Cabinet | `minescore:music_cabinet` | 1 × 1 × 1 · axe. Holds 16 Scores and 8 Vinyl Records (§4.2) |
| Block | Audio Mixer | `minescore:audio_mixer` | 3 × 1.5 × 1 · pickaxe |
| Block | Multi-Effects Unit | `minescore:multi_effects_unit` | 1 × 1 × 1 · pickaxe. Six effect card slots (§5.5) |
| Block | Channel Manager | `minescore:channel_manager` | 1 × 1 × 1 · pickaxe |
| Block | Amplifier | `minescore:amplifier` | 1 × 1 × 1 · axe |
| Block | Subwoofer | `minescore:subwoofer` | 1 × 2 × 1 · pickaxe |
| Block | Middle Speaker | `minescore:middle_speaker` | 1 × 1 × 1 · pickaxe |
| Block | Tweeter Speaker | `minescore:tweeter_speaker` | 0.5 × 0.5 × 0.5 · pickaxe |
| Block | Cable Anchor | `minescore:cable_anchor` | Any tool or by hand |
| Block | Writer's Desk | `minescore:writers_desk` | 2 × 1 × 1 · axe |

**Creative tab order** (2026-11-19): Blank Vinyl, Vinyl Record, Disc Cutting Lathe, Vinyl Player, Vinyl Display, Music Cabinet, MP3 Player, Audio Mixer, *Multi-Effects Unit, the six effect cards, Preset Chip* (once built), Channel Manager, Amplifier, Subwoofer, Middle Speaker, Tweeter Speaker, 12/16/20 ga wire, Cable Anchor, Wire Cutters, Writer's Desk, Blank Score, Score, MineScore Manual.

Every block faces whichever way the player was looking when they placed it.

---

## 2. Records and the Lathe

This is how your own music gets into the game. You drop songs in a folder, then cut them onto vinyl at the Disc Cutting Lathe.

### 2.1 The song folder
- Songs come from a folder called `blkcdsongs` (short for "blank CD songs", from back when the records were CDs). It's at `.minecraft/blkcdsongs/` in each player's own instance, not in a world folder.
- It takes `.mp3`, `.ogg`, `.wav`, `.flac`, `.mid`/`.midi`, `.nbs` and MineScore scores, with no limit on size or length.
- How songs get shared with other players is in §8.1.

### 2.2 Blank Vinyl
- Put it in the Lathe's **input slot** to cut a song onto it.
- Put it in the Lathe's **MP3 slot** instead and it gets a copy of whatever Vinyl Record is in the input slot.

### 2.3 Vinyl Record
- **Name:** by default it's the file name minus the extension. You can rename it at the Lathe for free, kind of like an anvil.
- **Tooltip:** the title, length, who cut it, and whether it's the **Original** or **Copy #n**.
  - *n* counts every copy that's ever been made from the original, copies of copies included.
  - So there's no "copy of a copy" label. Copied scores get numbered the same way.
- **Dye:** works like dyeing leather armor, so any combo of dyes you want gives you a custom color.
- **Trim:** done at a smithing table.
  - The **pattern** (16 templates) and the **material** (every trim material) both matter.
  - The template gets **used up**.
  - A record can only have **one trim** at a time.
  - The dye and trim **show on the record when it's in the Lathe or the Vinyl Player**.
- **Playing it:** in a Vinyl Player (§3.1). Vanilla jukeboxes don't take Vinyl Records.
- **Erasing it:** put it in a furnace. It needs fuel, takes the normal smelt time and gives XP. It works on copies too, and you get a Blank Vinyl back.
- **Copying it:** at the Lathe. It takes as long as cutting does, and copies can be copied.

### 2.4 Disc Cutting Lathe (1 × 0.5 × 1)
The block that cuts songs onto records, copies them, and loads songs onto MP3 Players.

| Input slot | MP3 slot | What happens |
|---|---|---|
| Blank Vinyl | — | Pick a file from `blkcdsongs`, then press **Cut**. You get a Vinyl Record (Original) |
| Score | Blank Vinyl | Turns the score into audio and cuts it onto the Blank Vinyl. The Score stays. You get a Vinyl Record (built in M10) |
| Vinyl Record | MP3 Player | Adds the song to the player. The record stays where it is |
| Vinyl Record | Blank Vinyl | Makes **Copy #n** |

- **The song list:** when a Blank Vinyl goes in, a list of the files in `blkcdsongs` pops up.
  - It has a Preview button, a search box, and sort buttons for **Name, Date modified, Type and Size**. Click the one that's on again to reverse it.
  - Each row shows whatever detail it's sorted by, and hovering over a song shows all of them (added 2026-11-19).
- **Using up the vinyl:** pressing **Cut** (or Copy) uses the Blank Vinyl right away. It only comes back if the cut gets abandoned.
- **Empty or loaded:** with no record in it, the Lathe shows an empty, recessed platter. Once a record's loaded you can see it sitting on the platter.
- **Power:** **20 FE/t**, or rotation from **Create**. Without power it can still **preview**, but it can't cut, copy or load songs.
- **Cut and copy time with FE:**

| Amplifier band wired to the Lathe | Cut time |
|---|---|
| none | 10 s |
| 20 ga | 5 s |
| 16 ga | 2 s |
| 12 ga | instant |

- **Wiring an Amplifier to it:** the Lathe has a 12, 16 and 20 ga input jack, and each one connects to the **matching band output on an Amplifier** (jacks only join matching colors). The band you use sets the speed in the table above, not the wire gauge.
  - The Amplifier and Lathe can work **without being part of a speaker system**, but they both still need power.
- **Cut and copy time with Create:**
  - **Time** = **3 × 64 ÷ RPM seconds**, so 64 RPM takes 3 s and 1 RPM takes 192 s.
  - **Stress:** 1 SU per RPM, so 64 RPM puts 64 SU on the network.
  - **Speed limits:** no minimum RPM, and a max of **64 RPM**. Go over 64 and the **Blank Vinyl breaks and gets deleted**.
  - How this works together with an Amplifier is S3 (§18).
  - **How it's built** (M7, 2026-11-19): with Create installed (6.0 or newer, it's optional and never bundled), the Lathe is a full Create kinetic block.
    - It takes rotation from a **shaft or cog underneath it**, which turns its platter on the vertical axis, and it puts real stress on the network. Rotation doesn't need any FE.
    - Every tick, whichever is faster out of rotation and FE does the work, so changing the speed in the middle of a cut kicks in right away.
    - Over the max, the record being cut or copied onto **shatters**, with record shards and the shatter sound. An overstressed network counts as not turning.
  - **No extra messages for rotation** (removed 2026-11-19): the screen just shows the cut's progress like normal, and Engineer's Goggles show Create's own speed and stress lines.
  - **Config** (`minescore-common.toml`, `[create]`): `maxRpm` (64) and `stressPerRpm` (1).

---

## 3. Playing music

### 3.1 Vinyl Player (1 × 0.5 × 1, built in M4)
A record player for Vinyl Records. On 2026-11-18 it replaced the vanilla jukebox in every plan, so MineScore doesn't touch the jukebox at all anymore.

- **Look:** pretty much exactly like the Disc Cutting Lathe (half-height deck, recessed platter, arm), but in the same **walnut brown** as the speakers. The iron parts are wood instead, and the arm is a tonearm, not a cutting head.
- **The record:** whenever there's a record in it, you can see it on the platter in its dye color and trim, and it **spins while it plays**. An empty Vinyl Player shows its bare recessed platter, same as the Lathe.
- **Using it:** right-click with a Vinyl Record to put it on (it starts playing), and right-click with an empty hand to take it off. "Now Playing: <title>" shows up for players in earshot whenever a record starts.
- **Static** (2026-11-20): records played on a Vinyl Player have a faint hiss, a low rumble and the odd crackle, the same on every speaker, like it's on the record itself.
  - It sits about 50 dB under full scale, so you hear it in the quiet parts and lose it under the music.
  - Config (client, Audio engine): **Vinyl static** on/off (on by default) and **Vinyl static amount** (100%, 0–400%).
- **On its own** (no speakers wired) *(proposed)*: it plays the full range from itself, the same all the way around, and you can hear it as far as a speaker (the hearing range, 32 blocks by default).
- **As a source** (F13): wire it to speakers and it **takes the Mixer's place**, so the sound comes out of the speakers instead.
  - It has **no volume control**.
  - It **can't** connect to a Channel Manager or Amplifier.
  - **Jacks:** 12, 16 and 20 ga band outputs on the speaker-band side of its back, following the jack rules. Since it has a real front, one back panel replaced the four side panels that were planned for the jukebox (S7).
  - **Channels:** set automatically from which way it faces. If you're facing its front, speakers on your left play Left and speakers on your right play Right (like a stereo facing you). Speakers straight ahead or behind play Mono.
- **Power:** the Vinyl Player itself doesn't need **any**, so it has no minimum. Only its speakers draw power, through its power input.
- **Redstone and hoppers** *(proposed)*: a comparator reads 15 while it plays. Hoppers can't load it.

### 3.2 MP3 Player
- **Loading songs:** at the Lathe, with the Vinyl Record in the input slot and the player in the MP3 slot. The record stays where it is.
- **Library:** unlimited songs, with playlists and everything a normal MP3 player can do.
- **Removing songs:** done in the player's own GUI. Removed songs are deleted, they don't come back out as records.
- **Listening on your own:** only the player holding it hears it.
  - It plays from anywhere in your inventory, not just your hands, and stops once it leaves your inventory.
  - It keeps its spot in the song, even when it gets passed between players.
- **EQ:** it has its own 10-band EQ for listening on your own.
  - When it's docked, the EQ tab moves **every** Amplifier in the system **by the same amount**, so each one keeps its own differences.
- **Volume:** the player's volume and the Mixer's volume are the same slider.
- **Bluetooth mode:** for sharing songs with people nearby without setting up a whole system.
  - **Start and stop:** right-click a speaker with the MP3 Player. Right-click it again, or break the speaker, to stop.
  - **Who hears it:** **everyone** nearby, but only through the speaker. The holder doesn't also hear it in their "headphones".
  - **Sound:** the speaker only plays its own band, at 60% volume, and you can hear it up to 60% of the hearing range (about 19 blocks at the default 32).
  - **Range:** it stops if the holder goes more than 16 blocks away (like bluetooth). If the MP3 Player is in an **item frame within 8 blocks** though, that speaker keeps playing through the queue, so you can start a little vibe.
  - **Chains:** daisy chains of one speaker type only, losing 15% each speaker.

---

## 4. Showing off and storing

### 4.1 Vinyl Display (added 2026-11-19)
- **What it is:** an item frame, but only for **Vinyl Records**.
- **Look:** a half-record plaque in the same style as the record, with grooves, a bright rim and half a red label. The record stands in a slot along the top, so its bottom half sinks into the plaque and its **top half sticks out**, in its own dye and trim.
- **Color** (2026-11-19): the half label on the display takes the **dye color of the record** in it, and it's red when it's empty.
- **Placing it:** on a wall it hangs flat. On top of a block it stands on a little foot, facing the player. It can't go on ceilings, and it drops if whatever's holding it up goes.
- **Using it:** right-click with a Vinyl Record to put it up, and right-click with an empty hand to take it down. The record's title floats above it while you're looking at it.
- **Comparator:** 15 while it's showing a record. Hoppers can't fill it.

### 4.2 Music Cabinet (1 × 1 × 1, added 2026-11-19)
A walnut cabinet with an open front for keeping your music in. Decided on 2026-11-19.

- **What it holds:** **16 Scores** on the top shelf and **8 Vinyl Records** on the bottom one. Only written Scores and cut records go in, **nothing blank** (no Blank Scores, Blank Vinyls or empty Scores).
- **Look:** walnut, the same wood as the speakers, with an open front.
  - When it's empty it's **hollow**, just the shell and the middle shelf, dark inside.
  - It shows **how full it is**. Each shelf fills from the left, with a cream paper spine for each Score and a **sleeve for each record**.
  - Each sleeve is the **record's dye color**, and you can see the record and its label through a hole in the side of the sleeve.
- **Putting things in:** right-click the front with a Score or a Vinyl Record. It goes in the first free spot from the left (facing the front) on its own shelf.
- **Taking things out:** **Shift+right-click** with an empty hand on the Score or sleeve you're pointing at, and it goes into your inventory (or drops if you're full). The rest **slide over to the left**, so a shelf always fills from the left with no gaps.
  - The one you're pointing at gets outlined, and its title floats above the cabinet.
  - A plain right-click with an empty hand doesn't do anything, and there's no screen.
- **Comparator:** how full it is, 0–15 across all 24 spots. **Hoppers** can't fill or empty it.
- **Breaking it** drops everything inside. An axe mines it fastest.
- **Recipe:** planks, wooden slabs and a wooden pressure plate (§12).

---

## 5. Building a sound system

A system is a **source** (the Audio Mixer, the Writer's Desk or the Vinyl Player), optionally some **hubs** in between (Multi-Effects Unit, Channel Manager, Amplifier), and then the **speakers**, all connected with speaker wire.

### 5.1 Speaker Wire and Wire Cutters
- **Cables:** hang between their two ends with some sag. Wire doesn't get used up by length, and there's no max length.
- **Distance warning:** a chat message pops up if the straight line from the last connection to the player's head is longer than the hearing range. The connection still works.
- **Through blocks:** cables cross chunk borders and go right through blocks.
- **Wiring HUD** (built in M12): while you're laying a cable, a line above the hotbar shows the block it started at, how far away the last fixed point is, and how many anchors it has. It turns amber once you're past the hearing range.
- **Invisible wires:** a config option. With it on, wires only show while you're holding wire or Wire Cutters.
- **Wire Cutters:** remove a connection and give you the wire back.

### 5.2 Cable Anchor
- **Placing it:** anywhere (floor, wall or ceiling). It has a dedicated **input end** and **output end**.
- **Using it:** while you're laying a cable, right-click the anchor's input end. The cable mounts there and carries on from the output end, keeping where it originally started.
- **Limit:** as many anchors per cable as you want.
- **Breaking it:** the cable reconnects straight through, from whatever fed the anchor to whatever it fed.

### 5.3 Speakers

| Speaker | Size | Band | Wire | Power |
|---|---|---|---|---|
| Tweeter Speaker | 0.5 × 0.5 × 0.5 | 2–20 kHz | 20 ga | 5 + 5×(n−1) FE/t |
| Middle Speaker | 1 × 1 × 1 | 200 Hz–2 kHz | 16 ga | 10 + 5×(n−1) FE/t |
| Subwoofer | 1 × 2 × 1 | 20–200 Hz | 12 ga | 25 + 5×(n−1) FE/t |

*n* is the speaker's spot in its chain.

- **Placement:** a speaker has to be held up from **below or above**, never from the side. It can still sit right up against a wall, and it faces whichever way the player was facing when they placed it.
- **Stacking flush** (2026-11-20): a Middle Speaker or Tweeter standing on a Middle Speaker, or a Tweeter on a Tweeter, sits flush on it with no gap.
  - It sinks 4 pixels into its own block space each step, and its jacks, sound and cone move down with it.
  - A column keeps stacking flush until the sinking adds up to a whole block (five Middle Speakers or three Tweeters). Then the next one starts fresh with a gap.
  - Subwoofers are full blocks, so they already stack flush.
- **Hearing range:** **32 blocks** by default, so it sounds more realistic (it was 128 until 2026-11-19). It's configurable as "Speaker range" in the config menu. The volume fades out over the distance.
  - Boosted past 100%, a speaker carries √gain times further.
  - Distance is measured from the center of the front face to the player's head.
  - It's loudest right in front and **30%** straight behind, changing smoothly with the angle.
- **Channels:** a speaker plays the channel it's assigned to, or mono if it doesn't have one.
- **Crossover frequencies:** configurable.
- **Speaker GUI:** opens when you **right-click with an empty hand**. You can rename the speaker there, and it shows its channel, power and state. Speakers can also be renamed in the Channel Manager.
- **Jacks:** one input jack and one output jack, for daisy chaining.
- **3D drivers** (built in M12, asked for on 2026-11-19): the Subwoofer's and Middle Speaker's cones sink into a recess behind the rubber surround, with a domed dust cap, and the Tweeter's dome sticks out of its waveguide.
  - While it plays, the cone pumps in and out. It goes harder the louder it's driven (the music's loudness at that speaker times its system's gain), and faster the higher it's playing, from a slow pump on bass to a fast buzz on treble.

### 5.4 Audio Mixer (3 × 1.5 × 1)
- **What it does:** it's the system's source, and you dock an MP3 Player in it.
- **GUI:** the MP3 Player's GUI, plus a Mixer tab.
- **Jacks:** 5 outputs: 12/16/20 ga, Channel Manager and Amplifier (plus the olive one to an MEU, §5.5).
- **Volume:** going over 100% needs an Amplifier, and channel controls need a Channel Manager.
- **Power:** 50 FE/t. The system's **power input** is here (§6.3).
- **Redstone Control toggle:** yes (§7).
- **Chunk loading:** the whole system stays loaded. Configurable.

### 5.5 Multi-Effects Unit, MEU (1 × 1 × 1, added 2026-11-19, built in M6)
A hub that runs the whole system's sound through up to six effect cards, like a guitarist's multi-effects unit. Decided as Q1–Q30 on 2026-11-19. Where Q1–Q18 were left open, these are the defaults.

- **Where it goes:** always right after the source, before any Channel Manager or Amplifier (§5.8). One per system. Never after a Channel Manager, Amplifier or speaker, and there's no "line" MEU on speaker lines.
- **Sources:** the Audio Mixer and the Writer's Desk. The Vinyl Player stays speakers-only. Bluetooth and listening on your own with the MP3 Player skip it.
- **Look:** a rack unit with six card slots across the front and a little display, mined with a pickaxe *(proposed)*.
- **Jacks (back):** hub I/O on the left half, band I/O on the right, like usual.
  - Hub links: an **olive green** input from the source, and outputs to a Channel Manager, an Amplifier and a Lathe. Each one still only joins its own match.
  - Its outputs match what they feed (2026-11-19). The MEU takes the Mixer's place in front of a Channel Manager or Amplifier, so its outputs to them are the Mixer's **purple** and **orange**. Its output to a Lathe is olive.
  - The Mixer and Desk get an olive output to the MEU. The Channel Manager gets a purple input from it, the Amplifier an orange one and the Lathe an olive one.
  - 12/16/20 ga band inputs and outputs, passed through like a Channel Manager does, so a speaker type only plays if its band runs through the MEU too.
  - Its links count toward the Amplifier's boost cap, and the thinnest link on the way wins (§5.7).
- **Cards:** six effect cards, one of each at most, in six slots (so it's like an upgraded machine).
  - Each card is a category. You pick **one effect** in it and set its knobs. A card keeps its settings when you move it to another MEU.
  - **Order:** the slot order is the order things get processed in, so move cards around to rearrange the chain.
  - Each slot has a **bypass**, each card has a **mix** (dry/wet) knob, and the unit has a **master bypass**.
  - **A fresh card doesn't change anything** (2026-11-19). Every mix knob starts fully dry, and effects without one start where they do nothing (Speed 100%, gate fully open, compressor 1:1 with no makeup, Tuner not retuning, Expression sweeping nothing). You turn it up to hear it.
  - Effects work on the **full stereo mix**, before the crossover splits it into bands. Mono speakers get the mono sum of stereo effects.
  - **Blowouts** (§6.5) count what comes out of the MEU, so Drive and compression push speakers harder.
- **The effects** *(knobs proposed)*:

| Card | Effects | Knobs |
|---|---|---|
| Drive & Distortion | Overdrive, Fuzz, High-gain | drive, tone, level, mix |
| Modulation | Chorus, Flanger, Phaser, Rotary speaker, Pitch shifter, **Speed** | rate, depth, mix; semitones (Pitch); speed % (Speed) |
| Delay | Straight, Stereo, Ping-pong | time (ms, or note lengths synced to the song's detected tempo), feedback, mix |
| Reverb | Room, Spring, Plate, Hall | size, decay, damping, pre-delay, mix |
| Amp & Cabinet Modeling | made-up amp models (Clean Tube, British Crunch, Boutique Lead, Modern High-gain) and cabinets (1×12 Open Back, 2×12, 4×12 Closed Back), with built-in impulse responses made just for MineScore | gain, bass, mid, treble, cabinet, level, mix |
| Dynamics & Utility | Wah, Noise gate, Compressor, Tuner, Expression sweep | per effect; the **expression pedal** is a slider in the GUI that sweeps whatever knob you choose |

- **Tuner** (Q19, decided 2026-11-19: our own code, no outside library): it works out the song's key, its tuning (what it tunes A to) and its tempo, and shows them on the MEU's screen. It retunes the song by a key shift and, when Retune is on, a new reference pitch for A (415–466 Hz), using the Pitch shifter's engine. The same analysis gives synced delays their tempo.
- **Speed:** 25%–200%, for the whole system, and kept in sync, so everyone's spot in the song and when the next song starts follow it.
  - **Config "Speed algorithm":** **WSOLA** time-stretch (the default, keeps the pitch, clean on most music, cheap), **Phase vocoder** (keeps the pitch, smoother on long notes, heavier) or **Tape** (varispeed, the pitch follows the speed like a record).
- **Presets:** the MEU has **built-in presets** (like Stadium, Lo-fi Radio, Cathedral, Slowed + Reverb).
  - Changing a built-in preset never changes it. It just turns into a new, unsaved preset.
  - Presets get saved on **Preset Chips**. Put a chip in the MEU's chip slot to write the current settings onto it, or load the chip's preset. A chip holds the cards' effects, order, knobs and bypasses. Loading one flags any card the preset needs that isn't in the unit.
- **Mixer tab:** shows the MEU **read-only**, with the preset's name, the cards in order and which ones are bypassed. You can only change effects at the MEU itself.
- **Power (M7):** 50 FE/t plus 10 FE/t for each card in use.
- **Redstone (M8):** a signal **disables** it (like master bypass), with the Redstone Control toggle like the other hubs. A comparator reads how many **active cards** there are (0–6). See §7.2.
- **Printing effects onto a record:** a Lathe linked to the MEU (olive jack) cuts songs **with the effects in them**, rendered on the cutting player's computer and shared as a new song.
  - While it's linked to the MEU, the Lathe **can't use an Amplifier** at the same time.
  - Each effect changed from its defaults adds **2 s** to the cut time.
- **Performance:** effects run once per system on each player's computer, not once per speaker, with a **Low/High quality** setting in the config.
- **Art:** the MEU block (cards showing in their slots, a lit display), six card items, the Preset Chip, and the MEU GUI (a pedalboard of six card panels with knobs, the chip slot, bypass switches and the expression pedal).

### 5.6 Channel Manager (1 × 1 × 1)
- **Channels:** sets each speaker to Left, Right or Mono.
- **Speaker list:** shows each speaker's name, type and range, with a Locate button.
- **Trims:** volume trims for speakers wired straight to the last hub. A trim also scales that speaker's chain or branch.
- **Jacks:** inputs for 12/16/20 ga and Audio Mixer. Outputs for 12/16/20 ga and Amplifier.
- **Placement rule:** it never goes after an Amplifier.
- **Power:** 50 FE/t.
- **Redstone Control toggle:** yes (§7.3).

### 5.7 Amplifier (1 × 1 × 1)
- **Controls:** a master volume plus a 10-band EQ (31, 62, 125, 250, 500, 1k, 2k, 4k, 8k and 16k Hz).
- **Boost cap:** set by the wires **linking the hubs together** on the way to it: the orange Mixer → Amplifier link, or the purple Mixer → Channel Manager link and the teal Channel Manager → Amplifier link. The **thinnest** gauge decides (changed 2026-11-19, band wires don't count anymore). An Amplifier with no link gets no boost (100%).

| Thinnest hub link | Max boost |
|---|---|
| 12 ga | 400% |
| 16 ga | 300% |
| 20 ga | 200% |

- **First Amplifier:** its cap is set by the hub links from the **source** side. Line Amplifiers can't push any speaker past that cap, so stacking amps can't give you unlimited volume (S4).
- **Jacks:** inputs for 12/16/20 ga, Audio Mixer and Channel Manager. Outputs for 12/16/20 ga.
- **Main and Line Amplifiers:**
  - There's at most **one** main Amplifier between the source and the first speakers.
  - You can have as many **Line Amplifiers** on speaker lines as you want. Each one serves one speaker type.
  - A Line Amplifier keeps faults contained, so a short after it doesn't affect anything before it. If its input line goes out though, everything after it goes out too.
- **Power:** 50 FE/t, drawn from the system. A standalone Amplifier (like one wired only to a Lathe) takes its own FE (S6).
- **Redstone Control toggle:** in its GUI (§7.4).

### 5.8 What connects to what
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

### 5.9 Connecting things
- **Jacks:** you click **specific jacks** on the models. Hub inputs and outputs are on one side and speaker-band inputs and outputs on the other, and jacks only join matching colors.
- **Hub → speaker, and speaker → speaker:** **right-click** with the **matching gauge**.
- **Hub → hub:** **shift-right-click** from jack to jack, with **any gauge**.
  - The exception is the **link wires** (purple, orange, teal) going to an Amplifier, since their gauge sets its boost cap.
- **Band paths:** a speaker type only plays if its band jacks are linked at every hub all the way back to the source.
- **Feeding hubs vs. speakers:** a band output that feeds another hub can't also feed speakers. Speaker outputs can fan out as much as you want.
- **Removing:** Wire Cutters remove a connection and give you the wire back.

### 5.10 A full system
```
            shift (any ga)        shift (any ga)
  MIXER ─────link────► CH.MGR ─────link────► AMP
    12 ─(shift)──────►  12 ──(shift)──────►  12 ──(12 ga)──► SUB ──► SUB …
    16 ─(shift)──────►  16 ──(shift)──────►  16 ──(16 ga)──► MID ──► MID …
    20 ─(shift)──────►  20 ──(shift)──────►  20 ──(20 ga)──► TWT ──► TWT …
              links on the way to the AMP: thinnest gauge sets its cap
```

---

## 6. Volume, power and failures

### 6.1 Gain at a speaker
gain = Mixer/MP3 volume × Amplifier master × Amplifier EQ × Channel Manager trim × power/chain factor × Line Amplifier settings

- Gain is capped at the first Amplifier's limit.
- Over 100%, a speaker gets louder **and** you can hear it from further away.

### 6.2 Power modes
| Mode | How it works |
|---|---|
| **Energy** (default) | FE costs, the power budget, the minimum and the cap all apply. Daisy-chain loss **only starts once the budget runs out** |
| **None** | No power anywhere, no minimum, no limits. **No daisy-chain loss** either, so you can have as many speakers as you want |

### 6.3 Energy mode
The idea is that power replaces the daisy-chain loss until the budget runs out, so small and mid-sized systems don't get punished, and bigger systems have to budget their power and be designed better to get more bang for their buck.

- **Power input:** only at the **source**: the Mixer, the Desk or the Vinyl Player.
- **Demand:**
  - Every hub (Mixer, Channel Manager, Amplifier, Line Amplifier): 50 FE/t. The MEU: 50 FE/t plus 10 FE/t per card in use.
  - Speakers: base cost + 5×(n−1), by their spot in the chain.
  - Lathe: 20 FE/t, powered on its own.
- **Minimum:** a system needs at least **the hubs' total cost** to run. For example, Mixer + Channel Manager + Amplifier needs 150 FE/t. A Vinyl Player source doesn't have a minimum.
- **Cap:** a system draws at most **500 FE/t** by default. Configurable.
- **Volume:** stays **full** as long as the supply covers the demand.
  - Speakers past the budget follow the daisy-chain curve (S1, §6.4).
- **Example:** Mixer + Amplifier (100), 2 Subwoofers (25 + 30), 4 Middle Speakers (10 + 15 + 20 + 25) and 4 Tweeters (5 + 10 + 15 + 20) = **275 FE/t**. That's under the 500 cap, so everything plays at full volume.
- **How it works in game** (built in M7, 2026-11-19):
  - **Taking FE:** the source, the Lathe and a standalone Amplifier take **Forge Energy from any side**, from cables, pipes or generators. MineScore only takes FE, it never gives any out. They store it too: a source holds 10,000 FE, and a Lathe or Amplifier holds 4,000.
  - **Budget:** while a system plays, its source draws the system's demand (up to the cap) every tick. The budget is what it actually managed to draw over the last second, so a weak generator gives you a smaller budget. An idle system doesn't draw anything.
  - **Below the minimum** the system goes silent, and the Mixer tab says how much its hubs need. The Mixer tab shows the budget against the demand, and each speaker's screen shows its cost and whether it's at full volume or past the budget.
  - **Resetting after a short:** the source has to go a whole second with **no FE coming in**, then Play starts it back up.
  - **Lathe:** without power it won't start a cut, copy or load, and a cut that's already going **waits** until the power comes back. Its screen has an FE gauge. An Amplifier speeding it up has to be powered too: a standalone one spends its own 50 FE/t while the Lathe works, and one that's in a system needs its source to have power.
  - **Config** (`minescore-common.toml`, `[power]`): `mode` (ENERGY/NONE), `systemCap`, `hubCost`, `meuCardCost`, `tweeterCost`, `middleCost`, `subwooferCost`, `chainStep`, `latheCost`. The curve's 5% and 10% joined the config menu in M11.

### 6.4 Daisy-chain curve (Energy mode past the budget, and single-speaker mode)
- **Loss:** each speaker plays at 95% of the one before it, down to a 10% floor.
- **Past the floor:** adding more speakers drops the first speaker by 5 points, with the ones in between spaced out evenly.
- **All at 10%:** each speaker plays at 10% × N₀ ÷ n, where N₀ is how many speakers there were when they all first hit 10%.
- **Position:** counted across every branch.
- **Reset:** a Line Amplifier resets the curve.
- **Single-speaker mode:** loses 15% per speaker.

### 6.5 Blowouts and shorts
- **What sets it off:** the gain setting **and** how loud the music actually is.
- **Order:** bass blows first, then mid, then tweeter. Thresholds *(proposed)*: Sub +9 dB, Mid +10 dB, Tweeter +11 dB.
- **Distortion:** off by default, with a toggle and thresholds in the config.
- **When a speaker shorts:**
  - It **pops off as an item**.
  - Everything after it loses its link to the hub, but those speakers stay connected to each other.
- **Fixing it:**
  - **Energy mode:** turn the **source's power** off and back on.
  - **None mode:** wait 5 s, then press **Play** on the Mixer.
  - How the gap in the line gets closed is S2 (§18).

---

## 7. Redstone
- **Toggle:** every hub's GUI has a **Redstone Control** toggle. It only controls **redstone coming in**, redstone going out (read with comparators) always works. It's **off by default** on newly placed hubs, and that's configurable.
- **Where the toggle is:** the Mixer tab, the Channel Manager and Amplifier screens (top right), and the MEU screen (under the pedal). Its tooltip explains what redstone does on that hub. Config: `redstoneControlByDefault` in `minescore-common.toml`.
- **Sides:** left and right are the **block's own**, like you're the block looking out of its front.
- **Power types:** "soft" means weak power and "hard" means strong power. Strong power is what a repeater, comparator, torch, lever or button gives. Redstone dust only gives weak power.
- **Speakers needed:** redstone only does anything on hubs that are connected to speakers.
- **How it's built** (M8, 2026-11-19):
  - **Comparators read each face separately**, so a Channel Manager's left and right, or an Amplifier's band groups, can give different signals. MineScore hooks the vanilla comparator for this, since vanilla never says which face it's reading.
  - Hubs check their neighbors whenever one changes, and again once a second, so they also catch up after the world loads.

### 7.1 Audio Mixer
| Input | What it does |
|---|---|
| Signal on the front, back, top or bottom | **Signal on: play** the queued music. Nothing plays if the queue is empty. **Signal off: pause** |
| Pulse on the left side | Next song (swapped with the right on 2026-11-19) |
| Pulse on the right side | Restart if more than 2 s has played. Otherwise go to the previous song, or restart if there isn't one. (The screen's Previous button uses the same 2 s now) |
| Comparator | 15 while playing, 0 otherwise |

### 7.2 Multi-Effects Unit
| Input | What it does |
|---|---|
| Any signal | Disables the unit, like master bypass, for as long as the signal lasts. Its display reads BYPASS |
| Comparator | How many cards are active (not bypassed), 0–6 |

### 7.3 Channel Manager
| Input | What it does |
|---|---|
| Weak power on the left or right side | Turns that channel off while the signal lasts |
| Strong power | Forces mono, then goes back to the setting before when the power stops |
| Comparator | 15 from the left side while Left plays, from the right side while Right plays, and from every side while Mono plays. "Mono plays" means the Channel Manager is forced to mono, by its switch or by strong power. All 0 while its system isn't playing |

### 7.4 Amplifier
| Side | Bands |
|---|---|
| Back | 1–3 |
| Left | 4–5 |
| Right | 6–7 |
| Front | 8–10 |

- **Side input:** signal 1–15 sets those bands' volume, where 15 = 400%. Signal 0 hands control back to the GUI value.
- **Bottom input:** signal 1–15 sets the **loudest band** to signal ÷ 15 × 400% and scales the other bands so they keep their ratios. The ratios are remembered even when a band is held at its cap. Signal 0 goes back to the GUI values.
- **Top input:** doesn't do anything.
- **Comparator output:** goes with the loudest band in that group, on the same scale as the inputs (15 = 400%, so a flat band reads 4). There's no output if no speaker type for that group is connected: back = Subwoofers, left and right = Middle Speakers, front = Tweeters. The top and bottom don't give any.

---

## 8. Sound engine

### 8.1 Audio engine and songs
- **Decoding:** each client decodes and caches songs. MP3 and FLAC need bundled decoder libraries.
  - MIDI, `.nbs` and scores get rendered to audio on the client of the player who cuts them.
- **Crossover:** splits the audio into 3 bands × 2 channels.
- **Speakers:** directional OpenAL sources.
  - The volume fades out over the hearing range (32 blocks by default).
  - They're loudest in front and 30% straight behind.
- **Sync:** the server keeps playback in sync, and players who join late start in the middle of the song.
- **Sharing songs:**
  - The original files stay in each player's own `blkcdsongs/` folder.
  - When a player cuts a record, the song gets uploaded in the background and cached in `<server>/minescore/cache/`.
  - Listeners download it once and keep it in their own instance's cache.

### 8.2 Sound through blocks (added 2026-11-18, built in M5)
Blocks between a speaker and a listener muffle it, and harder blocks muffle it more.

- **The path:** the straight line from the center of the speaker's front face to the listener's head. The speaker's own block is handled by which way it faces (§5.3), so it doesn't count here.
- **Loss per block on the path,** in decibels:
  - **loss = (3 + 4 × hardness) × coverage**, capped at 24 dB per block.
  - **hardness** is the block's mining hardness. For example: leaves 0.2 → 3.8 dB, glass 0.3 → 4.2 dB, dirt 0.5 → 5 dB, stone 1.5 → 9 dB, planks 2 → 11 dB, iron block 5 → 23 dB. Really hard and unbreakable blocks (obsidian, bedrock) hit the 24 dB cap.
  - **coverage** is how much of the block is solid, from its collision shape: 1 for full cubes, about 0.5 for slabs, and less for panes, fences and bars. Air, flowers, torches and other blocks you can walk through count as 0.
  - **Sound-dampening blocks** (wool and carpet, from Minecraft's `dampens_vibrations` tag) lose twice as much.
  - **Water and lava** lose 2 dB per block.
- **Total:** the losses add up along the path, capped at 48 dB total (about 0.4% volume).
- **Bass gets through walls best.** The loss is scaled per band: Subwoofer ×0.5, Middle Speaker ×1, Tweeter ×1.4. So through a wall you mostly hear the bass, like in real life.
- **No sudden jumps:** each speaker's path gets re-checked about 5 times a second, and its volume glides to the new value over a quarter of a second.
- **Config:** on/off, the base and per-hardness numbers, the per-block and total caps, the band scaling, and how often paths get checked.

### 8.3 Room Sound Physics (added 2026-11-19, built in M5)
A speaker in a sealed space sounds fuller than one out in the open, and every gap lets sound escape, like how a car stereo loses its punch when you open the door.

- **Measuring the room:** 64 rays go out from the speaker's front, evenly in every direction, up to 16 blocks. A ray counts as closed once the blocks it passes through take off 6 dB (one stone block, or a couple of glass blocks), using the same losses as §8.2. Open doorways, missing blocks and open trapdoors let rays out, and spaces bigger than 16 blocks count as open air.
- **Sealing:** enclosure = the share of closed rays. Seal = 1 − 4 × (1 − enclosure), at least 0, so small gaps matter a lot. With a quarter of the rays escaping, the effect is gone.
- **The effect:** up to **+4 dB** in a fully sealed space, scaled per band: Subwoofer ×2 (+8 dB, since bass builds up in closed rooms), Middle Speaker ×1, Tweeter ×0.6. Open air plays like before.
- **Who hears it:** listeners in the room with the speaker get all of it. It fades out as walls come between them (gone once the path loses 12 dB), so people outside hear the muffled sound from §8.2, not the room.
- **Smooth:** each speaker's room gets re-measured every 2 seconds, and the change glides in over a quarter of a second.
- **Blowouts** (§6.5) don't count the room. A speaker blows from its settings and the music, wherever it is.
- **Config:** a toggle called **Room Sound Physics** (on by default, in the config menu's Audio engine category), plus the ray count and range, the 6 dB closed-ray loss, the leak factor, the +4 dB max, the band scaling and how often rooms get checked.

---

## 9. Writing music

### 9.1 Blank Score and Score
- **Editing:** only at the Writer's Desk. Anywhere else, scores are read-only and can't be played back.
- **Name:** *Untitled Score* in italics until you name it. After that it's the custom title in the normal font.
- **A Blank Score** turns into a new *Untitled Score* as soon as it goes on a desk (built that way in stage 1, so a score exists before the editor does).
- **Copying:** done at the desk. Copies are numbered **Copy #n**, and scores can't be locked.
- **Where the notes live** (M9, 2026-11-19): on the server, like songs (`<world>/minescore/scores/`). The item only carries the score's ID and title, so big scores never bloat items or the network. A copy gets its own ID, so editing it never changes the original.
- **Size limit:** 16 parts and 2,000 measures.

### 9.2 Writer's Desk (2 × 1 × 1)
- **Building it:** the desk is made of **two Writer's Desk blocks placed side by side**. A single desk block shows as an unfinished half, and the full desk model only shows up once two desk blocks are next to each other (added 2026-11-18).
  - Two desk blocks facing the same way, side by side, join up, and either half opens the same desk.
  - The halves are **mirror images** (made symmetrical 2026-11-19). Each one is a drawer pedestal at its outer end under the top, with knee space between them, a back panel across, and one leather-inlaid top with the score sheet in the middle.
  - The **left half** (facing it) holds the slots, and its back panel has the jacks.
  - Breaking either half leaves the other one as a lone, unfinished half (M9 decision).
- **Slots:** a **score slot** and a **copy slot**. Put a Blank Score in the copy slot and a copy comes out instantly.
- **Screen:** the two slots, an **Open Editor** button, and Play / Stop / Loop for when it's a source.
- **Editor:** opens the score editor (§9.3). Several players can edit the same score together.
- **As a source:** it can take the Mixer's place as a system's source.
  - It has the Mixer's 5 outputs and plays the score on the desk.
  - Its volume is set at the Amplifier.
  - It takes the system's power input, and costs 50 FE/t (S5). Editing is still free.
  - Play / Stop / Loop are on the desk screen. With no speakers wired it plays from itself, like an unwired Vinyl Player.
  - Playback uses the score the way it was when Play got pressed. Edits count from the next Play.
  - **Built 2026-11-19 (M9 stage 5):** Play renders the score on the client of whoever pressed it, which uploads it like a cut song. The desk then plays it through its system (MEU, Channel Manager, Amplifiers, same as the Mixer) or, unwired, from itself at the hearing range.
    - Stop stops it, and Loop starts it again from the top when it ends. The desk screen shows "Playing" while it goes.
    - A short stops it, and it restarts the usual way (§6.5). Breaking the desk stops it too.

### 9.3 The score editor
A notation editor with as many of **MuseScore**'s features as possible, but drawn in MineScore's own pixel style. All its symbols are original pixel art in one texture atlas, so a resource pack can restyle the whole editor. That replaced the Bravura font (decided 2026-11-19, to keep "all art from scratch"). Most of how it works came out of the M9 question round on 2026-11-19, where all the proposed defaults were taken.

- **Layout:** staves scroll sideways, one row per part, with zoom (MuseScore's "continuous view"). A page view like printed sheet music comes later.
- **Parts:** up to 16, each with one instrument and its own clef. Chords are allowed.
- **Opening it:** from the desk's **Open Editor** button.
- **Saving:** the score gets sent in compressed pieces, and every edit goes back to the server as "this measure now reads…", so saving, undo and other editors all follow one path. Scores save a few seconds after a change and when the last editor closes.
- **Undo/redo:** the last 100 of your own edits.
- **Following edits:** undo, redo, paste and other edits scroll the view to where they happen if it's out of sight.
- **Moving around:** the **wheel** scrolls sideways, Shift+wheel goes through the parts, Ctrl+wheel zooms, and dragging in open space (or with the middle button anywhere) moves the view around.
- **Preferences** (⚙ or Ctrl+comma): every shortcut can be changed there, saved to `config/minescore-editor-keys.json`.

### 9.4 Entering notes
Mouse and keyboard both work like MuseScore.

- **Mouse:** pick a duration in the palette, click the staff, and drag to change the pitch. In note input, clicking the staff writes the chosen value there.
- **Keyboard** (MuseScore's shortcuts, all changeable in Preferences): `N` note input; `A`–`G` notes (Shift adds to the chord); `1`–`8` 64th up to breve; `0` rest; `.` dot; `+` tie; `-` / `=` / `\` flat, sharp, natural; `↑`/`↓` a semitone, `Ctrl+↑/↓` an octave, `Alt+↑/↓` a line or space in the key; `Alt+2`–`8` add an interval above; `R` repeat the selection; `Ctrl+Z` / `Ctrl+Y`; `Ctrl+C` / `X` / `V`; `Delete`; `Space` play; `Ctrl+B` add a measure; `Insert` insert one; `Ctrl+Alt+1`–`4` voices; `Ctrl+=` / `Ctrl+-` zoom. Typing letters over a selection starts note input there.
- **Note input preview** (2026-11-19): in note input, a see-through note of the chosen value follows the mouse, with its ledger lines and stem, so you can see where a click will put it. Shift+click adds the note to the chord there.
- **Hearing notes** (2026-11-19): placing, selecting, moving or re-pitching a note plays it (a chord plays all together), in its part's instrument, and only you hear it.
- **Deleting:** `Delete` and `Backspace` both delete.
- **Splitting rests:** choosing a shorter value on a rest splits it into the new rest, then the fewest clean rests for the rest of its time, each starting on a multiple of its length (a quarter on beat 1 of a whole rest leaves a quarter and a half).
- **Over the barline:** a note too long for what's left of its measure (like adding a dot to one at the end) carries on over the barline, tied.

### 9.5 Selecting
Works like MuseScore (2026-11-19).

- Click a note to select it (it plays), or click empty staff for its measure.
- Drag across measures, or Shift+drag a box, to select several. Shift+click extends the selection to whatever you clicked. Ctrl+click picks several separate notes.
- Click a part's name to select its whole staff.
- `Shift+←/→` extends by a note, and `Ctrl+Shift+←/→` to the barline. `Ctrl+←/→` jumps a measure, `Alt+Page Up/Down` moves to the staff above or below, `Ctrl+Home/End` goes to the start or end, and `Ctrl+A` selects everything.
- A range can start and end inside measures. Copy and paste keep every chord's place, and paste overwrites the time it covers.
- **Arrow keys** select the **top note** of each chord by default. Preferences can change that to the whole chord or the bottom note.
- **Every mark can be selected on its own:** dynamics, articulations, ornaments, lyrics, chord symbols, staff text, tempo marks, lines, signs, rehearsal marks and endings. `Delete` removes the selected one.
  - Clicking a dynamic opens a list of all of them, four at a time with ▲/▼ (or the wheel), to change it.

### 9.6 Toolbar, palettes and sidebars
- **Toolbar:** note input (N), note values 64th–breve, dot and double dot, rest, tie, flat/natural/sharp, voices 1–4, tuplets (3), grace notes, undo/redo, play/stop, zoom.
- **Palettes** (left): clefs, key and time signatures, dynamics, articulations, ornaments, lines, text, tempo marks, repeats, endings, jumps, barlines and arpeggios.
  - **Palette search** (`Ctrl+F`, or the box at the top of the palettes) shows only the items whose names match, in every palette at once.
  - **Arpeggios:** arpeggio, arpeggio up and arpeggio down, drawn as a wavy line (with an arrowhead for up and down). They play rolled, each note about 45 ms after the one below (from the top for down), less on short chords. You can select and delete them on their own like other marks.
- **Properties** (right): **Score** (title, subtitle, composer, lyricist, tempo), **Instruments** and **Measures** (insert, append and delete measures).
- **Folding:** the palettes on the left and the sections on the right all fold up when you click their headings.
- **Instruments sidebar:** lists every part as a subtab. The open one shows its name, instrument (✎), volume, pan, mute, solo, re-strike, order and Remove.
  - A new part shows up at the bottom of the list with its subtab open. Clicking in the music follows that part again, and the panel scrolls with the wheel.
- **Adding parts:** "+ Add part…" opens a list of every instrument (the three drums together as a Drum kit). The pencil next to a part's instrument opens the same list to change it.
  - Parts are named after their instrument, and numbered when that one's already used ("Bass 2"). A renamed part keeps its own name.

### 9.7 Lines, text, tuplets, grace notes and endings
- **Lines,** from the Lines palette or keys, over a range (first chord to last in each part) or from one note: slurs (`S`, to the next note), crescendo and diminuendo hairpins (`<`, `>`), 8va, 8vb and 15ma, glissando (to the next note), rit. and accel.
  - Putting the same line on again takes it off. Lines move with their measures when measures get inserted or deleted.
- **Text:** lyrics (`Ctrl+L`: Space or `-` to the next note, Shift+Space back, Enter for the next verse, Backspace in an empty box goes back), chord symbols (`Ctrl+K`, Space to the next chord), staff text (`Ctrl+T`) and rehearsal marks (`Ctrl+M`), also from the Text palette. `Esc` finishes.
- **Tuplets** (`Ctrl+2`–`9`, or the toolbar's 3): the selected note becomes the first of the tuplet and the others rests. In note input the tuplet gets made at the cursor from the chosen value, and notes entered in it take its ratio.
- **Grace notes:** acciaccatura (`/`, or the toolbar) and appoggiatura, at the chord's top note, then selected so you can move them. Click just left of a chord to select its grace note.
- **Endings** 1st to 4th, and end repeats played 2, 3 or 4 times.

### 9.8 Notation it supports (MuseScore-style)
- **Notes and rests:** breve to 64th notes, single and double dots, ties, chords, **up to 4 voices per staff**, **tuplets** of any ratio (triplets, quintuplets…), **grace notes** (acciaccatura, appoggiatura).
- **Pitch:** proper spelling (sharps, flats, naturals, double sharps and flats), courtesy accidentals shown automatically, and transposing a selection by semitone or octave.
- **Clefs:** treble, bass, alto, tenor, treble 8vb, percussion, and clef changes in the middle of a score.
- **Key and time:** all 15 key signatures and key changes, any time signature (common and cut time included) and changes, and a pickup measure.
- **Tempo:** tempo marks and changes, ritardando and accelerando.
- **Dynamics:** ppp–fff, sf, sfz, fp, and crescendo and diminuendo hairpins.
- **Articulations:** staccato, staccatissimo, tenuto, accent, marcato, fermata.
- **Ornaments:** trill, mordent, turn (all played back).
- **Lines:** slurs, 8va / 8vb / 15ma octave lines, glissando.
- **Structure:** repeat barlines, 1st/2nd endings, D.C., D.S., Segno, Coda, To Coda, Fine (all followed in playback), double and final barlines, and inserting, appending and deleting measures.
- **Text:** title, subtitle, composer, lyricist, staff text, rehearsal marks, **lyrics** (several verses) and **chord symbols**.
- **Editing:** select notes or ranges, copy, cut, paste, palettes, a properties panel, navigate by note and by measure, zoom.

### 9.9 Playback
- **Listening:** play from the cursor, and only you hear it (like the Lathe's preview), with a cursor following along. Each part has its own volume, mute and solo in a mixer panel. Metronome and count-in are optional.
- **Playing a selection:** if you play with several notes selected across time, it just plays that stretch.
- **Pitch range:** a note block covers F♯3–F♯5. Scores can go 2 octaves either side by stretching the samples, and notes outside the natural range show orange.
- **Notes last as long as they're written** (changed 2026-11-19, it used to re-strike every beat):
  - **Held:** a pitched note longer than its sample holds its tone like an organ. Right after the attack, a stretch that's a whole number of the note's cycles long gets looped, with its fading level evened out and its join crossfaded. Then it fades out over 60 ms when the note ends.
  - **Cut short:** a note shorter than its sample fades out at its end. Staccato sounds for half its length and staccatissimo a third, and a tie holds one note through the tied notes.
  - **Drums** always play their whole hit, since held notes only count for pitched instruments.
  - **Re-striking every beat** (the old way) is still there, per part (the part's "Re-strike long notes" button) or for every part from Preferences and the client config (`restrikeLongNotes`, off by default).
  - **MIDI files** cut on the Lathe use their notes' lengths the same way. Note Block Studio files don't have any, so they stay hits.
- **Dynamics:** ppp–fff (plus sf, sfz, fp) set the volume, hairpins glide between them, and an accent adds 30% to one note.
- **Drums:** a percussion staff with one line per drum sound (bass drum, snare, hi-hat), no pitch.

### 9.10 Playing together
- Everyone with the desk's editor open sees changes live, and each edit shows up as it's made.
- Everyone sees each other too. Every other editor's selection is outlined, or their note-input caret drawn, in their own color with their name on it, and the status bar lists who else is editing.
- Cursors get sent up to ten times a second, only when they move. Someone who closes the editor, walks away or logs out disappears from everyone else.
- If two people change the same note, the last change wins.
- Undo keeps working while other people edit. Undoing puts your own version back, so the last change wins. Your undo history only gets cleared when someone inserts or deletes measures or adds, removes or reorders parts, since it wouldn't line up anymore.

### 9.11 Instruments
- **What's there:** the vanilla note block instruments, plus any added by resource packs (M10). Each pack sets its instruments' pitch ranges. Missing instruments fall back to harp.
- **Pack instruments** (built 2026-11-19, M10): a resource pack lists its instruments in `assets/<namespace>/minescore/instruments.json`:
  `{"instruments": [{"id": "violin", "name": "Violin", "sound": "mypack:violin", "center": 69, "low": 55, "high": 100}]}`
  - `sound` is a sound event in the pack's `sounds.json`, `center` is the MIDI note it plays at as recorded, `low`–`high` is its range (notes outside it show orange), and `"pitched": false` makes it a drum.
  - They show up in the instrument list after the note block ones, and play, hold and audition the same way. A part keeps its pack instrument by ID, and without that pack it plays as a harp. The list gets read again whenever the editor opens.
- **Choosing instruments** (M10): players pick which instruments are on. "Choose instruments…" in the instrument list switches it to ticking which ones it offers (saved in `config/minescore-editor.json`). A part's own instrument always shows.
- **Fonts** (M10): the notation symbols' layout loads from the resources too (`assets/minescore/score_glyphs.json`), so a resource pack can redraw the atlas *and* lay it out its own way.

### 9.12 Import and export
- **Import** (File → Import…), from the **editor at any time** (M10):
  - A list of the `.mid`, `.midi`, `.nbs` and `.minescore` files in `blkcdsongs/` and `minescore/`, newest first, or Browse… for the system's file dialog.
  - Importing **overwrites** the score, so the editor **offers to make a copy** first: "Copy, then import" (keeps the score how it was as a numbered copy, on a Blank Score from your inventory), "Just import", or Cancel. Everyone editing the score gets the imported one.
  - **MIDI:** notes on the nearest 16th, keeping their lengths. A part per channel and instrument (up to 16), and all the drums in one percussion part (bass drum, snare, hi-hat). Chords go in one voice, each lasting until the next one starts. The first tempo, time and key signature, and later tempo changes as tempo marks. Low parts get a bass clef, and flat keys are spelled with flats.
  - **Note Block Studio:** a tick is a 16th. Each note lasts until its instrument's next note, at most a quarter. A part per instrument, drums together, plus its title, author, tempo and time signature.
  - **MineScore files** come back exactly how they were saved.
  - Notes too long for their measure get tied over the barline. A note that one written value covers (a half on beat 2) stays one note.
- **Export** (File → MIDI, WAV, NBS, MineScore):
  - Written from what playback plays (repeats, ties, ornaments, dynamics as you'd hear them).
  - It goes to `.minecraft/minescore/` by default, or a folder you pick with "Export folder…". Files are named after the title and never overwrite an earlier export.
  - **MIDI:** a channel per instrument with its closest General MIDI sound, drums on channel 10, and the score's tempo, time and key.
  - **NBS:** version 5, a tick per 16th at the score's tempo.
  - **WAV:** 16-bit stereo at 44.1 kHz.
  - **MineScore:** the whole score.

### 9.13 How the editor was built (M9 stages)
1. The two-block desk, Blank Score and Score, scores stored on the server, and the score format with every feature above, played back as note block audio. **Built 2026-11-19.**
2. The editor itself: drawing the score, note input, selection, clipboard, undo, palettes, playback. **Built 2026-11-19.** At this point slurs, hairpins, 8va, glissando and rit./accel. lines, lyric, chord symbol and text entry, tuplets and grace notes from the toolbar (they already saved, played and drew), and more than two endings were still left for stage 3.
3. The rest of the notation (§9.7). **Built 2026-11-19.**
4. Live collaboration (§9.10). **Built 2026-11-19.**
5. The desk as a source (§9.2). **Built 2026-11-19.**

---

## 10. Settings menu and hotkeys
- **Opening it:** Ctrl+M, which you can rebind in vanilla Controls and in the mod's own hotkey tab. It also opens from the Mods list.
- **Style:** like Tweakeroo, with search, presets, and import and export. Operators can change server settings in game.
- **Files** (M11): server settings are in `config/minescore-server.json`, and each player's own are in `config/minescore-client.json`. The old `minescore-common.toml` and `minescore-client.toml` move over by themselves the first time. Both are plain JSON, saved whenever something changes.
- **The menu:** categories down the left, and each setting is a row with its name (yellow once it's changed from the default), the value (on/off, a choice you click through with Shift-click for the one before, or numbers and text you type in that take effect on Enter or clicking away), its unit, and ↺ to go back to the default.
  - Hovering says what it does, its default, and whether it's a server setting.
  - A search box looks through every category at once.
  - In small windows the tabs, value buttons and top buttons shrink to fit. Long names end in "…" before the unit or the ◆ marker, and the legend wraps onto two lines.
- **Server settings** (◆): these are the server's, sent to everyone who joins and again whenever they change. Operators (and whoever's world it is) change them in game and they take effect right away. For everyone else they're locked (◇). From the title screen (the Mods list), the menu changes this computer's own file.
  - Server settings changed in a singleplayer world save properly (fixed 2026-11-20). Before, the menu set them in the same game before the server got the change, so the server didn't see anything new, didn't save, and leaving the world reloaded the old file.
- **Presets:** MineScore's own (Defaults, No power, Light on resources, Big venues, Forgiving speakers), plus any you save from your current settings ("Save current", kept in `config/minescore-presets/`, and deletable). A preset only changes what it holds, and only changes server settings for operators.
- **Import and export:** Export copies every setting to the clipboard and saves `config/minescore-settings-export.json`. Import applies settings from the clipboard.
- **Hotkeys:** Ctrl+M opens the menu. "Invisible wires", "Room Sound Physics" and "Sound through blocks" on/off are unbound until you give them keys (extra hotkeys are always unbound by default). They're all in vanilla Controls (under MineScore) and the menu's Hotkeys tab, where you click one and press keys (with Ctrl, Shift or Alt) to change it, and Delete clears it.
- **What's in each category:**
  - **Power** (server): Energy or None mode, system cap (500), the hub, effect card, Tweeter, Middle, Subwoofer, chain step and Lathe costs (every FE/t value), Create's max RPM (64) and SU per RPM.
  - **Single-speaker mode** (server): all its values, volume and range (60%) and daisy chain loss (15%).
  - **Hearing** (server): speaker range (32) and volume behind a speaker (30%).
  - **Daisy chain** (server): loss past the budget (5%) and the quietest it gets (10%).
  - **Blowouts and distortion** (server): whether speakers can blow (on), seconds over the limit (2), the None-mode restart wait (5 s), and each speaker type's limit (9, 10, 11 dB).
  - **Wires:** invisible wires (each player's own) and the distance warning (server).
  - **Chunk loading** (server): keep playing systems loaded.
  - **Copies and erasing** (server): copying records and copying scores. Erasing stays the furnace recipe, which a data pack can remove.
  - **Redstone** (server): whether Redstone Control is on by default (off).
  - **Audio engine:** Room Sound Physics (§8.3), sound through blocks (§8.2), Vinyl static, the Speed algorithm, and the bass and treble crossovers (200 Hz, 2 kHz).
  - **Score editor:** what the arrow keys select, re-strike long notes, and the editor's shortcuts.
  - **Instruments:** which ones the instrument list offers.
  - **Import/export:** the MIDI import grid (8th, 16th or 32nd) and the export folder (typed or picked).
  - **Server limits** (server): how many parts (16) and measures (2000) a score can have, and the biggest song (64 MB).
  - **Performance:** speakers at once (48), high-quality effects, and **Particles** (added 2026-11-20, a client setting, **off by default**, which turns every MineScore particle on or off).
  - **Accessibility:** Now Playing messages.
  - **Hotkeys.**
  - **Debug** (server): log every system starting and stopping.

## 11. The MineScore Manual
The in-game guidebook (built 2026-11-19 in M12).

- Right-click it to open an open-book screen with an introduction and clickable contents, then nine chapters: Records, Playing Music, Building a System, Speakers, Hubs, Power, Redstone, Writing Music and Settings, each with its item as an icon.
- Arrows, the mouse wheel or the arrow keys turn pages, and Backspace goes back to the contents.
- The text is in `assets/minescore/minescore/manual/<language>.json`, so packs can translate it.

## 12. Recipes (2026-11-20)
The recipes were made in `tools/recipe_maker.py` and exported to `tools/recipes_export.json`. `gen_resources.py` turns that into `data/minescore/recipes/` with a recipe book unlock for each one (once the player has any of the ingredients). A recipe that's just another one flipped left to right is left out, since Minecraft already accepts every shaped recipe mirrored. None of them clash with vanilla or each other.

- **No crafting recipe:** Vinyl Record (cut at the Lathe), Score (from a Blank Score at the Desk), and the Creative Power Source (dev only).
- **Speakers:** Subwoofer = planks around leather. Middle Speaker = wooden slabs in the corners, planks on the sides, leather in the middle. Tweeter = wooden buttons in the corners, wooden slabs on the sides, leather in the middle.
- **Hubs:** each one is two Tweeters over two Middle Speakers and a Preset Chip, over three Subwoofers, with its own top-middle piece: TNT for the Amplifier, a Vinyl Display for the Audio Mixer, and a sticky piston for the Channel Manager. The Multi-Effects Unit is iron nuggets and bars, a slime block, a Preset Chip, a clock and iron blocks.
- **Preset Chip** (×2): copper, gold, redstone and quartz.
- **Effect cards:** a Preset Chip between two of: TNT (Amp & Cabinet), repeaters (Delay), pistons (Modulation), or slime blocks (Reverb). Wool and stone on either side (Drive). A goat horn and any tool, which gets used up, since it's the utility card (Dynamics).
- **Records and players:** Blank Vinyl = four smooth quartz slabs in a diamond, plus the furnace eraser. Disc Cutting Lathe = iron ingots, an iron hoe and iron blocks. Vinyl Player = a wooden button and iron bars over wooden slabs and a copper ingot. Vinyl Display = a carpet over three quartz. MP3 Player = a glass pane over stone buttons and a Preset Chip.
- **Wiring:** Speaker Wire ×4 from three on a diagonal: cut copper (12 ga) or cut copper slabs (16 ga), only the unoxidized kind, waxed or not, or copper ingots (20 ga). Cable Anchor ×4 = an iron ingot between two nuggets, in a row or on a diagonal. Wire Cutters = iron ingots and sticks.
- **Writing:** Blank Score = leather, paper, a feather and an ink sac. Writer's Desk = planks, a wooden slab and a pressure plate in an L. Manual = a book (or a book and quill) with a Blank Score, or with another Manual to copy it.
- **Music Cabinet:** planks, any wooden slabs and a wooden pressure plate.

---

## 13. Art and sound (all original)

All the art is made in code, so it's clean and consistent but not hand-painted, and anything can get redone if it doesn't look right.

### 13.1 Styles
Confirmed 2026-11-19: the default look plus three built-in packs (Modern, Retro and a 16× Minecraft-style one). Built in M12 stage 1.

| Style | Ships as | Resolution | GUIs restyled |
|---|---|---|---|
| **Minecraft-style** | Default | 32× | Yes |
| **Minecraft-style 16×** | Built-in resource pack | 16× | Uses the default GUIs |
| **Retro 80s hi-fi** | Built-in resource pack | 32× | Yes |
| **Modern studio** | Built-in resource pack | 32× | Yes |

- **The packs** show up in vanilla's Resource Packs screen, off by default, and can be stacked over any other pack.
- **Minecraft-style 16×:** the default art at half the resolution. Each 2 × 2 block of pixels turns into its most typical pixel, so it stays crisp instead of blurry.
- **Retro 80s hi-fi:** brushed-silver faces with chrome knobs and screws, rosewood cabinets, teal fluorescent (VFD) displays, amber lamps and warm VU lights, and paper-brown speaker cones. Screens are silver with VFD-teal text and amber highlights.
- **Modern studio:** matte black hardware with flush, screwless panels, pale birch cabinets, white speaker cones, ice-blue and white LEDs, and black OLED-style displays. Screens are flat charcoal with ice-blue highlights.
- **Screens drawn in code** (the score editor, config menu, lists) follow whichever pack is on through a color theme file (`assets/minescore/minescore/theme.json`), which any resource pack can override too.
- **Sounds:** Retro and Studio each have their **own built-in sound pack**. Turning on a texture pack doesn't change the sounds.
- **Total:** 4 texture sets, 3 GUI sets, 3 sound sets.

### 13.2 Blocks
All blocks have slightly rounded edges so each side flows smoothly into the next, and the jacks stick out just a little, with inputs and outputs clearly marked.

| Block | States |
|---|---|
| Disc Cutting Lathe | idle · cutting (its speed follows the amp gauge or Create RPM) · unpowered · overspeed break (the record shatters) · record on the platter with its dye and trim |
| Vinyl Player | the Lathe's shape in walnut brown · empty (bare recessed platter) · record on the platter in its dye and trim · spinning while playing · back jack panel |
| Vinyl Display | half-record plaque · empty · a record standing half out of its slot, in its dye and trim · on a wall or standing on the floor |
| Music Cabinet | walnut with an open front · hollow when empty · cream Score spines filling the top shelf from the left · record sleeves in each record's dye filling the bottom shelf, the record and label showing through a hole · the pointed-at one outlined |
| Audio Mixer | empty · docked (built 2026-11-20: the MP3 Player stands in the dock, its screen lit with power and dark without) · playing · paused · unpowered · underpowered warning · faders and meters |
| Multi-Effects Unit | off · on · each slot empty or holding its card · bypassed slots · display showing the preset · disabled by redstone |
| Channel Manager | off · on · L/R/Mono lights · forced mono · Redstone Control light · jacks |
| Amplifier | off · on · VU meters · clipping · redstone override · Redstone Control on or off · labeled jacks |
| Speakers ×3 | standing and hanging · 4 facings · idle · playing (cone moves with the audio) · Bluetooth · unpowered · shorted (sparks, then pops off) |
| Cable Anchor | input and output ends · on the floor, a wall or the ceiling |
| Writer's Desk | empty · score on the desk · in use · jacks |

- **Powered and unpowered** (built 2026-11-19): the Mixer, Channel Manager, Amplifier and MEU show their usual face while their system has power, and the same face with **every indicator off** when it doesn't. That means lamps and LEDs dark, the Amplifier's VU meter unlit with its needle resting on the left, and the MEU's display blank.
  - A system has power when its source gets at least what its hubs need. A hub that's not in a system is dark, except a standalone Amplifier with its own 50 FE. In None mode they're always lit.
- **Undersides** (2026-11-19): every block has its own bottom texture, in its body's material, with rubber feet and something of its own: a bearing with a shaft socket on the Lathe, heat-sink fins on the Amplifier, a vent grid on the Channel Manager, an access panel on the MEU, a bass port on the Subwoofer, felt strips on the Vinyl Display, and a plinth on the Music Cabinet.
- **No see-through spots** (2026-11-20): every model got checked for faces showing clear pixels.
  - The Lathe's side texture had a clear top half, and the Vinyl Display's stepped plaque had clear corners. Both are solid now.
  - The rounded edges left a thin channel along each edge, open at both ends, that you could see straight through. Each channel got plugged, at first with a wall across the middle. That showed as a stray pixel, so the plugs moved to the two ends of each edge, textured like the face they meet.
  - The speaker pockets reach a pixel past the driver hole all the way around now. The hole's edge pixels used to poke past the old walls, so you could see into the speaker.
  - The only holes left are on purpose, each with something behind it: the speaker drivers (their pockets) and the record sleeves (the record inside).

### 13.3 Items
- **Blank Vinyl.**
- **Vinyl Record:** a tintable dye layer, plus 16 trim pattern overlays using the vanilla trim palettes.
- **MP3 Player:** off and playing.
- **Wire coils ×3.**
- **Wire Cutters.**
- **Effect cards ×6** and the **Preset Chip**.
- **Blank Score.**
- **Score.**
- **Manual.**
- **Held poses.**

### 13.4 Screens and HUDs
- **Cables:** 3 cable textures and plug ends.
- **GUIs:**
  - The Lathe.
  - The MP3 Player, with EQ.
  - The Mixer tab.
  - The Amplifier.
  - The Channel Manager.
  - The speaker.
  - The Multi-Effects Unit (six card panels, chip slot, expression pedal), in a vanilla-style menu.
  - The score editor, including the import and copy prompt.
  - The score viewer.
  - The desk.
  - The config menu.
  - A widget kit.
  - The Manual's pages.
- **HUDs:** now playing, wiring (§5.1), and a power budget readout. Jade and The One Probe show a source's power budget, and the Mixer tab shows it against the demand.

### 13.5 Particles
Built in M12, MineScore's own and drawn in code. They're all off unless the Particles setting is on (§10).

- **Music notes** rise from playing speakers in their band's color (red bass, yellow mid, blue treble), more often the louder they play.
- **Vinyl shavings** curl off the Lathe's cutter while it cuts.
- **Record shards** when a record shatters.
- **Sparks and smoke** when a speaker shorts.

### 13.6 Sounds
Made in code (synthesized), so no recordings are needed. They play at the Blocks volume, at a slightly different pitch each time. Built 2026-11-19 (M12 stage 2).

- **Record shattering** on an over-speed Lathe: a sharp crack, then shards clattering down.
- **Jack plugged in** (starting or finishing a cable) and **unplugged** (cancelling one): a click, the plug's knock and a detent, and a pop coming out.
- **Wire cut** with the Wire Cutters: a metallic snap.
- **Speaker short:** a hard pop and thump, then a fizzing crackle.
- **Needle drop** when the Vinyl Player starts a record: a soft thump and surface crackle.
- **Docking** an MP3 Player in the Mixer: a latch and a seat.
- **Breaking:** the blocks you mine with an axe (Vinyl Player, Writer's Desk, Music Cabinet) break with the wooden sound.
- **Sound packs:** Retro is heavier and warmer, with mechanical clunks, more crackle and a touch of mains hum. Studio is clean, short and precise, with a digital glitch on a short. A pack picks the style with `assets/minescore/minescore/sound_style.json`.

### 13.7 Branding
- **Logo:** a record with a red label next to MINESCORE in gold capitals, shown in the Mods list.
- **Icon, creative tab and advancement icons.**

## 14. Advancements
| Advancement | How to get it |
|---|---|
| Fresh Cut | Cut your first record |
| Bootleg | Copy a record |
| Mixtape | Load 10 songs onto an MP3 Player |
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
| **Overclocked** *(new)* | Break a record by spinning the Lathe over 64 RPM |
| **Power Budget** *(new)* | Run a system that draws 500 FE/t |
| **Pedalboard** *(new)* | Fill all six slots in a Multi-Effects Unit |
| **Slowed + Reverb** *(new)* | Play a song slowed down with reverb on |

Built 2026-11-19 (M12 stage 4):
- They have their own tab on a walnut background, under a MineScore root you get by picking up any MineScore record, wire, score, MP3 Player or the Manual.
- Three branches: records (Fresh Cut first), systems (Plugged In first) and scores (Composer first).
- Things that happen in the world (a short, a shattered record, a system's sound or power) count for every player within 32 blocks. Stereo needs one speaker set to Left and one set to Right.

## 15. Other mods and loaders
- **Code layout:** a shared core, with separate Forge, NeoForge and Fabric modules. Forge 1.20.1 comes first.
- **Animations:** GeckoLib.
- **Energy API:** Forge Energy on Forge and NeoForge, and Team Reborn Energy on Fabric.
- **Create:** an **optional** integration that's only active if Create is installed (§2.4).
- **JEI** (built 2026-11-19, M12 stage 5): a Disc Cutting Lathe category (cut a song, press a score, copy a record, load an MP3 Player) with the Lathe as its catalyst, plus info pages for the Manual, scores and wires.
- **Jade and The One Probe** (same stage): they show a speaker's band, name and channel, the record on a Vinyl Player or Vinyl Display, the Music Cabinet's counts, the score on a Writer's Desk, whether a hub's Redstone Control is on, and a source's power budget.
- All of them are optional and never bundled. The dev game runs with JEI and The One Probe. Jade is compiled against but not run in dev (its own mixins don't apply in the dev environment), so its tooltip hasn't been tried.

## 16. Milestones
| # | Milestone |
|---|---|
| M0 | Multi-loader setup (needs **JDK 17**) and placeholder registrations |
| M1 | Wiring: jacks, cables, anchors, Wire Cutters, rules |
| M2 | Audio engine: decoding, crossover, directional sources, sync, chunk loading |
| M3 | Lathe, records, upload and cache, preview, copies, erasing, dye and trims |
| M4 | MP3 Player: handheld, EQ, playlists, Bluetooth mode, item frame. Mixer. **Vinyl Player** (§3.1): plays Vinyl Records, record shown and spinning, Now Playing |
| M5 | Amplifier (caps, EQ, line amps), Channel Manager, chains, shorts, sound through blocks (§8.2), Room Sound Physics (§8.3) |
| M6 | **Multi-Effects Unit** (§5.5): the hub, olive links, six effect cards, Preset Chips and built-in presets, speed modulation with its algorithms, printing effects onto records. Its power cost came with M7 and its redstone (disable, comparator) with M8 |
| M7 | Power: FE budget, None mode, Create integration. A dev-only Creative Power Source (endless FE) was added first for testing. FE, None mode and the Create kinetic Lathe built 2026-11-19 |
| M8 | Redstone and the Redstone Control toggles. Built 2026-11-19 |
| M9 | Score, Writer's Desk, editor (MuseScore-style, §9), collaboration, Desk as a source. Built in five stages (§9.13), all built 2026-11-19 |
| M10 | Import/export, score-to-record, resource pack instruments and fonts. Built 2026-11-19 (§9.11, §9.12, §2.4) |
| M11 | Config menu, hotkeys, presets. Built 2026-11-19 (§10) |
| M12 | Art in all styles, sound packs, Manual, advancements, JEI category, Jade/TOP, polish. Built 2026-11-20 (stages below) |

**M12 stages** (started 2026-11-19):
1. **Style packs:** the Retro, Modern and 16× built-in resource packs and the screen color theme (§13.1). *Built 2026-11-19.*
2. **Sounds:** MineScore's own synthesized sounds and the Retro and Studio sound packs (§13.6). *Built 2026-11-19.*
3. **Manual:** the in-game guidebook (§11). *Built 2026-11-19.*
4. **Advancements:** the 19 in §14. *Built 2026-11-19.*
5. **JEI and Jade/TOP** (§15). *Built 2026-11-19.*
6. **Polish:** particles (§13.5), 3D speaker drivers (§5.3), the Particles setting (§10), the logo (§13.7), the wiring HUD (§5.1), the config menu in small windows (§10), see-through fixes (§13.2), wooden breaking sounds (§13.6), and the singleplayer config saving fix (§10). *Built 2026-11-19 to 20.*

## 17. Future versions
- **1.1:** a full test on a dedicated server.
- Subwoofer screen shake and vibration particles.
- A page view for the score editor, like printed sheet music (§9.3).

## 18. Decision log (S1–S8, all settled)
- **S1. Over budget:** speakers get powered in chain order, starting nearest the source.
  - Speakers within the budget play at full volume.
  - The first speaker past the budget starts the 5% daisy-chain curve, and the rest carry it on.
- **S2. After a short:** the line **stays open** until a player places a speaker and rewires it. The power cycle (or the 5 s wait in None mode) is needed too.
- **S3. Create plus an Amplifier:**
  - Whichever cut time is **faster** applies.
  - The 64 RPM overspeed break applies when copying too.
  - Loading songs onto an MP3 Player is instant.
- **S4. Line Amplifier caps:**
  - No speaker can go over the first Amplifier's cap.
  - With no main Amplifier, Line Amplifiers cap at 100%.
- **S5. Writer's Desk as a source:** costs 50 FE/t and takes the system's power input. Editing is still free.
- **S6. Standalone Amplifiers:** an Amplifier outside a system takes FE directly. Inside a system, it draws from the source.
- **S7. Vinyl Player jacks** (was the jukebox): 12, 16 and 20 ga outputs on its back panel. The jukebox's four side panels got dropped because the Vinyl Player has a real front.
- **S8. Redstone Control toggle:** **off** by default on newly placed hubs. Configurable.
