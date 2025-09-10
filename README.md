# simpleskills

**Simpleskills** is a Fabric mod introducing a familiar RPG-style skilling system to Minecraft, enhancing gameplay with meaningful progression.  
Master skills like Woodcutting, Alchemy, Slaying, and more. Unlock tools, armor, weapons, and bonuses as you level up, creating a rewarding sense of achievement.

- 🎮 Works in **multiplayer and singleplayer**.
- 🌐 **Server-side only** — players do not need to install the mod to join.
- ⚔️ Designed for a **multiplayer experience** with long-term progression.
- 🧾 Detailed commands that allow for adjusting configs without restarts and other admin functions.
- ⚙️ **Highly configurable** with JSON config files to adjust XP, unlock tiers, prayer sacrifices, and more.

---

## 📜 Features

- **15 core skills** with meaningful progression.
- **Unlock tiers** for tools & armor.
- **Lore text** on items shows requirements (e.g., *“Requires level 50 Smithing”*).
- **Prayer system** with sacrifices for long-term buffs.
- **Adjusted XP equations** for balanced leveling.
- **Ironman mode** for hardcore players.
- **Extensive config options** — every skill, XP value, and unlock can be customized.

---

## 🏆 Leaderboards

Compete with your friends and other players!  
Simpleskills includes a **built-in leaderboard system** that tracks both **individual skills** and **total level** across the entire server.

- Accessible with `/simpleskills leaderboard <skill|total>`
- Shows the **top 5 players** per skill or total level.

Example commands:
- `/simpleskills leaderboard mining` → Top 5 miners on the server.
- `/simpleskills leaderboard total` → Overall top 5 players by combined level.

Leaderboards encourage **friendly competition** and make long-term progression feel rewarding.  
Perfect for survival servers, SMPs, and communities that want a clear “**endgame**” challenge.

---

## ⚔️ Ironman Mode
A hardcore playstyle with:
- Death announcements with total level reached of the dead player (configurable).
- Reduced HP (configurable).
- Reduced XP gain (configurable).
- On death, lose:
  - All vanilla XP.
  - All simpleskills XP.
  - All items.

---

## 🔓 Default Unlock Tiers for Armor and Tools

| Material         | Required Level |
|------------------|----------------|
| Leather / Wood   | 0              |
| Gold             | 5              |
| Copper           | 25             |
| Chainmail (armor)| 35             |
| Iron             | 50             |
| Diamond          | 75             |
| Netherite        | 99             |

---

## 🛠 Skills Overview

### Mining
- XP gained from **breaking stone & ores**.

### Woodcutting
- XP gained from **chopping logs**.

### Excavating
- XP gained from **shoveling dirt, sand, gravel, etc.**

### Farming
- XP gained from:
  - **Tilling soil**
  - **Harvesting mature crops**

### Fishing
- **+0.5% faster catch per level** (up to +49.5% at 99).
- Custom **loot tables** by tier:

| Tier        | Fish | Junk | Treasure                |
|-------------|------|------|-------------------------|
| Novice      | 80%  | 20%  | 0%                      |
| Journeyman  | 80%  | 15%  | 5% (bows, rods, saddles, shells, tags) |
| Artisan     | 80%  | 13%  | 7% (enchanted bows/rods, saddles, shells, tags) |
| Expert      | 85%  | 5%   | 10% (enchanted books, bows, rods, saddles, shells, tags) |
| Grandmaster | 79%  | 1%   | 15% (enchanted trident, books, bows, rods, saddles, shells, tags) |

---

### Defense
- XP gained from **taking damage** while wearing armor.

### Slaying
- XP gained from **killing mobs** with melee weapons.

### Ranged
- XP gained from **bow/crossbow combat**.

### Enchanting
- XP gained from:
  - Using the **enchanting table**.
  - Combining books/items at the **anvil**.

### Alchemy
- Potion **duration bonuses** by tier:
  - Novice (1–24): ×1.00
  - Journeyman (25–49): ×1.25
  - Artisan (50–74): ×1.50
  - Expert (75–98): ×1.75
  - Grandmaster (99): ×2.00

- XP gained from **brewing potions**.

### Smithing
**Repairs**
- Repairs cost **no materials**, efficiency scales with level:
  - Novice: 20–30%
  - Journeyman: 35–45%
  - Artisan: 50–60%
  - Expert: 65–75%
  - Grandmaster: 100%

**Upgrades**
- Diamond → Netherite upgrades grant **extra durability**, stacking with Crafting bonuses.
  - Novice: ×1.00
  - Journeyman: ×1.05
  - Artisan: ×1.10
  - Expert: ×1.15
  - Grandmaster: ×1.20

