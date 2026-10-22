package com.resumematch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of the skill dictionary (the {@code skills} table). Besides the canonical
 * name it carries the other spellings (aliases) that count as the same skill, for
 * example "CPP" for "C++".
 *
 * <p>Demonstrates: encapsulation, {@code List<String>} / {@code ArrayList}.</p>
 */
public class Skill {

    private int id;
    private String name;
    private String category;
    private List<String> aliases = new ArrayList<>();

    public Skill() {
    }

    public Skill(int id, String name, String category, List<String> aliases) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.aliases = (aliases == null) ? new ArrayList<>() : aliases;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Same as {@link #getName()}; named after the {@code skill_name} column. */
    public String getSkillName() {
        return name;
    }

    /** Same as {@link #setName(String)}; named after the {@code skill_name} column. */
    public void setSkillName(String skillName) {
        this.name = skillName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /** Never null; an empty list when the skill has no other spellings. */
    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = (aliases == null) ? new ArrayList<>() : aliases;
    }

    @Override
    public String toString() {
        return "Skill{id=" + id + ", name='" + name + "', category='" + category
                + "', aliases=" + aliases + "}";
    }
}
