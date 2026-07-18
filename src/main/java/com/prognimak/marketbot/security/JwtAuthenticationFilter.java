package com.prognimak.marketbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = tokenFromRequest(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            java.util.Optional<UserDetails> user = loadUser(token);
            if (user.isEmpty()) {
                log.warn("JWT authentication did not create a principal: {}", jwtService.rejectionReason(token));
                return;
            }

            UserDetails userDetails = user.get();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private java.util.Optional<UserDetails> loadUser(String token) {
        java.util.Optional<Long> userId = jwtService.extractUserId(token);
        if (userId.isPresent()) {
            try {
                return java.util.Optional.of(userDetailsService.loadUserById(userId.get()));
            } catch (UsernameNotFoundException e) {
                log.debug("JWT user id {} was not found. Trying token subject.", userId.get());
            }
        }
        return jwtService.extractUsername(token).map(userDetailsService::loadUserByUsername);
    }

    private String tokenFromRequest(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        String accessToken = request.getParameter("access_token");
        return accessToken == null || accessToken.isBlank() ? null : accessToken;
    }
}
