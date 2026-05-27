package com.prognimak.marketbot.user.api;

import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.repository.AppUserRepository;
import com.prognimak.marketbot.security.AppUserPrincipal;
import com.prognimak.marketbot.security.JwtService;
import com.prognimak.marketbot.user.model.AuthResponse;
import com.prognimak.marketbot.user.model.LoginRequest;
import com.prognimak.marketbot.user.model.SignUpRequest;
import com.prognimak.marketbot.user.model.UserResponse;
import com.prognimak.marketbot.user.service.UserMapper;
import com.prognimak.marketbot.user.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final UserManagementService userManagementService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AppUserEntity user = userRepository.findByUsernameIgnoreCase(request.username())
                .filter(AppUserEntity::isEnabled)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        return new AuthResponse(jwtService.createToken(user), userMapper.toResponse(user));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
        AppUserEntity user = userManagementService.signUp(request);
        return new AuthResponse(jwtService.createToken(user), userMapper.toResponse(user));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userMapper.toResponse(principal.user());
    }
}
