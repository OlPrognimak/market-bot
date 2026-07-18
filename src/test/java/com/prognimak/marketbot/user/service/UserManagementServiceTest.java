package com.prognimak.marketbot.user.service;

import com.prognimak.marketbot.entity.AppUserEntity;
import com.prognimak.marketbot.entity.AppUserPropertyEntity;
import com.prognimak.marketbot.repository.AppUserRepository;
import com.prognimak.marketbot.security.UserRole;
import com.prognimak.marketbot.service.SymbolValidationService;
import com.prognimak.marketbot.user.model.UpdateUserRequest;
import com.prognimak.marketbot.user.model.UserPropertyRequest;
import com.prognimak.marketbot.user.model.UserPropertyType;
import com.prognimak.marketbot.user.model.UserPropertyValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SymbolValidationService symbolValidationService;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(
                userRepository,
                passwordEncoder,
                new UserMapper(),
                eventPublisher,
                symbolValidationService
        );
    }

    @Test
    void updateSelfValidatesAndOnboardsEnabledWatchlistSymbolBeforeSavingProperty() {
        AppUserEntity user = new AppUserEntity();
        user.setId(1L);
        user.setUsername("alex");
        user.setDisplayName("Alex");
        user.setEmail("alex@example.com");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameIgnoreCase("alex")).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(symbolValidationService.validateStock("AAPL")).thenReturn(
                new SymbolValidationService.SymbolValidationResult("AAPL", true, null, "Apple Inc.")
        );

        service.updateSelf(1L, new UpdateUserRequest(
                "alex",
                null,
                "Alex",
                "alex@example.com",
                UserRole.USER,
                true,
                Map.of(),
                List.of(new UserPropertyRequest(
                        null,
                        UserPropertyType.WATCHLIST,
                        "AAPL",
                        "Apple",
                        true,
                        null,
                        UserPropertyValueType.SYMBOL
                ))
        ));

        verify(symbolValidationService).validateStock("AAPL");
        assertEquals("AAPL", user.getProperties().getFirst().getPropertyName());
    }

    @Test
    void updateSelfDoesNotRevalidateUnchangedEnabledWatchlistSymbol() {
        AppUserEntity user = new AppUserEntity();
        user.setId(1L);
        user.setUsername("alex");
        user.setDisplayName("Alex");
        user.setEmail("alex@example.com");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        AppUserPropertyEntity property = new AppUserPropertyEntity();
        property.setId(10L);
        property.setUser(user);
        property.setPropertyType(UserPropertyType.WATCHLIST);
        property.setPropertyName("LEGACY");
        property.setPropertyValue("Legacy Share");
        property.setEnabled(true);
        property.setPropertyValueType(UserPropertyValueType.SYMBOL);
        user.getProperties().add(property);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameIgnoreCase("alex")).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateSelf(1L, new UpdateUserRequest(
                "alex",
                null,
                "Alex Updated",
                "alex@example.com",
                UserRole.USER,
                true,
                Map.of(),
                List.of(new UserPropertyRequest(
                        10L,
                        UserPropertyType.WATCHLIST,
                        "LEGACY",
                        "Legacy Share",
                        true,
                        null,
                        UserPropertyValueType.SYMBOL
                ))
        ));

        verify(symbolValidationService, never()).validateStock("LEGACY");
        assertEquals("Alex Updated", user.getDisplayName());
    }
}
