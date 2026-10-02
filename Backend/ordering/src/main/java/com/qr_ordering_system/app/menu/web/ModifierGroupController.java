package com.qr_ordering_system.app.menu.web;

import com.qr_ordering_system.app.menu.dto.request.ModifierGroupCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.ModifierGroupUpdateRequest;
import com.qr_ordering_system.app.menu.dto.response.ModifierGroupResponse;
import com.qr_ordering_system.app.menu.service.ModifierGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/modifier-groups")
@RequiredArgsConstructor
public class ModifierGroupController {

    private final ModifierGroupService modifierGroupService;

    @PostMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ModifierGroupResponse> create (@Valid @RequestBody ModifierGroupCreateRequest request){
        return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(modifierGroupService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MENU_VIEW')")
    public ResponseEntity<List<ModifierGroupResponse>> getAll(){
        return ResponseEntity.ok(modifierGroupService.getAll());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ModifierGroupResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ModifierGroupUpdateRequest request) {
        return ResponseEntity.ok(modifierGroupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        modifierGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }


}
