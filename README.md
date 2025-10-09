# 🌟 **simpleskills**: Add Skills to Minecraft!

Welcome to **simpleskills**, the ultimate server-side Fabric mod that transforms Minecraft into an engaging RPG experience! Whether you're a lone survivor or running a bustling SMP, **simpleskills** adds progression with 15 unique skills, unlockable gear, and competitive leaderboards. Master Woodcutting, Slaying, Alchemy, and more, as you climb to level 99!

**Why **simpleskills**?**
- 🎮 **Seamless Fun**: Works flawlessly out-of-the-box in multiplayer - no client mods needed!
- ⚔️ **Simple Progression**: Unlock powerful tools, armor, and bonuses as you level up.
- 🏆 **Bragging Rights**: Compete on leaderboards and show off your skills!
- 👑 **Prestige System**: Reach max level in all skills to unlock Prestige mode, resetting skills and gaining a Prestige counter next to your name.
- 🛠 **Your World, Your Rules**: Fully customizable to craft the perfect experience for your server.
- 👤 **Singleplayer Support**: Prefer to play on your own? Install **simpleskills** on your client. Toggle the skill HUD with TAB, move it around with J, rebind in options->controls->key binds.

---

## 🎮 What Makes **simpleskills** Awesome?

**simpleskills** brings RPG-style depth to Minecraft with features designed to keep you and your friends hooked:
- **15 Unique Skills**: From Mining to Prayer, each skill offers rewarding progression and tangible benefits.
- **Toggle HUD**: Skills are shown in the **Tab Menu**. Easily toggle the hud with `/simpleskills togglehud`
- **Gear Unlocks**: Earn the right to wield Diamond, Netherite, and more as you level up.
- **Ironman Mode**: Crave a challenge? Test your skills with hardcore restrictions and epic stakes.
- **Leaderboards**: Battle for the top spot in individual skills or overall mastery.
- **Customizable Everything**: Tweak XP rates, unlock tiers, and more with easy JSON configs.
- **Natural Economy**: Players who reach high levels can provide their superior services and goods, such as enchanting, cooking, crafting and more, to lower level players!
- **Feels Like Vanilla+**: The mod is designed to be unintrusive and intuitive without overreach - simple!

Perfect for casual SMPs, lets-players, hardcore survival servers, streamers, or solo players who want a richer Minecraft experience.

---

## 🛠 Skills to Master

**simpleskills** is composed of **15 core skills** that make every action in Minecraft feel rewarding:

1. **Mining**: Break stone and ores to level up and unlock better tools.
2. **Woodcutting**: Chop trees to become a lumberjack legend.
3. **Excavating**: Shovel dirt, sand, and gravel to dig your way to greatness.
4. **Farming**: Till soil, compost, shear/feed/breed animals, and harvest crops to feed your empire.
5. **Fishing**: Reel in fish, treasures, and XP with faster and increasingly better catches as you level.
6. **Defense**: Take hits in armor to equip improved gear.
7. **Slaying**: Slay mobs with melee weapons to become a feared warrior.
8. **Ranged**: Master bows, crossbows, and tridents.
9. **Enchanting**: Enhance your gear with anvil level-locked enchantments like Mending.
10. **Alchemy**: Brew potions with boosted durations as you rise through tiers.
11. **Smithing**: Repair and upgrade gear with unmatched efficiency.
12. **Cooking**: Cook meals that restore more hunger and saturation.
13. **Crafting**: Craft durable tools and armor with a chance to refund materials on most recipes.
14. **Agility**: Run, jump, and swim faster with every level.
15. **Prayer**: Sacrifice rare items for long-term buffs like Haste or Invisibility.

**Which skill will you max out first?**

---

## 🏆 Compete and Conquer with Leaderboards

Nothing fuels fun like a little friendly competition! **simpleskills**’ **built-in leaderboard system** lets you:
- Track your rank in **individual skills** or **total level**.
- Show off your progress with commands like `/simpleskills leaderboard alchemy` or `/simpleskills leaderboardironman total`.
- Cheer on the **top 5 players** in each skill or total level mastery, including those in the Ironman-exclusive leaderboard.
- **The mod doesn't stop at 99!** Leaderboards continue to track XP after hitting max level, and for the true achievers, the Prestige system allows players a choice to reset their skills and gain a Prestige Counter signifying their prowess.
- Leaderboards take into account Prestige levels.

