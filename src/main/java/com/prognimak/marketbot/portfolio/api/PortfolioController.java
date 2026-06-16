package com.prognimak.marketbot.portfolio.api;

import com.prognimak.marketbot.portfolio.model.*;
import com.prognimak.marketbot.portfolio.service.PortfolioAnalysisService;
import com.prognimak.marketbot.portfolio.service.PortfolioImportService;
import com.prognimak.marketbot.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.time.LocalDate;

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
    public PortfolioAnalysisResponse analysis(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String providerType
    ) {
        return analysisService.analyze(principal.user().getId(), from, to, ticker, providerType);
    }

    @GetMapping("/markers/{ticker}")
    public PortfolioMarkerResponse markers(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String ticker
    ) {
        return analysisService.markers(principal.user().getId(), ticker);
    }
}
