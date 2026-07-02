package com.spendwise.transaction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code categories} is a shared, unowned lookup table (docs/database.md) — no RLS policy exists
 * for it (unlike every user-owned table), so no {@link com.spendwise.common.db.RlsSession} scoping
 * is needed here.
 */
@Repository
public class CategoryRepository {

    private static final RowMapper<Category> ROW_MAPPER =
            (rs, rowNum) -> new Category(rs.getInt("id"), rs.getString("name"), rs.getString("icon"));

    private final JdbcTemplate jdbcTemplate;

    public CategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Category> findAll() {
        return jdbcTemplate.query("SELECT id, name, icon FROM categories ORDER BY id", ROW_MAPPER);
    }

    public boolean existsById(int categoryId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ?", Integer.class, categoryId);
        return count != null && count > 0;
    }
}