Leaderboards create a vibrant “endgame” where players strive to claim and retain the #1 spot.

---

## ⚔️ Ironman Mode: The Ultimate Challenge

Think you’ve got what it takes to be a legend? **Ironman Mode** makes the game a true challenge without affecting your friends:
- **High Stakes**: Lose *everything* on death, including items and XP.
- **Admin-customizable Hardcore Tweaks**: Reduced HP (-50%) and XP gain (0.2x)
- **Bragging Rights**: Red skull icon shows everyone you’re embarking on an Ironman journey.
- **Death Announcements**: Notify the server of your total level if you fall.

Perfect for players who crave a brutal, rewarding challenge or want to stand out in their community.

---

## Quick Customization

1. Too slow? Too fast? Adjust `standard_xp_multiplier` and `ironman_xp_multiplier` in **config.json**!

2. Reload config changes with `/simpleskills reload` (no restart necessary)

---

## 🔓 Gear Progression: Earn Your Power

Unlock tools and armor as you level up, with **fully customizable** unlock tiers:

| Armor Material | Defense Level |  
|----------------|---------------|  
| Leather        | 0             |  
| Gold           | 10            |  
| Copper         | 25            |  
| Turtle Helmet  | 30            |  
| Chainmail      | 35            |  
| Iron           | 50            |  
| Diamond        | 75            |  
| Netherite      | 99            |  


| Tool Material | Required Level |  
|---------------|----------------|  
| Wood          | 0              |  
| Gold          | 5              |  
| Stone         | 10             |  
| Copper        | 25             |
| Iron          | 50             |  
| Diamond       | 75             |  
| Netherite     | 99             |  

### NOTE!: _simpleskills_ allows the Copper Pickaxe to mine the same ores as an [Iron Pickaxe](https://minecraft.wiki/w/Tiers#Mining_level).

| Weapon   | Required Level |  
|----------|----------------|  
| Mace     | 50             |  
| Crossbow | 0              |  
| Bow      | 30             |  
| Trident  | 99             |  

**Elytra requires level 50 Prayer by default.**

Affected items comes with **lore text** (e.g., *“Requires level 50 Defense”*), keeping things intuitive for players.

---

## 🎣 Skill Highlights: Your Path to Mastery

Here’s a taste of what each skill offers:

### Fishing
- **Faster Catches**: +0.5% speed per level (up to +49.5% at 99).
- **Custom Loot Tables**: Unlock better treasures as you level:

| Tier               | Fish | Junk | Treasure                   |  
|--------------------|------|------|----------------------------|  
| Novice (1–24)      | 80%  | 20%  | None                       |  
| Journeyman (25–49) | 80%  | 15%  | 5% (Bows, Rods, Saddles)   |  
| Artisan (50–74)    | 80%  | 13%  | 7% (Enchanted Gear)        |  
| Expert (75–98)     | 85%  | 5%   | 10% (Books, Tridents)      |  
| Grandmaster (99)   | 79%  | 1%   | 15% (Rare Enchanted Items) |

### NOTICE!
**If you use a fishing mod that changes loot tables, set "custom_fishing_loot_enabled" to false in config.json and restart the server.**

---

### Enchanting
Lock powerful enchantments from being used at an anvil to specific levels, defaulted to:
- Fortune III: Level 25
- Sharpness V: Level 50
- Power V: Level 50
- Efficiency V: Level 75
- Mending: Level 99

No more rushing and cycling trades for Mending villagers; you have to earn it! You can also offer your enchanting services to other players, creating an economy.

---

### Cooking
Players are rewarded with higher multipliers to saturation and hunger for foods created based on the players' Cooking level.

Default (customizable):
- Novice (1–24): 0.875x multiplier to saturation and hunger from cooked foods
- Journeyman (25–49): 1x multiplier to saturation and hunger from cooked foods
- Artisan (50–74): 1.125x multiplier to saturation and hunger from cooked foods
- Expert (75–98): 1.25x multiplier to saturation and hunger from cooked foods
- Grandmaster (99): 1.5x multiplier to saturation and hunger from cooked foods

The customizable Cooking XP table is also designed to encourage other food types, not just the usual porkchops, mutton, and beef.

