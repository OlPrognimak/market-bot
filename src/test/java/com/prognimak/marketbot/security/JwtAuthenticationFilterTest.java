package com.prognimak.marketbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import com.prognimak.marketbot.entity.AppUserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fallsBackToUsernameWhenTokenUserIdNoLongerExists() throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        AppUserDetailsService userDetailsService = mock(AppUserDetailsService.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(1L);
        user.setUsername("alex");
        user.setPasswordHash("hash");
        AppUserPrincipal principal = new AppUserPrincipal(user);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtService.extractUserId("token")).thenReturn(Optional.of(99L));
        when(userDetailsService.loadUserById(99L)).thenThrow(new UsernameNotFoundException("missing"));
        when(jwtService.extractUsername("token")).thenReturn(Optional.of("alex"));
        when(userDetailsService.loadUserByUsername("alex")).thenReturn(principal);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
    }
}
