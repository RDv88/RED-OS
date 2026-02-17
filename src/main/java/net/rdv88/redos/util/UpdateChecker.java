package net.rdv88.redos.util;

import net.rdv88.redos.Redos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-update");
    
    private static final String VERSION_URL = "https://pastebin.com/raw/f9jhckT6"; 
    
    private static String latestRemoteVersion = Redos.VERSION;
    private static String remoteChangelog = "";
    private static boolean updateChecked = false;

    public static void checkForUpdates() {
        if (updateChecked) return;
        
        LOGGER.info("RED-OS: Starting background update check...");
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(VERSION_URL))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();

                if (response.statusCode() == 200 && !body.isEmpty()) {
                    // REGEX EXTRACTION: Robustly find version and changelog despite line numbers or BOM
                    Pattern versionPattern = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
                    Pattern changelogPattern = Pattern.compile("\"changelog\"\\s*:\\s*\"([^\"]+)\"");
                    
                    Matcher vMatcher = versionPattern.matcher(body);
                    Matcher cMatcher = changelogPattern.matcher(body);
                    
                    if (vMatcher.find()) {
                        latestRemoteVersion = vMatcher.group(1);
                        if (cMatcher.find()) {
                            remoteChangelog = cMatcher.group(1);
                        }
                        updateChecked = true;
                        LOGGER.info("RED-OS: Regex check successful. Latest: V{}, Local: V{}", latestRemoteVersion, Redos.VERSION);
                    } else {
                        LOGGER.error("RED-OS: Could not find version pattern in response. Content: [{}]", body);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("RED-OS: Update check failed: {}", e.getMessage());
            }
        });
    }

    public static String getLatestRemoteVersion() { return latestRemoteVersion; }
    public static String getRemoteChangelog() { return remoteChangelog; }
    public static boolean isCheckDone() { return updateChecked; }

    /**
     * Compare version strings robustly (e.g., 1.0.4.5 vs 1.0.4.4).
     * Returns true only if the remote version is strictly HIGHER than the local version.
     */
    public static boolean isNewerVersionAvailable() {
        try {
            String[] remoteParts = latestRemoteVersion.split("\\.");
            String[] localParts = Redos.VERSION.split("\\.");
            
            int length = Math.max(remoteParts.length, localParts.length);
            for (int i = 0; i < length; i++) {
                int remote = i < remoteParts.length ? Integer.parseInt(remoteParts[i].replaceAll("[^0-9]", "")) : 0;
                int local = i < localParts.length ? Integer.parseInt(localParts[i].replaceAll("[^0-9]", "")) : 0;
                
                if (remote > local) return true;
                if (remote < local) return false;
            }
        } catch (Exception e) {
            // Fallback to simple equality check if parsing fails
            return !latestRemoteVersion.equals(Redos.VERSION);
        }
        return false;
    }
}