| Item                | XP  |
|---------------------|-----|
| Cooked Porkchop     | 180 |
| Cooked Beef         | 180 |
| Cooked Mutton       | 180 |
| Cooked Chicken      | 225 |
| Cooked Salmon       | 150 |
| Cooked Cod          | 150 |
| Cooked Rabbit       | 285 |
| Baked Potato        | 130 |
| Golden Carrot       | 230 |
| Golden Apple        | 450 |
| Bread               | 130 |
| Cookie              | 40  |
| Cake                | 750 |
| Pumpkin Pie         | 350 |
| Mushroom Stew       | 285 |
| Beetroot Soup       | 285 |
| Rabbit Stew         | 350 |

---

### Alchemy
Brew potions with boosted durations, defaulted to:
- Novice (1–24): 1x duration
- Journeyman (25–49): 1.25x duration
- Artisan (50–74): 1.50x duration
- Expert (75–98): 1.75x duration
- Grandmaster (99): 3x duration

---

## Farming

* Gain XP from breeding and feeding animals, shearing sheep, and harvesting crops. Gain bonus drops on mature crops based on your Farming level (1% chance per level, up to 99%).
* Farming unlocks the ability to use higher tiers of Hoe tools.
* Players also gain 1% per level of farming to grant an **extra drop** when harvesting crops, to a max of 99% at level 99.

### XP Sources

**Animals:**
- Breeding animals: **250 XP**
- Feeding baby animals: **25 XP** (per food item)
- Shearing sheep: **150 XP**

**Crops** (when fully mature):
- Wheat: **275 XP**
- Carrots: **275 XP**
- Potatoes: **300 XP**
- Beetroots: **250 XP**
- Melons: **100 XP**
- Nether Wart: **350 XP**
- Cocoa: **250 XP**

You can customize these XP values in `farming_xp.json`.

---

### Crafting
Gain XP by crafting tools and armor, as well as smelting ores.

Tools and armor crafted by players enjoy boosts to durability based on Crafting skill tier. All crafted items are signed by the player and their rank, signifying the extent of the durability increase. Crafting increases your chance to get recover ingredients on successful craft of (almost) any recipe.
- Novice (1–24): 0% durability bonus, 0% recovery chance
- Journeyman (25–49): 5% durability bonus, 2.5% recovery chance
- Artisan (50–74): 10% durability bonus, 5% recovery chance
- Expert (75–98): 15% durability bonus, 7.5% recovery chance
- Grandmaster (99): 25% durability bonus, 15% recovery chance

---

### Smithing
Tools and armor are repaired at higher rates. All upgraded Netherite items are signed by the player and their rank, signifying the extent of the durability increase. 

Anvils no longer takes damage from repairing.

The Smithing bonus stacks with the Crafting bonus: a Grandmaster Crafted Pickaxe upgraded by a Grandmaster Smith will have the highest durability.
- Novice (1–24): 0% durability bonus for gear diamond upgraded to Netherite
- Journeyman (25–49): 5% durability bonus for diamond gear upgraded to Netherite
- Artisan (50–74): 7.5% durability bonus for diamond gear upgraded to Netherite
- Expert (75–98): 10% durability bonus for diamond gear upgraded to Netherite
- Grandmaster (99): 20% durability bonus for diamond gear upgraded to Netherite

---

### Prayer
Sacrifice rare items for powerful buffs.

Fully customizable: create your own sacrifices and effects!

