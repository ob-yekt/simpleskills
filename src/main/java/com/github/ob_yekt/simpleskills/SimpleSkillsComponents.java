package com.github.ob_yekt.simpleskills;

import com.github.ob_yekt.simpleskills.data.PlayerSkillComponent;
import com.github.ob_yekt.simpleskills.data.PlayerSkillComponentImpl;

import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;

public class SimpleSkillsComponents implements EntityComponentInitializer {
    // Create a unique key for the skill component
    public static final ComponentKey<PlayerSkillComponent> PLAYER_SKILL =
            ComponentRegistry.getOrCreate(
                    Identifier.of(SimpleSkillsMod.MOD_ID, "player_skill"), // Correct constructor usage
                    PlayerSkillComponent.class
            );

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // Attach the component to player entities
        registry.registerFor(PlayerEntity.class, PLAYER_SKILL, PlayerSkillComponentImpl::new);
    }

    public static PlayerSkillComponent getSkillComponent(PlayerEntity player) {
        return PLAYER_SKILL.get(player); // Directly access the component key
    }
}