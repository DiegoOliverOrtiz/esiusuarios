package esi.edu.usuarios.usuarios.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RateLimiterService {
    private static final String RATE_LIMIT_MESSAGE = "Demasiadas solicitudes. Intentalo de nuevo mas tarde.";

    private final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void check(String scope, String key, int maxRequests, Duration window) {
        if (maxRequests <= 0 || window == null || window.isNegative() || window.isZero()) {
            return;
        }

        String bucketKey = normalize(scope) + "|" + normalize(key);
        Instant now = Instant.now();
        Bucket bucket = buckets.compute(bucketKey, (ignored, current) -> {
            if (current == null || !current.windowStart.plus(window).isAfter(now)) {
                return new Bucket(now, 1, window);
            }
            return new Bucket(current.windowStart, current.count + 1, window);
        });

        if (bucket.count > maxRequests) {
            logger.warn("Rate limit superado scope={} max={} windowSeconds={}", scope, maxRequests, window.toSeconds());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_MESSAGE);
        }
    }

    @Scheduled(fixedDelayString = "${app.rate-limit.cleanup-delay-ms:300000}")
    public void cleanupExpiredBuckets() {
        Instant now = Instant.now();
        buckets.entrySet().removeIf(entry -> !entry.getValue().windowStart.plus(entry.getValue().window).isAfter(now));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Bucket(Instant windowStart, int count, Duration window) {
    }
}
