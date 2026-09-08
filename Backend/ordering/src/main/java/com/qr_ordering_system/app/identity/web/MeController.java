package com.qr_ordering_system.app.identity.web;

import com.qr_ordering_system.app.common.security.SecurityUser;
import com.qr_ordering_system.app.identity.dto.MeResponse;
import com.qr_ordering_system.app.identity.service.MeResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeController {

    private final MeResponseService meResponseService;

    @GetMapping("/me")
    public MeResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser securityUser)){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized"
            );
        }

        return meResponseService.byUserId(securityUser.userId());
    }

}
