package com.prognimak.marketbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.prognimak.marketbot.config.AppProperties;
import com.prognimak.marketbot.model.CryptoWatchlistItem;
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
public class CryptoWatchlistService {
    private static final TypeReference<Map<String, Object>> YAML_MAP_TYPE = new TypeReference<>() {
    };

    private final AppProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public Map<String, CryptoWatchlistItem> watchlist() {
        String watchlistFile = properties.crypto().watchlistFile();
        if (watchlistFile == null || watchlistFile.isBlank()) {
            return configuredWatchlist();
        }

        Resource resource = resource(watchlistFile);
        if (!resource.exists() || !resource.isReadable()) {
            log.warn("Crypto watchlist file {} does not exist or is not readable. Falling back to application crypto watchlist.", watchlistFile);
            return configuredWatchlist();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, CryptoWatchlistItem> fileWatchlist = readWatchlist(inputStream);
            if (fileWatchlist.isEmpty()) {
                log.warn("Crypto watchlist file {} is empty. Falling back to application crypto watchlist.", watchlistFile);
                return configuredWatchlist();
            }
            return fileWatchlist;
        } catch (IOException e) {
            log.warn("Could not read crypto watchlist file {}. Falling back to application crypto watchlist.", watchlistFile, e);
            return configuredWatchlist();
        }
    }

    private Resource resource(String location) {
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            return resourceLoader.getResource(location);
        }
        return new FileSystemResource(Path.of(location));
    }

    private Map<String, CryptoWatchlistItem> configuredWatchlist() {
        if (properties.crypto().watchlist() == null) {
            return Map.of();
        }

        Map<String, CryptoWatchlistItem> watchlist = new LinkedHashMap<>();
        properties.crypto().watchlist().forEach((symbol, name) ->
                watchlist.put(normalizeSymbol(symbol), CryptoWatchlistItem.simple(normalizeSymbol(symbol), name))
        );
        return watchlist;
    }

    private Map<String, CryptoWatchlistItem> readWatchlist(InputStream inputStream) throws IOException {
        Map<String, Object> root = yamlMapper.readValue(inputStream, YAML_MAP_TYPE);
        if (root == null || root.isEmpty()) {
            return Map.of();
        }

        Object watchlistNode = root.containsKey("crypto-watchlist") ? root.get("crypto-watchlist") : root;
        if (!(watchlistNode instanceof Map<?, ?> rawWatchlist)) {
            log.warn("Crypto watchlist YAML must be a map or contain a top-level 'crypto-watchlist' map.");
            return Map.of();
        }

        Map<String, CryptoWatchlistItem> watchlist = new LinkedHashMap<>();
        rawWatchlist.forEach((symbol, value) -> {
            CryptoWatchlistItem item = toWatchlistItem(symbol, value);
            if (item != null && item.enabled()) {
                watchlist.put(item.symbol(), item);
            }
        });
        return watchlist;
    }

    private CryptoWatchlistItem toWatchlistItem(Object symbolValue, Object value) {
        if (symbolValue == null || value == null) {
            return null;
        }

        String symbol = normalizeSymbol(symbolValue.toString());
        if (value instanceof String name) {
            return CryptoWatchlistItem.simple(symbol, name);
        }

        if (!(value instanceof Map<?, ?> metadata)) {
            log.warn("Skipping invalid crypto watchlist item for symbol {}: {}", symbol, value);
            return null;
        }

        String name = stringValue(metadata, "name");
        if (name == null || name.isBlank()) {
            name = symbol;
        }
        return new CryptoWatchlistItem(symbol, name, booleanValue(metadata, "enabled", true));
    }

    private String normalizeSymbol(String symbol) {
        String normalized = symbol.trim().toUpperCase();
        return normalized.endsWith("USDT") ? normalized.substring(0, normalized.length() - 4) : normalized;
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
}
