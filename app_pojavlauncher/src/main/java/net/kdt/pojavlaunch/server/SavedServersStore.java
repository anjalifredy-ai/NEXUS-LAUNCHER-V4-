package net.kdt.pojavlaunch.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SavedServersStore {
    private static final Gson GSON = new Gson();
    private static final Type LIST = new TypeToken<List<SavedServerEntry>>(){}.getType();

    private SavedServersStore() {}

    private static File file() {
        File f = new File(Tools.DIR_GAME_HOME, "nexus_multiplayer_servers.json");
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        return f;
    }

    public static synchronized List<SavedServerEntry> load() {
        File f = file();
        if (!f.isFile()) return new ArrayList<>();
        try (FileReader r = new FileReader(f)) {
            List<SavedServerEntry> list = GSON.fromJson(r, LIST);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static synchronized void save(List<SavedServerEntry> list) throws Exception {
        try (FileWriter w = new FileWriter(file())) {
            GSON.toJson(list, LIST, w);
        }
    }

    public static SavedServerEntry createDefault() {
        SavedServerEntry e = new SavedServerEntry();
        e.id = UUID.randomUUID().toString();
        return e;
    }
}