### Cooking
- Food saturation & hunger restored scale by tier:
  - Novice: ×0.875
  - Journeyman: ×1.00
  - Artisan: ×1.125
  - Expert: ×1.25
  - Grandmaster: ×1.375

- XP gained from **cooking foods in furnaces/cookers**.

### Crafting
- Crafting skill improves durability & may **refund ingredients**:
  - Novice: 0% refund, normal durability
  - Journeyman: 5% refund, +5% durability
  - Artisan: 10% refund, +10% durability
  - Expert: 15% refund, +15% durability
  - Grandmaster: 25% refund, +20% durability

- XP gained from crafting **tools & armor (durability items)**.

### Agility
- Grants **+0.1% movement speed per level** (max +9.9% at 99).
- XP gained from **running, jumping, falling, swimming**.

### Prayer
Sacrifice valuable items by right clicking a lit candle for **long-term buffs**.

#### 📖 Default Sacrifice List

| Item                          | Level | Duration | Buff              | Effect Level | Name                      |
|-------------------------------|-------|----------|------------------|--------------|---------------------------|
| 🐇 Rabbit Foot                | 0     | 2h       | Luck             | I            | Prayer I: Luck            |
| 🌸 Lily of the Valley         | 0     | 2h       | Absorption       | I            | Prayer I: Absorption      |
| ✨ Glow Ink Sac               | 0     | 2h       | Dolphin’s Grace  | I            | Prayer I: Dolphin’s Grace |
| 🌊 Heart of the Sea           | 50    | 4h       | Conduit Power    | I            | Prayer III: Conduit Power |
| 🍏 Golden Apple               | 50    | 4h       | Health Boost     | I            | Prayer III: Health Boost  |
| 🐚 Nautilus Shell             | 25    | 4h       | Water Breathing  | I            | Prayer II: Water Breathing|
| 💧 Ghast Tear                 | 50    | 6h       | Slow Falling     | I            | Prayer III: Slow Falling  |
| 💎 Diamond                    | 25    | 6h       | Speed            | II           | Prayer II: Speed          |
| 🐐 Goat Horn                  | 25    | 6h       | Jump Boost       | II           | Prayer II: Jump Boost     |
| 🦇 Phantom Membrane           | 75    | 8h       | Strength         | II           | Prayer IV: Strength       |
| 🎼 Echo Shard                 | 75    | 8h       | Resistance       | II           | Prayer IV: Resistance     |
| ☠️ Wither Skeleton Skull      | 75    | 8h       | Fire Resistance  | I            | Prayer IV: Fire Resistance|
| 🍎 Enchanted Golden Apple     | 99    | 12h      | Night Vision     | I            | Prayer V: Night Vision    |
| 🏆 Totem of Undying           | 99    | 12h      | Invisibility     | I            | Prayer V: Invisibility    |
| ⭐ Nether Star                 | 99    | 12h      | Haste            | II           | Prayer V: Haste II        |

---

## 💻 Commands

The `/simpleskills` root command provides multiple subcommands:

- `/simpleskills togglehud` → Toggle the skills HUD visibility.
- `/simpleskills ironman enable` → Enable Ironman mode until death.
- `/simpleskills query <username> <skill|total>` → Query a skill level or total level.
- `/simpleskills leaderboard <skill|total>` → Show top 5 leaderboard for a skill or total levels.
- `/simpleskills ironman disable` → Disable Ironman mode (admin only).
- `/simpleskills reload` → Reload configs (admin only).
- `/simpleskills reset <username>` → Reset all skills (resetting other players is admin only).
- `/simpleskills addxp <username> <skill> <amount>` → Add XP to a skill (admin only).
- `/simpleskills setlevel <username> <skill> <level>` → Set a player’s skill level (admin only).


---

## ⚙️ Customization
Simpleskills is built to be **fully customizable**.

- All XP values, unlock levels, loot tables, and prayer sacrifices are stored in **JSON config files** located in 'server_root'\config\simpleskills.
- Server owners can **rebalance progression**, adjust skill unlocks, or even add/remove sacrifices.
- Ironman mode difficulty is adjustable.

This makes the mod flexible for both casual RPG servers and hardcore long-term survival worlds.

---

## 🔧 Other Changes
- Removed **Classes & Perks** system (will be split into optional mod in the future).
- Removed all attribute bonuses **except Agility (movement speed)**.
- Item **lore text** now shows requirements (e.g., *“Requires level 50 Smithing”*).
