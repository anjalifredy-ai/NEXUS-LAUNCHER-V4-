package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ClientVersionCatalog {
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static long cacheAt;
    private static List<Entry> cache;

    public static final class Entry {
        public final String id;
        public final String type;
        public final String releaseTime;
        public final boolean installed;

        public Entry(String id, String type, String releaseTime, boolean installed) {
            this.id = id;
            this.type = type;
            this.releaseTime = releaseTime;
            this.installed = installed;
        }
    }

    private ClientVersionCatalog() {}

    @NonNull
    public static synchronized List<Entry> load(boolean forceNetwork) throws Exception {
        if (!forceNetwork && cache != null && System.currentTimeMillis() - cacheAt < 10 * 60 * 1000L)
            return cache;
        String json = DownloadUtils.downloadString(MANIFEST);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray versions = root.getAsJsonArray("versions");
        File versionsDir = new File(Tools.DIR_GAME_NEW, "versions");
        List<Entry> out = new ArrayList<>();
        for (JsonElement el : versions) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            String type = o.has("type") ? o.get("type").getAsString() : "unknown";
            String time = o.has("releaseTime") ? o.get("releaseTime").getAsString() : "";
            boolean installed = new File(versionsDir, id).isDirectory()
                    || new File(versionsDir, id + "/" + id + ".json").isFile();
            out.add(new Entry(id, type, time, installed));
        }
        cache = out;
        cacheAt = System.currentTimeMillis();
        return out;
    }

    @NonNull
    public static List<Entry> filter(@NonNull List<Entry> all, @Nullable String query,
                                     boolean releases, boolean snapshots, boolean installedOnly) {
        String q = query != null ? query.trim().toLowerCase() : "";
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (installedOnly && !e.installed) continue;
            if (releases || snapshots) {
                if ("release".equals(e.type) && !releases) continue;
                if ("snapshot".equals(e.type) && !snapshots) continue;
                if (!"release".equals(e.type) && !"snapshot".equals(e.type) && !releases) continue;
            }
            if (!q.isEmpty() && !e.id.toLowerCase().contains(q)) continue;
            out.add(e);
        }
        return out;
    }
}
