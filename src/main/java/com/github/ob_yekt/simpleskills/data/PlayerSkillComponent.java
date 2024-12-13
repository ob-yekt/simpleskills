package com.github.ob_yekt.simpleskills.data;

import org.ladysnake.cca.api.v3.component.Component;

public interface PlayerSkillComponent extends Component {
    int getSkillLevel(String skill);
    void setSkillLevel(String skill, int level);

    void addXp(String skill, int xp); // Add this method
    int getLevel(String skill);      // Add this method if missing

    void addSkillChangeListener(SkillChangeListener listener);

    @FunctionalInterface
    interface SkillChangeListener {
        void onSkillChange(String skillName, int level, int xp);
    }
}