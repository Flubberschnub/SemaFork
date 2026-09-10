package com.diningphilosopher.semafork.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapsConfigController {
    private final MapsConfig config;

    public MapsConfigController(@Value("${GOOGLE_MAPS_BROWSER_KEY:}") String browserKey,
                                @Value("${GOOGLE_MAPS_MAP_ID:DEMO_MAP_ID}") String mapId) {
        String key = browserKey.trim();
        this.config = new MapsConfig(!key.isEmpty(), key, mapId.isBlank() ? "DEMO_MAP_ID" : mapId.trim());
    }

    // Intentionally public browser key, protected by Google website/API restrictions.
    // Do not add server API keys or any other environment configuration to this response.
    @GetMapping("/api/config/maps")
    public ResponseEntity<MapsConfig> mapsConfig() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config);
    }

    public record MapsConfig(boolean enabled, String browserKey, String mapId) {}
}
