package com.retail.pim.taxonomy.infrastructure.persistence;

import com.retail.pim.shared.exceptions.DomainException;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import com.retail.pim.taxonomy.domain.model.CategoryNode;
import com.retail.pim.taxonomy.domain.repository.CategoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: Implements CategoryRepository domain port using the Closure Table pattern in PostgreSQL.
 */
@Repository
@Transactional
public class ClosureTableRepositoryImpl implements CategoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClosureTableRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CategoryNode> rowMapper = new RowMapper<>() {
        @Override
        public CategoryNode mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID id = (UUID) rs.getObject("id");
            String code = rs.getString("code");
            String name = rs.getString("name");
            String description = rs.getString("description");
            UUID parentId = (UUID) rs.getObject("parent_id");
            int level = rs.getInt("level");

            CategoryNode node = new CategoryNode(id, code, name, description, parentId, level);
            node.clearDomainEvents();
            return node;
        }
    };

    @Override
    public CategoryNode save(CategoryNode node) {
        if (existsById(node.getId())) {
            String updateSql = """
                UPDATE taxonomy.category_node
                SET name = ?, description = ?, updated_at = ?
                WHERE id = ?
                """;
            jdbcTemplate.update(updateSql, node.getName(), node.getDescription(),
                    Timestamp.from(Instant.now()), node.getId());
            return node;
        }

        String insertSql = """
            INSERT INTO taxonomy.category_node (id, code, name, description, parent_id, level, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(insertSql,
                node.getId(),
                node.getCode(),
                node.getName(),
                node.getDescription(),
                node.getParentId(),
                node.getLevel(),
                Timestamp.from(node.getCreatedAt()),
                Timestamp.from(node.getUpdatedAt())
        );

        String selfClosureSql = """
            INSERT INTO taxonomy.category_closure (ancestor_id, descendant_id, depth)
            VALUES (?, ?, 0)
            """;
        jdbcTemplate.update(selfClosureSql, node.getId(), node.getId());

        if (node.getParentId() != null) {
            String parentClosureSql = """
                INSERT INTO taxonomy.category_closure (ancestor_id, descendant_id, depth)
                SELECT ancestor_id, ?, depth + 1
                FROM taxonomy.category_closure
                WHERE descendant_id = ?
                """;
            jdbcTemplate.update(parentClosureSql, node.getId(), node.getParentId());
        }

        return node;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoryNode> findById(UUID id) {
        String sql = "SELECT * FROM taxonomy.category_node WHERE id = ?";
        List<CategoryNode> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoryNode> findByCode(String code) {
        String sql = "SELECT * FROM taxonomy.category_node WHERE code = ?";
        List<CategoryNode> results = jdbcTemplate.query(sql, rowMapper, code.toUpperCase());
        return results.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryNode> findAllRoots() {
        String sql = "SELECT * FROM taxonomy.category_node WHERE parent_id IS NULL ORDER BY name ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryNode> findAll() {
        String sql = "SELECT * FROM taxonomy.category_node ORDER BY level ASC, name ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryNode> findAncestors(UUID categoryId) {
        String sql = """
            SELECT n.*
            FROM taxonomy.category_node n
            JOIN taxonomy.category_closure c ON n.id = c.ancestor_id
            WHERE c.descendant_id = ?
            ORDER BY c.depth DESC
            """;
        return jdbcTemplate.query(sql, rowMapper, categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryNode> findDescendants(UUID categoryId) {
        String sql = """
            SELECT n.*
            FROM taxonomy.category_node n
            JOIN taxonomy.category_closure c ON n.id = c.descendant_id
            WHERE c.ancestor_id = ? AND c.depth > 0
            ORDER BY c.depth ASC, n.name ASC
            """;
        return jdbcTemplate.query(sql, rowMapper, categoryId);
    }

    @Override
    public void moveCategory(UUID categoryId, UUID newParentId) {
        CategoryNode target = findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("CategoryNode", categoryId));

        if (newParentId != null) {
            CategoryNode newParent = findById(newParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("CategoryNode", newParentId));

            String cycleCheckSql = """
                SELECT COUNT(*) FROM taxonomy.category_closure
                WHERE ancestor_id = ? AND descendant_id = ?
                """;
            Integer count = jdbcTemplate.queryForObject(cycleCheckSql, Integer.class, categoryId, newParentId);
            if (count != null && count > 0) {
                throw new DomainException("Cannot move a category under one of its own descendants (cycle detected)");
            }

            String disconnectSql = """
                DELETE FROM taxonomy.category_closure
                WHERE descendant_id IN (
                    SELECT descendant_id FROM taxonomy.category_closure WHERE ancestor_id = ?
                )
                AND ancestor_id IN (
                    SELECT ancestor_id FROM taxonomy.category_closure WHERE descendant_id = ? AND ancestor_id != descendant_id
                )
                """;
            jdbcTemplate.update(disconnectSql, categoryId, categoryId);

            String reconnectSql = """
                INSERT INTO taxonomy.category_closure (ancestor_id, descendant_id, depth)
                SELECT supertree.ancestor_id, subtree.descendant_id, supertree.depth + subtree.depth + 1
                FROM taxonomy.category_closure AS supertree
                CROSS JOIN taxonomy.category_closure AS subtree
                WHERE supertree.descendant_id = ?
                  AND subtree.ancestor_id = ?
                """;
            jdbcTemplate.update(reconnectSql, newParentId, categoryId);

            target.moveTo(newParentId, newParent.getLevel() + 1);
        } else {
            String disconnectToRootSql = """
                DELETE FROM taxonomy.category_closure
                WHERE descendant_id IN (
                    SELECT descendant_id FROM taxonomy.category_closure WHERE ancestor_id = ?
                )
                AND ancestor_id IN (
                    SELECT ancestor_id FROM taxonomy.category_closure WHERE descendant_id = ? AND ancestor_id != descendant_id
                )
                """;
            jdbcTemplate.update(disconnectToRootSql, categoryId, categoryId);
            target.moveTo(null, 0);
        }

        String updateNodeSql = """
            UPDATE taxonomy.category_node
            SET parent_id = ?, level = ?, updated_at = ?
            WHERE id = ?
            """;
        jdbcTemplate.update(updateNodeSql, target.getParentId(), target.getLevel(),
                Timestamp.from(Instant.now()), target.getId());
    }

    @Override
    public void deleteById(UUID id) {
        String deleteSql = "DELETE FROM taxonomy.category_node WHERE id = ?";
        jdbcTemplate.update(deleteSql, id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        String sql = "SELECT COUNT(*) FROM taxonomy.category_node WHERE code = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, code.toUpperCase());
        return count != null && count > 0;
    }

    private boolean existsById(UUID id) {
        String sql = "SELECT COUNT(*) FROM taxonomy.category_node WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
}
