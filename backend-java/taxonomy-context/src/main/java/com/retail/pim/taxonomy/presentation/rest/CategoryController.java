package com.retail.pim.taxonomy.presentation.rest;

import com.retail.pim.shared.exceptions.DomainException;
import com.retail.pim.shared.exceptions.ResourceNotFoundException;
import com.retail.pim.taxonomy.domain.model.CategoryNode;
import com.retail.pim.taxonomy.domain.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Web Controller implementing openapi/taxonomy-api.yaml.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public record CreateCategoryRequest(String code, String name, String description, UUID parentId) {}
    public record MoveCategoryRequest(UUID newParentId) {}

    public record CategoryResponse(
            UUID id,
            String code,
            String name,
            String description,
            UUID parentId,
            int level,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CategoryResponse fromDomain(CategoryNode node) {
            return new CategoryResponse(
                    node.getId(),
                    node.getCode(),
                    node.getName(),
                    node.getDescription(),
                    node.getParentId(),
                    node.getLevel(),
                    node.getCreatedAt(),
                    node.getUpdatedAt()
            );
        }
    }

    public record CategoryTreeNodeResponse(
            CategoryResponse category,
            List<CategoryTreeNodeResponse> children
    ) {}

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@RequestBody CreateCategoryRequest request) {
        if (categoryRepository.existsByCode(request.code())) {
            throw new DomainException(String.format("Category with code '%s' already exists", request.code()));
        }

        CategoryNode node;
        if (request.parentId() == null) {
            node = CategoryNode.createRoot(request.code(), request.name(), request.description());
        } else {
            CategoryNode parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("CategoryNode", request.parentId()));
            node = CategoryNode.createChild(request.code(), request.name(), request.description(), parent.getId(), parent.getLevel());
        }

        CategoryNode saved = categoryRepository.save(node);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.fromDomain(saved));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories(@RequestParam(defaultValue = "false") boolean rootOnly) {
        List<CategoryNode> list = rootOnly ? categoryRepository.findAllRoots() : categoryRepository.findAll();
        List<CategoryResponse> response = list.stream().map(CategoryResponse::fromDomain).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID id) {
        CategoryNode node = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CategoryNode", id));
        return ResponseEntity.ok(CategoryResponse.fromDomain(node));
    }

    @GetMapping("/{id}/ancestors")
    public ResponseEntity<List<CategoryResponse>> getAncestors(@PathVariable UUID id) {
        List<CategoryNode> ancestors = categoryRepository.findAncestors(id);
        return ResponseEntity.ok(ancestors.stream().map(CategoryResponse::fromDomain).collect(Collectors.toList()));
    }

    @GetMapping("/{id}/descendants")
    public ResponseEntity<List<CategoryResponse>> getDescendants(@PathVariable UUID id) {
        List<CategoryNode> descendants = categoryRepository.findDescendants(id);
        return ResponseEntity.ok(descendants.stream().map(CategoryResponse::fromDomain).collect(Collectors.toList()));
    }

    @GetMapping("/{id}/tree")
    public ResponseEntity<CategoryTreeNodeResponse> getCategorySubtree(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int maxDepth) {
        CategoryNode root = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CategoryNode", id));
        List<CategoryNode> descendants = categoryRepository.findDescendants(id);

        Map<UUID, List<CategoryNode>> childrenMap = new HashMap<>();
        for (CategoryNode node : descendants) {
            childrenMap.computeIfAbsent(node.getParentId(), k -> new ArrayList<>()).add(node);
        }

        CategoryTreeNodeResponse tree = buildTree(root, childrenMap, 0, maxDepth);
        return ResponseEntity.ok(tree);
    }

    private CategoryTreeNodeResponse buildTree(CategoryNode current, Map<UUID, List<CategoryNode>> childrenMap, int currentDepth, int maxDepth) {
        List<CategoryTreeNodeResponse> childNodes = new ArrayList<>();
        if (currentDepth < maxDepth) {
            List<CategoryNode> children = childrenMap.getOrDefault(current.getId(), List.of());
            for (CategoryNode child : children) {
                childNodes.add(buildTree(child, childrenMap, currentDepth + 1, maxDepth));
            }
        }
        return new CategoryTreeNodeResponse(CategoryResponse.fromDomain(current), childNodes);
    }

    @PostMapping("/{id}/move")
    public ResponseEntity<CategoryResponse> moveCategory(
            @PathVariable UUID id,
            @RequestBody MoveCategoryRequest request) {
        categoryRepository.moveCategory(id, request.newParentId());
        CategoryNode updated = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CategoryNode", id));
        return ResponseEntity.ok(CategoryResponse.fromDomain(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
