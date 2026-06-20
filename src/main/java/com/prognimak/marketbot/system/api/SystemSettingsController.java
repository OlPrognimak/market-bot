package com.prognimak.marketbot.system.api;

import com.prognimak.marketbot.portfolio.service.ProviderSymbolMappingService;
import com.prognimak.marketbot.system.model.ProviderSymbolMappingRequest;
import com.prognimak.marketbot.system.model.ProviderSymbolMappingResponse;
import com.prognimak.marketbot.system.model.SystemApiCredentialRequest;
import com.prognimak.marketbot.system.model.SystemApiCredentialResponse;
import com.prognimak.marketbot.system.service.SystemApiCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingsController {
    private final ProviderSymbolMappingService symbolMappingService;
    private final SystemApiCredentialService credentialService;

    @GetMapping("/symbol-mappings")
    public List<ProviderSymbolMappingResponse> symbolMappings() {
        return symbolMappingService.list();
    }

    @PostMapping("/symbol-mappings")
    public ProviderSymbolMappingResponse createSymbolMapping(@Valid @RequestBody ProviderSymbolMappingRequest request) {
        return symbolMappingService.save(request);
    }

    @PutMapping("/symbol-mappings/{id}")
    public ProviderSymbolMappingResponse updateSymbolMapping(
            @PathVariable Long id,
            @Valid @RequestBody ProviderSymbolMappingRequest request
    ) {
        return symbolMappingService.update(id, request);
    }

    @DeleteMapping("/symbol-mappings/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteSymbolMapping(@PathVariable Long id) {
        symbolMappingService.delete(id);
    }

    @GetMapping("/api-credentials")
    public List<SystemApiCredentialResponse> apiCredentials() {
        return credentialService.list();
    }

    @PostMapping("/api-credentials")
    public SystemApiCredentialResponse createApiCredential(@Valid @RequestBody SystemApiCredentialRequest request) {
        return credentialService.save(request);
    }

    @PutMapping("/api-credentials/{id}")
    public SystemApiCredentialResponse updateApiCredential(
            @PathVariable Long id,
            @Valid @RequestBody SystemApiCredentialRequest request
    ) {
        return credentialService.update(id, request);
    }

    @DeleteMapping("/api-credentials/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteApiCredential(@PathVariable Long id) {
        credentialService.delete(id);
    }
}
