package net.kdt.pojavlaunch.server;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServerVersionCatalog {
    private static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String PAPER_PROJECTS = "https://api.papermc.io/v2/projects/paper";
    private static final String FABRIC_GAME = "https://meta.fabricmc.net/v2/versions/game";
    private static final String FABRIC_LOADER = "https://meta.fabricmc.net/v2/versions/loader";

    private ServerVersionCatalog() {}

    public static final class VersionEntry {
        public final String id;
        public final String type;
        public VersionEntry(String id, String type) { this.id = id; this.type = type; }
        @NonNull @Override public String toString() { return id + (type != null ? " (" + type + ")" : ""); }
    }

    public static final class PaperBuild {
        public final int build;
        public final String version;
        public PaperBuild(int build, String version) { this.build = build; this.version = version; }
    }

    @NonNull
    public static List<VersionEntry> listVanillaReleases(boolean includeSnapshots) throws Exception {
        JsonObject manifest = JsonParser.parseString(DownloadUtils.downloadString(MOJANG_MANIFEST)).getAsJsonObject();
        List<VersionEntry> out = new ArrayList<>();
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            String type = v.get("type").getAsString();
            if (!includeSnapshots && !"release".equals(type)) continue;
            out.add(new VersionEntry(v.get("id").getAsString(), type));
        }
        return out;
    }

    @NonNull
    public static List<String> listPaperVersions() throws Exception {
        JsonObject root = JsonParser.parseString(DownloadUtils.downloadString(PAPER_PROJECTS)).getAsJsonObject();
        List<String> out = new ArrayList<>();
        JsonArray versions = root.getAsJsonArray("versions");
        if (versions != null) for (JsonElement el : versions) out.add(el.getAsString());
        Collections.reverse(out);
        return out;
    }

    @NonNull
    public static List<PaperBuild> listPaperBuilds(String mcVersion) throws Exception {
        String url = "https://api.papermc.io/v2/projects/paper/versions/" + mcVersion + "/builds";
        JsonObject root = JsonParser.parseString(DownloadUtils.downloadString(url)).getAsJsonObject();
        List<PaperBuild> out = new ArrayList<>();
        JsonArray builds = root.getAsJsonArray("builds");
        if (builds != null) for (JsonElement el : builds) {
            JsonObject b = el.getAsJsonObject();
            out.add(new PaperBuild(b.get("build").getAsInt(), mcVersion));
        }
        Collections.reverse(out);
        return out;
    }

    @NonNull
    public static List<VersionEntry> listFabricGameVersions(boolean stableOnly) throws Exception {
        JsonArray arr = JsonParser.parseString(DownloadUtils.downloadString(FABRIC_GAME)).getAsJsonArray();
        List<VersionEntry> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            boolean stable = o.has("stable") && o.get("stable").getAsBoolean();
            if (stableOnly && !stable) continue;
            out.add(new VersionEntry(o.get("version").getAsString(), stable ? "release" : "snapshot"));
        }
        return out;
    }

    @NonNull
    public static String latestFabricLoader() throws Exception {
        JsonArray arr = JsonParser.parseString(DownloadUtils.downloadString(FABRIC_LOADER)).getAsJsonArray();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("stable") && o.get("stable").getAsBoolean()) return o.get("version").getAsString();
        }
        if (arr.size() > 0) return arr.get(0).getAsJsonObject().get("version").getAsString();
        throw new Exception("No Fabric loader versions available");
    }

    public static int requiredJavaMajor(String mcVersion) {
        try {
            String[] p = mcVersion.split("\\.");
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1].replaceAll("[^0-9].*", "")) : 0;
            int patch = 0;
            if (p.length > 2) {
                try { patch = Integer.parseInt(p[2].replaceAll("[^0-9].*", "")); } catch (Exception ignored) {}
            }
            if (major >= 1 && minor >= 21) return 21;
            if (major >= 1 && minor == 20 && patch >= 5) return 21;
            if (major >= 1 && minor >= 18) return 17;
            if (major >= 1 && minor >= 17) return 16;
            return 8;
        } catch (Exception e) {
            return 17;
        }
    }
}
