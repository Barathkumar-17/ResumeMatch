package com.resumematch.model;

import java.util.Locale;

/**
 * A registered user account. Maps to one row of the {@code users} table.
 * The password is never held in plain text, only its salted hash (see {@code PasswordUtil}).
 *
 * <p>Demonstrates: encapsulation (private fields with getters and setters), POJO design.</p>
 */
public class User {

    private int id;
    private String name;
    private String email;
    private String passwordHash;

    public User() {
    }

    public User(int id, String name, String email, String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
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

    /** The first letter of the name in capitals, shown in the sidebar avatar; "?" when there is no name. */
    public String getInitial() {
        String trimmed = (name == null) ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        return trimmed.substring(0, end).toUpperCase(Locale.ROOT);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** The password hash is left out on purpose so it never ends up in a log. */
    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" + email + "'}";
    }
}
