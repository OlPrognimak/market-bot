package com.prognimak.marketbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.prognimak.marketbot.config.AppProperties;
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
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public Map<String, String> watchlist() {
        String watchlistFile = properties.watchlistFile();
        if (watchlistFile == null || watchlistFile.isBlank()) {
            return configuredWatchlist();
        }

        Resource resource = resource(watchlistFile);
        if (!resource.exists() || !resource.isReadable()) {
            log.warn("Watchlist file {} does not exist or is not readable. Falling back to application watchlist.", watchlistFile);
            return configuredWatchlist();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, String> fileWatchlist = readWatchlist(inputStream);
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

    private Map<String, String> configuredWatchlist() {
        if (properties.watchlist() == null) {
            return Map.of();
        }

        return properties.watchlist();
    }

    private Map<String, String> readWatchlist(InputStream inputStream) throws IOException {
        Map<String, Object> root = yamlMapper.readValue(inputStream, YAML_MAP_TYPE);
        Object watchlistNode = root.containsKey("watchlist") ? root.get("watchlist") : root;

        if (!(watchlistNode instanceof Map<?, ?> rawWatchlist)) {
            log.warn("Watchlist YAML must be a map or contain a top-level 'watchlist' map.");
            return Map.of();
        }

        Map<String, String> watchlist = new LinkedHashMap<>();
        rawWatchlist.forEach((symbol, companyName) -> {
            if (symbol != null && companyName != null) {
                watchlist.put(symbol.toString(), companyName.toString());
            }
        });

        return watchlist;
    }
}
