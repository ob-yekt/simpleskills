# SimpleSkills Mod for Minecraft 1.21.4

SimpleSkills is a **Fabric mod** that prolongs your Minecraft experience with a familiar RPG-style skilling system. Master skills such as `Woodcutting`, `Magic`, or `Slaying` by leveling up through gameplay. Unlock tools, armor, and weapons as you progress, giving you a sense of achievement with every milestone.

This mod is performance-friendly, **does not utilize server ticks or client ticks**, and is lightweight. SimpleSkills works in both **multiplayer and singleplayer**. Installation is **not** necessary to join a server running SimpleSkills.

---

## Features

- 📜 **Skill Progression**: Earn XP in skills including `Defense`, `Woodcutting`, `Mining`, `Excavating`, `Slaying`, and `Magic`.
- ⚔ **Tool, Weapon, and Armor Requirements**: Tiered system based on skill levels to equip items and blocks.
- 🔮 **Magic Skill**: Learn brewing, enchanting, and enhance potion durations as you increase your Magic level!
- 🎮 **Customizable Requirements**: Edit JSON files to easily tweak skill requirements or progression for your server.
- 🧪 **Magic System**: Enhance potion effects and unlock utility blocks like the Anvil, Brewing Stand, and Enchanting Table at specific Magic levels.
- 🔋 **Performance Optimization**: Doesn't use server or client ticks, ensuring minimal overhead.

---

## Skill Requirements

Below are the **default skill requirements** for equipping armor, tools, weapons, and unlocking blocks. These requirements are fully customizable via JSON files to suit your server or singleplayer needs (details on file locations below).

### **Armor Requirements**
| Armor                      | Skill      | Level |
|----------------------------|------------|-------|
| Leather Armor              | Defense    | 0     |
| Golden Armor               | Defense    | 10    |
| Chainmail Armor            | Defense    | 13    |
| Turtle Shell Helmet        | Defense    | 15    |
| Iron Armor                 | Defense    | 25    |
| Diamond Armor              | Defense    | 45    |
| Netherite Armor            | Defense    | 65    |

---

### **Tool Requirements**
| Tool                       | Skill         | Level |
|----------------------------|---------------|-------|
| Wooden Pickaxe             | Mining        | 0     |
| Stone Pickaxe              | Mining        | 10    |
| Iron Pickaxe               | Mining        | 20    |
| Diamond Pickaxe            | Mining        | 45    |
| Netherite Pickaxe          | Mining        | 65    |
| Wooden Axe                 | Woodcutting   | 0     |
| Stone Axe                  | Woodcutting   | 10    |
| Iron Axe                   | Woodcutting   | 20    |
| Diamond Axe                | Woodcutting   | 45    |
| Netherite Axe              | Woodcutting   | 65    |
| Wooden Shovel              | Excavating    | 0     |
| Stone Shovel               | Excavating    | 10    |
| Iron Shovel                | Excavating    | 20    |
| Diamond Shovel             | Excavating    | 45    |
| Netherite Shovel           | Excavating    | 65    |

---

### **Weapon Requirements**
| Weapon                     | Skill     | Level |
|----------------------------|-----------|-------|
| Wooden Axe/Sword           | Slaying   | 0     |
| Stone Axe/Sword            | Slaying   | 10    |
| Golden Axe/Sword           | Slaying   | 12    |
| Iron Axe/Sword             | Slaying   | 20    |
| Diamond Axe/Sword          | Slaying   | 45    |
| Netherite Axe/Sword        | Slaying   | 65    |
| Bow                        | Slaying   | 12    |
| Mace                       | Slaying   | 35    |

---

### **Blocks and Magic Unlocks**
| Block                     | Skill   | Level |
|---------------------------|---------|-------|
| Brewing Stand             | Magic   | 10    |
| Enchanting Table          | Magic   | 35    |
| Anvil (all types)         | Magic   | 65    |

---

### Magic Skill: Potion Durations and Unlocks

The **Magic skill** allows players to enhance their gameplay through positive potion effects and unlock useful blocks.

- **Duration Increase**: The maximum duration of positive potion effects applied to the player is increased based on their Magic level. Players can drink multiple level I potions to extend durations in minutes equal to their Magic level.
- **Affected Potions (Level I Only)**:
   - Potion of Fire Resistance
   - Potion of Strength
   - Potion of Swiftness
   - Potion of Night Vision
   - Potion of Invisibility
   - Potion of Water Breathing
   - Potion of Leaping

#### Magic Level Milestones:
| Level | Unlockable        |
|-------|-------------------|
| 10    | Brewing Stand     |
| 35    | Enchanting Table  |
| 65    | Anvil             |

**Limits**:
- **Minimum Duration**: 1 minute (level 0).
- **Maximum Duration**: 60 minutes (level 60).

---

## Installation

1. **Download the Mod**
2. **Install Fabric**:
   - Make sure you have the **Fabric Loader** installed.
   - Follow the steps on the [official Fabric website]().

3. **Add the Mod**:
   - Place the `simpleskills` mod `.jar` file into your Minecraft Fabric server or `.minecraft` `mods` folder.

---

## Customization

Customize all skill requirements in JSON configuration files generated upon the mod's first run. You’ll find these files in the `/mods/simpleskills/` folder.

| File Name                               | Purpose                                                            |
|-----------------------------------------|--------------------------------------------------------------------|
| `simpleskills_tool_requirements.json`   | Define skill requirements for tools.                               |
| `simpleskills_weapon_requirements.json` | Define skill requirements for weapons.                             |
| `simpleskills_armor_requirements.json`  | Define skill requirements for armor.                               |
| `simpleskills_magic_requirements.json`  | Define skill requirements for magic unlocks (e.g., brewing stand). |

### Example Entry:
```json
{
  "minecraft:diamond_pickaxe": { "skill": "Mining", "level": 45 }
}
```

These values are **easy to edit** with a text editor, allowing you to set custom requirements that align perfectly with your server or gameplay preferences.

---

## Commands

SimpleSkills provides a set of easy-to-use commands for server admins:

- **Add XP**: `/simpleskills xp add <player> <skill> <amount>`
- **Set Level**: `/simpleskills level set <player> <skill> <level>`
- **Query Skills**: `/simpleskills query <player> <skill>`

Commands require a permission level of `/op` or `/level 2`.

---

## Performance and Uninstalling

### Performance-Friendly:
SimpleSkills is optimized and **does not utilize server ticks or client ticks**, ensuring no performance issues even on large servers.

### Easy to uninstall:
1. Remove the `.jar` file from your `mods` folder.
2. Delete the `simpleskills` configuration folder.

---

## Technical Overview

- **Fabric API Required**: Lightweight and built specifically for Fabric.
- **Mixin Architecture**: Smooth integration with Minecraft with minimal performance impact.
- **SQLite Database**: Ensures efficient skill data storage and retrieval for every player.

---
#### AI Disclaimer: SimpleSkills has been primarily generated with openai-gpt-4o.