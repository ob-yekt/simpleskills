package com.github.ob_yekt.simpleskills.data;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.ob_yekt.simpleskills.SimpleSkillsComponents;

public class PlayerSkillComponentImpl implements PlayerSkillComponent, AutoSyncedComponent {
    private final PlayerEntity player;
    private final Map<String, Integer> skillLevels = new HashMap<>();
    private final Map<String, Integer> skillXp = new HashMap<>();
    private final List<SkillChangeListener> listeners = new ArrayList<>();

    public PlayerSkillComponentImpl(PlayerEntity player) {
        this.player = player;
    }

    @Override
    public int getSkillLevel(String skill) {
        return skillLevels.getOrDefault(skill, 0);
    }

    @Override
    public void setSkillLevel(String skill, int level) {
        skillLevels.put(skill, level);
        notifyListeners(skill);

        // Synchronize this component to the client
        SimpleSkillsComponents.PLAYER_SKILL.sync(player);
    }

    @Override
    public void addXp(String skill, int xp) {
        int currentXp = skillXp.getOrDefault(skill, 0) + xp;

        // Example leveling-up mechanic: Level up every 100 XP
        if (currentXp >= 100) {
            int levelsGained = currentXp / 100;
            setSkillLevel(skill, getSkillLevel(skill) + levelsGained);
            currentXp %= 100;
        }
        skillXp.put(skill, currentXp);
        notifyListeners(skill);

        // Synchronize this component to the client
        SimpleSkillsComponents.PLAYER_SKILL.sync(player);
    }

    @Override
    public int getLevel(String skill) {
        return skillXp.getOrDefault(skill, 0);
    }

    @Override
    public void addSkillChangeListener(SkillChangeListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(String skill) {
        int level = getSkillLevel(skill);
        int xp = skillXp.getOrDefault(skill, 0);
        for (SkillChangeListener listener : listeners) {
            listener.onSkillChange(skill, level, xp);
        }
    }

    @Override
    public void readFromNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {
        // Clear existing data to avoid conflicts
        skillLevels.clear();
        skillXp.clear();

        // Deserialize skill levels
        NbtCompound levelsTag = nbtCompound.getCompound("SkillLevels");
        for (String key : levelsTag.getKeys()) {
            skillLevels.put(key, levelsTag.getInt(key));
        }

        // Deserialize skill XP
        NbtCompound xpTag = nbtCompound.getCompound("SkillXp");
        for (String key : xpTag.getKeys()) {
            skillXp.put(key, xpTag.getInt(key));
        }
    }

    @Override
    public void writeToNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {
        // Serialize skill levels
        NbtCompound levelsTag = new NbtCompound();
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
            levelsTag.putInt(entry.getKey(), entry.getValue());
        }

        // Serialize skill XP
        NbtCompound xpTag = new NbtCompound();
        for (Map.Entry<String, Integer> entry : skillXp.entrySet()) {
            xpTag.putInt(entry.getKey(), entry.getValue());
        }

        nbtCompound.put("SkillLevels", levelsTag);
        nbtCompound.put("SkillXp", xpTag);
    }
}