package com.prognimak.marketbot.portfolio.service;

import com.prognimak.marketbot.portfolio.entity.ProviderSymbolMappingEntity;
import com.prognimak.marketbot.portfolio.model.PortfolioProviderType;
import com.prognimak.marketbot.portfolio.repository.ProviderSymbolMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderSymbolMappingServiceTest {

    @Mock
    private ProviderSymbolMappingRepository repository;

    private ProviderSymbolMappingService service;

    @BeforeEach
    void setUp() {
        service = new ProviderSymbolMappingService(repository);
        when(repository.findByEnabledTrueOrderByProviderTypeAscSourceSymbolAscPriorityAsc()).thenReturn(List.of());
    }

    @Test
    void ensureTickerMappingsForStockCreatesRevolutAndTradeRepublicTickerMappings() {
        when(repository.findByProviderTypeAndSourceSymbolIgnoreCaseAndMarketProviderIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());

        service.ensureTickerMappingsForStock("AIR.PA", "Airbus SE", "EUR");

        ArgumentCaptor<ProviderSymbolMappingEntity> captor = ArgumentCaptor.forClass(ProviderSymbolMappingEntity.class);
        verify(repository, times(4)).save(captor.capture());
        assertEquals(List.of(
                PortfolioProviderType.REVOLUT,
                PortfolioProviderType.TRADE_REPUBLIC,
                PortfolioProviderType.REVOLUT,
                PortfolioProviderType.TRADE_REPUBLIC
        ), captor.getAllValues().stream().map(ProviderSymbolMappingEntity::getProviderType).toList());
        assertEquals(List.of("AIR", "AIR", "AIR.PA", "AIR.PA"),
                captor.getAllValues().stream().map(ProviderSymbolMappingEntity::getSourceSymbol).toList());
        assertEquals(List.of("AIR.PA", "AIR.PA", "AIR.PA", "AIR.PA"),
                captor.getAllValues().stream().map(ProviderSymbolMappingEntity::getMarketSymbol).toList());
    }
}
