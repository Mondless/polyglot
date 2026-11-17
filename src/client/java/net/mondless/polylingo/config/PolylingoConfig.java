package net.mondless.polylingo.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Client-only user configuration. The values are vanilla/Fabric language IDs, never display names. */
public final class PolylingoConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Polylingo");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("polylingo.json");

    private static volatile List<String> additionalLanguages = List.of();

    private PolylingoConfig() {
    }

    public static void load() {
        if (Files.notExists(FILE)) {
            saveCurrent();
            return;
        }

        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonElement rootElement = GSON.fromJson(reader, JsonElement.class);
            if (rootElement == null || !rootElement.isJsonObject()) {
                throw new JsonParseException("Expected a JSON object");
            }

            JsonObject root = rootElement.getAsJsonObject();
            JsonArray array = root.has("additionalLanguages") && root.get("additionalLanguages").isJsonArray()
                    ? root.getAsJsonArray("additionalLanguages")
                    : new JsonArray();

            List<String> loaded = new ArrayList<>();
            for (JsonElement element : array) {
                if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    loaded.add(element.getAsString());
                }
            }
            additionalLanguages = normalize(loaded);
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            // keep a malformed file untouched so player can repair it manually
            additionalLanguages = List.of();
            LOGGER.error("Could not read config file {}. Polylingo is using an empty language list.", FILE, exception);
        }
    }

    public static List<String> getAdditionalLanguages() {
        return additionalLanguages;
    }

    public static synchronized void setAdditionalLanguages(Collection<String> languages) {
        additionalLanguages = normalize(languages);
        saveCurrent();
    }

    public static Path getConfigPath() {
        return FILE;
    }

    private static List<String> normalize(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String id = value.trim();
                if (!id.isEmpty() && id.matches("[a-z0-9_]+")) {
                    result.add(id);
                }
            }
        }
        return List.copyOf(result);
    }

    private static synchronized void saveCurrent() {
        JsonObject root = new JsonObject();
        JsonArray languages = new JsonArray();
        for (String id : additionalLanguages) {
            languages.add(id);
        }
        root.add("additionalLanguages", languages);

        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save config file {}.", FILE, exception);
        }
    }
}
