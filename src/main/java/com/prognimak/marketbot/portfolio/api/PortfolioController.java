package com.prognimak.marketbot.portfolio.api;

import com.prognimak.marketbot.portfolio.model.*;
import com.prognimak.marketbot.portfolio.service.PortfolioAnalysisService;
import com.prognimak.marketbot.portfolio.service.PortfolioImportService;
import com.prognimak.marketbot.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioImportService importService;
    private final PortfolioAnalysisService analysisService;

    @PostMapping("/imports")
    public PortfolioImportResponse importCsv(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "REVOLUT") PortfolioProviderType providerType,
            @RequestPart("file") MultipartFile file
    ) {
        return importService.importFile(principal.user(), providerType, file);
    }

    @GetMapping("/imports")
    public List<PortfolioImportResponse> imports(@AuthenticationPrincipal AppUserPrincipal principal) {
        return importService.imports(principal.user().getId());
    }

    @GetMapping("/analysis")
    public PortfolioAnalysisResponse analysis(@AuthenticationPrincipal AppUserPrincipal principal) {
        return analysisService.analyze(principal.user().getId());
    }

    @GetMapping("/markers/{ticker}")
    public PortfolioMarkerResponse markers(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String ticker
    ) {
        return analysisService.markers(principal.user().getId(), ticker);
    }
}
