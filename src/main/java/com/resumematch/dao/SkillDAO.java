package com.resumematch.dao;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.resumematch.exception.DatabaseConnectionException;
import com.resumematch.model.Skill;
import com.resumematch.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All SQL for the {@code skills} table, which is the skill dictionary used for
 * rule-based text matching. Loads every skill with its aliases, and turns a set of
 * skill names back into their ids so a resume's skills can be stored.
 *
 * <p>Demonstrates: JDBC, {@code PreparedStatement} (including a generated list of
 * {@code ?} placeholders for an IN clause), try-with-resources,
 * {@code ArrayList}, {@code HashSet}, {@code String.split}.</p>
 */
public class SkillDAO {

    private static final Logger LOGGER = Logger.getLogger(SkillDAO.class.getName());

    private static final String SELECT_ALL =
            "SELECT id, skill_name, category, aliases FROM skills ORDER BY id";
    private static final String SELECT_IDS_BY_NAMES_PREFIX =
            "SELECT id FROM skills WHERE skill_name IN (";

    /**
     * Loads the whole skill dictionary.
     *
     * @return every skill, aliases split from the comma-separated column and trimmed
     * @throws DatabaseConnectionException if the query fails
     */
    public List<Skill> findAll() throws DatabaseConnectionException {
        List<Skill> skills = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                skills.add(new Skill(rs.getInt("id"), rs.getString("skill_name"),
                        rs.getString("category"), splitAliases(rs.getString("aliases"))));
            }
            return skills;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findAll failed", e);
            throw new DatabaseConnectionException(e);
        }
    }

    /**
     * Finds the ids of the given skill names. Only the number of {@code ?} marks is
     * built from the input size; the names themselves are bound as parameters.
     *
     * @param names canonical skill names (as stored in {@code skill_name})
     * @return the ids of the names that exist; empty when {@code names} is null or empty
     * @throws DatabaseConnectionException if the query fails
     */
    public Set<Integer> findIdsByNames(Set<String> names) throws DatabaseConnectionException {
        Set<Integer> ids = new HashSet<>();
        if (names == null || names.isEmpty()) {
            return ids;
        }
        StringBuilder sql = new StringBuilder(SELECT_IDS_BY_NAMES_PREFIX);
        for (int i = 0; i < names.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(')');

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int index = 1;
            for (String name : names) {
                ps.setString(index++, name);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                }
            }
            return ids;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findIdsByNames failed", e);
            throw new DatabaseConnectionException(e);
        }
    }

    /** Splits the comma-separated aliases column into a trimmed list, skipping blanks. */
    private static List<String> splitAliases(String column) {
        List<String> aliases = new ArrayList<>();
        if (column == null || column.isBlank()) {
            return aliases;
        }
        for (String part : column.split(",")) {
            String alias = part.trim();
            if (!alias.isEmpty()) {
                aliases.add(alias);
            }
        }
        return aliases;
    }
}
