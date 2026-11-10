package com.transitpulse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transitpulse.exception.AnalyticsUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the analytical payloads written by the Python data engine.
 *
 * <p>The engine owns the analytics; this service is the bridge that makes
 * them available over HTTP without re-implementing the maths in Java, so
 * there is exactly one definition of each metric in the system.
 *
 * <p>Payloads are cached in memory and reloaded when the file's modified
 * time changes, so re-running the pipeline is picked up without a restart.
 */
@Service
public class EngineOutputService {

    private static final Logger log = LoggerFactory.getLogger(EngineOutputService.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Path jsonDir;

    public EngineOutputService(
            @Value("${transitpulse.engine.output-dir:../data-engine/output}") String outputDir) {
        this.jsonDir = Paths.get(outputDir).resolve("json");
        log.info("Engine output directory resolved to {}", this.jsonDir.toAbsolutePath());
    }

    private record CacheEntry(long lastModified, Object payload) {
    }

    /** True when the engine has produced this payload. */
    public boolean isAvailable(String name) {
        return Files.exists(jsonDir.resolve(name + ".json"));
    }

    /** Names of every payload currently on disk. */
    public List<String> availablePayloads() {
        if (!Files.isDirectory(jsonDir)) {
            return List.of();
        }
        try (var stream = Files.list(jsonDir)) {
            List<String> names = new ArrayList<>();
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(p -> names.add(p.getFileName().toString()
                          .replace(".json", "")));
            names.sort(String::compareTo);
            return names;
        } catch (IOException e) {
            log.warn("Could not list engine output directory: {}", e.getMessage());
            return List.of();
        }
    }

    /** Load a payload as a map, or fail with actionable guidance. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadObject(String name) {
        Object payload = load(name, new TypeReference<Map<String, Object>>() {
        });
        return (Map<String, Object>) payload;
    }

    /** Load a payload that is a JSON array. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadList(String name) {
        Object payload = load(name, new TypeReference<List<Map<String, Object>>>() {
        });
        return (List<Map<String, Object>>) payload;
    }

    private Object load(String name, TypeReference<?> type) {
        Path path = jsonDir.resolve(name + ".json");
        if (!Files.exists(path)) {
            throw new AnalyticsUnavailableException(
                    "Analytics payload '" + name + "' has not been generated yet. "
                            + "Run the data engine first:  cd data-engine && "
                            + "python -m transitpulse_engine.pipeline   "
                            + "(expected file: " + path.toAbsolutePath() + ")");
        }
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            CacheEntry cached = cache.get(name);
            if (cached != null && cached.lastModified() == modified) {
                return cached.payload();
            }
            Object parsed = mapper.readValue(path.toFile(), type);
            cache.put(name, new CacheEntry(modified, parsed));
            log.debug("Loaded engine payload {}", name);
            return parsed;
        } catch (IOException e) {
            throw new AnalyticsUnavailableException(
                    "Could not read analytics payload '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Rows of a payload that is either a bare array or an object containing a
     * named array, so callers do not need to know the payload's shape.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> rows(String name, String key) {
        Path path = jsonDir.resolve(name + ".json");
        if (!Files.exists(path)) {
            throw new AnalyticsUnavailableException(
                    "Analytics payload '" + name + "' has not been generated yet. "
                            + "Run:  cd data-engine && python -m transitpulse_engine.pipeline");
        }
        try {
            Object parsed = mapper.readValue(path.toFile(), Object.class);
            if (parsed instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            if (parsed instanceof Map<?, ?> map) {
                Object inner = map.get(key);
                if (inner instanceof List<?> list) {
                    return (List<Map<String, Object>>) list;
                }
            }
            return List.of();
        } catch (IOException e) {
            throw new AnalyticsUnavailableException(
                    "Could not read '" + name + "': " + e.getMessage());
        }
    }

    /** Status block for the dashboard and health checks. */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("engineOutputDirectory", jsonDir.toAbsolutePath().toString());
        status.put("available", Files.isDirectory(jsonDir));
        status.put("payloads", availablePayloads());
        return status;
    }

    public void clearCache() {
        cache.clear();
    }
}
