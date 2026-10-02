package com.qr_ordering_system.app.menu.web;

import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemUpdateRequest;
import com.qr_ordering_system.app.menu.dto.response.MenuItemResponse;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    // Reads require MENU_VIEW rather than mere authentication: without it any
    // logged-in account (CASHIER, for one, has no MENU_VIEW in the default role
    // template) could read the whole menu and every base price.
    @GetMapping
    @PreAuthorize("hasAuthority('MENU_VIEW')")
    public ResponseEntity<List<MenuItemResponse>> getAll() {
        return ResponseEntity.ok(menuItemService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_VIEW')")
    public ResponseEntity<MenuItemResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.getById(id));
    }

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAuthority('MENU_VIEW')")
    public ResponseEntity<List<MenuItemResponse>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(menuItemService.getAllByCategory(categoryId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuItemCreateRequest request) {
        MenuItemResponse response = menuItemService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<MenuItemResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemUpdateRequest request) {
        return ResponseEntity.ok(menuItemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        menuItemService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