| Item                      | Level | XP      | Effect              | Duration | Name                          | Effect Level |
|---------------------------|-------|---------|---------------------|----------|-------------------------------|--------------|
| **Tier 1: Novice (1h)**   |       |         |                     |          |                               |              |
| 🐇 Rabbit Foot            | 0     | 10 000  | Luck                | 1h       | Prayer I: Luck                | 1            |
| 🌼 Spore Blossom          | 0     | 24 000  | Absorption          | 1h       | Prayer I: Absorption          | 3            |
| ✨ Glow Ink Sac           | 0     | 8 000   | Dolphins grace      | 1h       | Prayer I: Dolphin's Grace     | 1            |
| **Tier 2: Journeyman (2h)** |     |         |                     |          |                               |              |
| 🦇 Phantom Membrane       | 25    | 16 000  | Slow falling        | 2h       | Prayer III: Slow Falling      | 1            |
| 🍎 Golden Apple           | 25    | 20 000  | Health boost        | 2h       | Prayer II: Health Boost       | 1            |
| 🐚 Nautilus Shell         | 25    | 9 000   | Water breathing     | 2h       | Prayer II: Water Breathing    | 1            |
| **Tier 3: Expert (3h)**   |       |         |                     |          |                               |              |
| 🌊 Heart of the Sea       | 50    | 20 000  | Conduit power       | 3h       | Prayer II: Conduit Power      | 1            |
| 💎 Diamond                | 50    | 25 000  | Speed               | 3h       | Prayer II: Speed II           | 2            |
| 🐐 Goat Horn              | 50    | 35 000  | Jump boost          | 3h       | Prayer III: Jump Boost II     | 2            |
| **Tier 4: Artisan (4h)**  |       |         |                     |          |                               |              |
| 🌿 Pitcher Plant          | 75    | 30 000  | Strength            | 4h       | Prayer IV: Strength II        | 2            |
| 🏆 Enchanted Golden Apple | 75    | 80 000  | Hero of the Village | 4h   | Prayer IV: Hero of the Village | 1            |
| ☠️ Wither Skeleton Skull  | 75    | 60 000  | Fire Resistance     | 4h       | Prayer IV: Fire Resistance    | 1            |
| **Tier 5: Grandmaster (8h)** |    |         |                     |          |                               |              |
| 🏵️ Torchflower           | 99    | 60 000  | Night Vision        | 8h       | Prayer V: Night Vision        | 1            |
| 💀 Totem of Undying       | 99    | 95 000  | Invisibility        | 8h       | Prayer V: Invisibility        | 1            |
| ⭐ Nether Star            | 99    | 170 000 | Haste               | 8h       | Prayer V: Haste II            | 2            |

XP is awarded to the *Prayer* skill on sacrifice. Higher-level sacrifices require the corresponding Prayer level.

### Elytra unlocks at level 50 Prayer by default. Adjustable in `armor_requirements.json`

---

## 💻 Commands: Take Control

**simpleskills** gives your players and admins full control with intuitive commands:
- `/simpleskills togglehud`: Show or hide the skills HUD.
- `/simpleskills ironman enable`: Embrace the Ironman challenge.
- `/simpleskills reset`: Set all skills to 0.
- `/simpleskills leaderboard <skill|total>`: Check the top 5 players.
- `/simpleskills leaderboardironman <skill|total>`: Check the top 5 ironman players.
- `/simpleskills query <username> <skill|total>`: See anyone’s progress.
- **Admin Commands**:
  - `/simpleskills reload`: Update configs without restarting.
  - `/simpleskills reset <username>`: Wipe a player’s skills.
  - `/simpleskills addxp <username> <skill> <amount>`: Directly add skill XP.
  - `/simpleskills setlevel <username> <skill> <level>`: Set skill levels.

---

## ⚙️ Make It Yours: Total Customization

**simpleskills** is built for *your* vision. Tweak every detail with JSON configs:
- Adjust **XP rates** and **unlock levels**.
- Customize **loot tables**, **prayer sacrifices**, and **Ironman difficulty**.
- Create a casual RPG vibe or a hardcore grind fest—your choice!

Configs live in `server_root\config\simpleskills`, making it easy to craft the perfect experience for your community.

---

## 🚀 Get Started Today!

1. **Download** **simpleskills**: Grab it from [Modrinth](https://modrinth.com/mod/simpleskills).
2. **Install**: Drop it into your server’s `mods` folder, or `\AppData\Roaming\.minecraft\mods` for singleplayer ([Fabric API](https://modrinth.com/mod/fabric-api) required for either option).
3. **Customize**: Tweak configs to match your server’s vibe, or just try it out-of-the-box.
4. **Play**: Jump in and start leveling up!

**Questions?** You can message me on Discord: **obyekt**, or check out the [GitHub](https://github.com/ob-yekt/simpleskills).

### I highly recommend checking out my compatible mod 'simpleqol' on [Modrinth](https://modrinth.com/mod/simpleqol) or [GitHub](https://github.com/ob-yekt/simpleqol)!

## Level up your world with simpleskills.