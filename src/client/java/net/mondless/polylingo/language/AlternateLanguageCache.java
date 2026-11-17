package net.mondless.polylingo.language;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.LanguageInfo;

import java.util.HashMap;
import java.util.Map;

public final class AlternateLanguageCache {
    private static final Map<String, ClientLanguage> CACHE = new HashMap<>();

    private AlternateLanguageCache() {
    }

    public static synchronized ClientLanguage get(String languageId) {
        Minecraft minecraft = Minecraft.getInstance();
        LanguageInfo info = minecraft.getLanguageManager().getLanguage(languageId);
        if (info == null) {
            return null;
        }

        ClientLanguage existing = CACHE.get(languageId);
        if (existing != null) {
            return existing;
        }

        try {
            ClientLanguage created = ClientLanguage.loadFrom(
                    minecraft.getResourceManager(),
                    java.util.List.of(languageId),
                    info.bidirectional()
            );
            CACHE.put(languageId, created);
            return created;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static synchronized void clear() {
        CACHE.clear();
    }
}
