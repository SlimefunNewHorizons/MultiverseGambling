package com.chagui68.multiversegambling.config;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.i18n.Language;
import com.chagui68.multiversegambling.util.Text;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Every string the plugin shows, resolved in the language of whoever is reading.
 *
 * <p>Messages live in {@code plugins/MultiverseGambling/lang/<code>.yml} — one file
 * per language, created on first start and free to be edited. A missing key never
 * fails silently: it shows up as {@code [missing message: key]} so translators can
 * spot the hole immediately.</p>
 *     * <p>Lookup order for a player is: the language they picked, then the language of
 * their Minecraft client (while {@code language.follow-client} is on), then
 * {@code language.default}, then English.</p>
 *
 * <p>Game names and descriptions live in the code and can be translated with a
 * {@code catalog.<game-id>.name} / {@code catalog.<game-id>.description} block.</p>
 */
public final class Messages {

    private static final String MISSING_PREFIX = "&c[missing message: ";

    private final MultiverseGamblingPlugin plugin;
    private final File folder;
    private final Map<String, YamlConfiguration> bundles = new LinkedHashMap<>();

    public Messages(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "lang");
        reload();
    }

    /** Reloads every language file from disk, creating the bundled ones when missing. */
    public void reload() {
        bundles.clear();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the language folder: " + folder.getPath());
        }
        migrateLegacyFile();
        for (Language language : Language.values()) {
            File file = new File(folder, language.fileName());
            if (!file.exists()) {
                plugin.saveResource("lang/" + language.fileName(), false);
            }
            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
            // A key added by a newer build falls back to the text shipped in the jar, so a
            // file saved by an older version never shows a raw key.
            java.io.InputStream shipped = plugin.getResource("lang/" + language.fileName());
            if (shipped != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(shipped, java.nio.charset.StandardCharsets.UTF_8)));
            }
            bundles.put(language.code(), loaded);
        }
        File[] extra = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (extra != null) {
            for (File file : extra) {
                String code = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                bundles.computeIfAbsent(code, key -> YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    /** Older builds shipped a single messages.yml; keep the admin's edits as English. */
    private void migrateLegacyFile() {
        File legacy = new File(plugin.getDataFolder(), "messages.yml");
        File english = new File(folder, Language.EN.fileName());
        if (!legacy.isFile() || english.exists()) {
            return;
        }
        try {
            Files.copy(legacy.toPath(), english.toPath());
            plugin.getLogger().info("Migrated messages.yml into lang/en.yml; messages are now per language.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not migrate messages.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ languages

    /** Every loaded language code, bundled or dropped in by an admin. */
    public List<String> locales() {
        return List.copyOf(bundles.keySet());
    }

    public boolean supports(String code) {
        return code != null && bundles.containsKey(code.trim().toLowerCase(Locale.ROOT));
    }

    /** Language used when the reader has no preference (console, other plugins). */
    public String defaultLocale() {
        String configured = plugin.config() == null ? null : plugin.config().defaultLanguage();
        if (configured == null) {
            return Language.EN.code();
        }
        String code = configured.trim().toLowerCase(Locale.ROOT);
        return bundles.containsKey(code) ? code : Language.EN.code();
    }

    /** Language the sender reads in. */
    public String localeOf(CommandSender sender) {
        return sender instanceof Player player ? localeOf(player) : defaultLocale();
    }

    /** Language the player reads in. */
    public String localeOf(Player player) {
        String chosen = plugin.languages().codeOf(player.getUniqueId());
        if (chosen != null && bundles.containsKey(chosen)) {
            return chosen;
        }
        if (plugin.config() != null && plugin.config().languageFollowClient()) {
            Locale client = player.locale();
            Language match = client == null ? null : Language.match(client.getLanguage());
            if (match != null && bundles.containsKey(match.code())) {
                return match.code();
            }
        }
        return defaultLocale();
    }

    /** Human readable list such as {@code en (English), es (Español)}. */
    public String localeList() {
        List<String> parts = new ArrayList<>();
        for (String code : bundles.keySet()) {
            Language known = Language.match(code);
            parts.add(code + " (" + (known == null ? code : known.nativeName()) + ")");
        }
        return String.join(", ", parts);
    }

    // -------------------------------------------------------------------- lookup

    private String lookup(String locale, String key) {
        if (locale != null) {
            YamlConfiguration bundle = bundles.get(locale);
            String value = bundle == null ? null : bundle.getString(key);
            if (value != null) {
                return value;
            }
        }
        String fallback = defaultLocale();
        if (!fallback.equals(locale)) {
            YamlConfiguration bundle = bundles.get(fallback);
            String value = bundle == null ? null : bundle.getString(key);
            if (value != null) {
                return value;
            }
        }
        if (!Language.EN.code().equals(fallback)) {
            YamlConfiguration english = bundles.get(Language.EN.code());
            String value = english == null ? null : english.getString(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String resolve(String locale, String key) {
        String value = lookup(locale, key);
        return value == null ? MISSING_PREFIX + key + "]" : value;
    }

    private List<String> resolveList(String locale, String key) {
        List<String> lines = lookupList(locale, key);
        if (lines != null) {
            return lines;
        }
        List<String> missing = new ArrayList<>(1);
        missing.add(MISSING_PREFIX + key + "]");
        return missing;
    }

    private List<String> lookupList(String locale, String key) {
        for (String candidate : new String[]{locale, defaultLocale(), Language.EN.code()}) {
            if (candidate == null) {
                continue;
            }
            YamlConfiguration bundle = bundles.get(candidate);
            if (bundle == null) {
                continue;
            }
            List<String> lines = bundle.getStringList(key);
            if (!lines.isEmpty()) {
                return lines;
            }
            String single = bundle.getString(key);
            if (single != null) {
                return List.of(single);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------- getters

    /** Message in the default language. */
    public String get(String key, Object... replacements) {
        return Text.fill(resolve(null, key), replacements);
    }

    /** Message in an explicit language. */
    public String forLocale(String locale, String key, Object... replacements) {
        return Text.fill(resolve(locale, key), replacements);
    }

    /** Message in the language of the sender. */
    public String forSender(CommandSender sender, String key, Object... replacements) {
        return Text.fill(resolve(localeOf(sender), key), replacements);
    }

    /** Like {@link #get(String, Object...)} but returns the fallback when the key is absent. */
    public String getOr(String key, String fallback, Object... replacements) {
        String value = lookup(null, key);
        return value == null ? fallback : Text.fill(value, replacements);
    }

    /** Key in the language of the sender, with a fallback when the key is absent. */
    public String forSenderOr(CommandSender sender, String key, String fallback, Object... replacements) {
        String value = lookup(localeOf(sender), key);
        return value == null ? fallback : Text.fill(value, replacements);
    }

    /** Key in an explicit language, with a fallback when the key is absent. */
    public String forLocaleOr(String locale, String key, String fallback, Object... replacements) {
        String value = lookup(locale, key);
        return value == null ? fallback : Text.fill(value, replacements);
    }

    public String prefix() {
        return get("prefix");
    }

    public String prefixFor(CommandSender sender) {
        return forLocale(localeOf(sender), "prefix");
    }

    public String prefixed(String key, Object... replacements) {
        return prefix() + get(key, replacements);
    }

    /** Message with the prefix, in the language of the sender. */
    public String prefixedFor(CommandSender sender, String key, Object... replacements) {
        return prefixFor(sender) + forSender(sender, key, replacements);
    }

    public Component component(String key, Object... replacements) {
        return Text.c(get(key, replacements));
    }

    public Component componentFor(CommandSender sender, String key, Object... replacements) {
        return Text.c(prefixedFor(sender, key, replacements));
    }

    public Component componentPlainFor(CommandSender sender, String key, Object... replacements) {
        return Text.c(forSender(sender, key, replacements));
    }

    public List<String> lore(String key) {
        return Text.fillAll(resolveList(null, key));
    }

    public List<String> loreFor(CommandSender sender, String key, Object... replacements) {
        return Text.fillAll(resolveList(localeOf(sender), key), replacements);
    }

    public boolean has(String key) {
        return lookup(null, key) != null;
    }

    public boolean hasFor(String locale, String key) {
        return lookup(locale, key) != null;
    }

    // --------------------------------------------------------------- game catalogue

    /** Game name in the language of the viewer; {@code catalog.<id>.name} wins when present. */
    public String gameName(CommandSender viewer, String gameId, String fallback) {
        String locale = localeOf(viewer);
        String value = lookup(locale, "catalog." + gameId + ".name");
        return value == null ? fallback : value;
    }

    /** Game description in the language of the viewer, falling back to the built in one. */
    public List<String> gameDescription(CommandSender viewer, String gameId, List<String> fallback) {
        List<String> lines = lookupList(localeOf(viewer), "catalog." + gameId + ".description");
        return lines == null ? fallback : lines;
    }

    // ---------------------------------------------------------------------- sending

    /** Sends with the prefix, in the language of the receiver. */
    public void send(CommandSender to, String key, Object... replacements) {
        to.sendMessage(Text.c(prefixedFor(to, key, replacements)));
    }

    /** Sends without the prefix, for decorative lines, in the language of the receiver. */
    public void sendPlain(CommandSender to, String key, Object... replacements) {
        to.sendMessage(Text.c(forSender(to, key, replacements)));
    }

    /** Sends a decorative block already resolved, coloured, to one receiver. */
    public void sendRaw(CommandSender to, String legacyText) {
        to.sendMessage(Text.c(legacyText));
    }

    /** Broadcasts in each receiver's own language. */
    public void broadcastTo(Iterable<CommandSender> audience, String key, Object... replacements) {
        for (CommandSender sender : audience) {
            send(sender, key, replacements);
        }
    }
}
