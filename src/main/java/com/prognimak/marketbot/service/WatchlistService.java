package com.prognimak.marketbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.WatchlistItem;
import com.prognimak.marketbot.model.WatchlistPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {
    private static final TypeReference<Map<String, Object>> YAML_MAP_TYPE = new TypeReference<>() {};

    private final AppProperties properties;
    private final ResourceLoader resourceLoader;
    private final StockCatalogService stockCatalogService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public Map<String, WatchlistItem> watchlist() {
        Map<String, WatchlistItem> databaseWatchlist = stockCatalogService.enabledWatchlist();
        if (!databaseWatchlist.isEmpty()) {
            return databaseWatchlist;
        }
        return configuredWatchlistFromSource();
    }

    public Map<String, WatchlistItem> configuredWatchlistFromSource() {
        String watchlistFile = properties.shares().watchlistFile();
        if (watchlistFile == null || watchlistFile.isBlank()) {
            return configuredWatchlist();
        }

        Resource resource = resource(watchlistFile);
        if (!resource.exists() || !resource.isReadable()) {
            log.warn("Watchlist file {} does not exist or is not readable. Falling back to application watchlist.", watchlistFile);
            return configuredWatchlist();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, WatchlistItem> fileWatchlist = readWatchlist(inputStream);
            if (fileWatchlist.isEmpty()) {
                log.warn("Watchlist file {} is empty. Falling back to application watchlist.", watchlistFile);
                return configuredWatchlist();
            }

            return fileWatchlist;
        } catch (IOException e) {
            log.warn("Could not read watchlist file {}. Falling back to application watchlist.", watchlistFile, e);
            return configuredWatchlist();
        }
    }

    private Resource resource(String location) {
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            return resourceLoader.getResource(location);
        }

        return new FileSystemResource(Path.of(location));
    }

    private Map<String, WatchlistItem> configuredWatchlist() {
        if (properties.shares().watchlist() == null) {
            return Map.of();
        }

        Map<String, WatchlistItem> watchlist = new LinkedHashMap<>();
        properties.shares().watchlist().forEach((symbol, companyName) ->
                watchlist.put(symbol, WatchlistItem.simple(symbol, companyName))
        );
        return watchlist;
    }

    Map<String, WatchlistItem> readWatchlist(InputStream inputStream) throws IOException {
        Map<String, Object> root = yamlMapper.readValue(inputStream, YAML_MAP_TYPE);
        if (root == null || root.isEmpty()) {
            return Map.of();
        }

        Object watchlistNode = root.containsKey("watchlist") ? root.get("watchlist") : root;

        if (!(watchlistNode instanceof Map<?, ?> rawWatchlist)) {
            log.warn("Watchlist YAML must be a map or contain a top-level 'watchlist' map.");
            return Map.of();
        }

        Map<String, WatchlistItem> watchlist = new LinkedHashMap<>();
        rawWatchlist.forEach((symbol, value) -> {
            WatchlistItem item = toWatchlistItem(symbol, value);
            if (item != null && item.enabled()) {
                watchlist.put(item.symbol(), item);
            }
        });

        return watchlist;
    }

    private WatchlistItem toWatchlistItem(Object symbolValue, Object value) {
        if (symbolValue == null || value == null) {
            return null;
        }

        String symbol = symbolValue.toString();
        if (value instanceof String companyName) {
            return WatchlistItem.simple(symbol, companyName);
        }

        if (!(value instanceof Map<?, ?> metadata)) {
            log.warn("Skipping invalid watchlist item for symbol {}: {}", symbol, value);
            return null;
        }

        String name = stringValue(metadata, "name");
        if (name == null || name.isBlank()) {
            log.warn("Skipping watchlist item {} because required field 'name' is missing.", symbol);
            return null;
        }

        return new WatchlistItem(
                symbol,
                name,
                stringValue(metadata, "region"),
                stringValue(metadata, "sector"),
                stringValue(metadata, "exchange"),
                stringValue(metadata, "currency"),
                booleanValue(metadata, "enabled", true),
                priorityValue(metadata)
        );
    }

    private String stringValue(Map<?, ?> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private boolean booleanValue(Map<?, ?> metadata, String key, boolean fallback) {
        Object value = metadata.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        return Boolean.parseBoolean(value.toString());
    }

    private WatchlistPriority priorityValue(Map<?, ?> metadata) {
        Object value = metadata.get("priority");
        if (value == null) {
            return WatchlistPriority.NORMAL;
        }

        try {
            return WatchlistPriority.valueOf(value.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown watchlist priority {}. Falling back to NORMAL.", value);
            return WatchlistPriority.NORMAL;
        }
    }
}
