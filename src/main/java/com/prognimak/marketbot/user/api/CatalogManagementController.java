package com.prognimak.marketbot.user.api;

import com.prognimak.marketbot.service.CryptoCoinCatalogService;
import com.prognimak.marketbot.service.StockCatalogService;
import com.prognimak.marketbot.user.model.CatalogItemRequest;
import com.prognimak.marketbot.user.model.CryptoCatalogResponse;
import com.prognimak.marketbot.user.model.StockCatalogResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CatalogManagementController {
    private final StockCatalogService stockCatalogService;
    private final CryptoCoinCatalogService cryptoCoinCatalogService;

    @GetMapping("/stocks")
    public List<StockCatalogResponse> stocks() {
        return stockCatalogService.list();
    }

    @PostMapping("/stocks")
    public StockCatalogResponse createStock(@Valid @RequestBody CatalogItemRequest request) {
        return stockCatalogService.create(request);
    }

    @PutMapping("/stocks/{id}")
    public StockCatalogResponse updateStock(@PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
        return stockCatalogService.update(id, request);
    }

    @DeleteMapping("/stocks/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteStock(@PathVariable Long id) {
        stockCatalogService.delete(id);
    }

    @GetMapping("/crypto")
    public List<CryptoCatalogResponse> crypto() {
        return cryptoCoinCatalogService.list();
    }

    @PostMapping("/crypto")
    public CryptoCatalogResponse createCrypto(@Valid @RequestBody CatalogItemRequest request) {
        return cryptoCoinCatalogService.create(request);
    }

    @PutMapping("/crypto/{id}")
    public CryptoCatalogResponse updateCrypto(@PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
        return cryptoCoinCatalogService.update(id, request);
    }

    @DeleteMapping("/crypto/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteCrypto(@PathVariable Long id) {
        cryptoCoinCatalogService.delete(id);
    }
}
