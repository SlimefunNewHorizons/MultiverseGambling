package com.chagui68.multiversegambling.economy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Which sBank wallet (modality) each player gambles with. sBank 1.5 keeps one bank per
 * modality, so the casino lets the player choose and remembers it across restarts.
 */
public final class Wallets {

    public static final String SURVIVAL = "survival";

    /** Modalities with their own economy. The lobby has none, so it is not a wallet. */
    public static final List<String> ECONOMIES =
            List.of(SURVIVAL, "oneblock", "skyblock", "onechunk", "clasico", "laboratorio");

    private final Map<UUID, String> chosen = new ConcurrentHashMap<>();

    /** @return the canonical modality name, or {@code null} when it is not a wallet */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("bskyblock")) {
            value = "skyblock";
        }
        return ECONOMIES.contains(value) ? value : null;
    }

    /**
     * The wallet to use: the chosen one, else the world's, else survival, taking the first
     * whose bank exists. {@code null} when none does, so nothing can be staked.
     */
    public static String pick(String chosen, String worldModality, Predicate<String> exists) {
        for (String candidate : new String[]{chosen, normalise(worldModality), SURVIVAL}) {
            if (candidate != null && exists.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public String get(UUID id) {
        return chosen.get(id);
    }

    public void put(UUID id, String wallet) {
        chosen.put(id, wallet);
    }

    public void load(File file) {
        if (!file.isFile()) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException ignored) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            String wallet = normalise(props.getProperty(key));
            try {
                if (wallet != null) {
                    chosen.put(UUID.fromString(key), wallet);
                }
            } catch (IllegalArgumentException ignored) {
                // a hand-edited line: skip it
            }
        }
    }

    public boolean save(File file) {
        Properties props = new Properties();
        chosen.forEach((id, wallet) -> props.setProperty(id.toString(), wallet));
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            try (OutputStream out = Files.newOutputStream(file.toPath())) {
                props.store(out, "MultiverseGambling: sBank wallet chosen by each player");
            }
            return true;
        } catch (IOException error) {
            return false;
        }
    }
}
