package com.prognimak.marketbot.config;

import com.prognimak.marketbot.entity.FuturesCatalogEntity;
import com.prognimak.marketbot.repository.FuturesCatalogRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FuturesCatalogInitializerTest {
    private final FuturesCatalogRepository repository = mock(FuturesCatalogRepository.class);
    private final FuturesCatalogInitializer initializer = new FuturesCatalogInitializer(repository);

    @Test
    void disablesExistingUnsupportedYahooEurexSymbols() {
        FuturesCatalogEntity dax = catalog("DAX", "FDAX.DE", true);
        when(repository.findBySymbolIgnoreCase("DAX")).thenReturn(Optional.of(dax));

        initializer.run(null);

        assertFalse(dax.isEnabled());
        verify(repository).save(dax);
    }

    @Test
    void preservesCustomProviderSymbolForDax() {
        FuturesCatalogEntity dax = catalog("DAX", "CUSTOM:DAX", true);
        when(repository.findBySymbolIgnoreCase("DAX")).thenReturn(Optional.of(dax));

        initializer.run(null);

        assertTrue(dax.isEnabled());
        verify(repository, never()).save(dax);
    }

    private FuturesCatalogEntity catalog(String symbol, String providerSymbol, boolean enabled) {
        FuturesCatalogEntity entity = new FuturesCatalogEntity();
        entity.setSymbol(symbol);
        entity.setProviderSymbol(providerSymbol);
        entity.setEnabled(enabled);
        return entity;
    }
}
