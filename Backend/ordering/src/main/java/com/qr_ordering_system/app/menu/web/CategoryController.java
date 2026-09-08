package com.qr_ordering_system.app.menu.web;

import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.CategoryUpdateRequest;
import com.qr_ordering_system.app.menu.dto.response.CategoryResponse;
import com.qr_ordering_system.app.menu.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // Any authenticated staff member can list categories (needed for menus, POS, etc.)
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAll() {
        return ResponseEntity.ok(categoryService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build(); // 204 — success, nothing to return
    }

}
