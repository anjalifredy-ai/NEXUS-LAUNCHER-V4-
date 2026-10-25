package net.kdt.pojavlaunch.server;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerController {
    private static final String TAG = "NexusServerCtrl";
    private static final ServerController INSTANCE = new ServerController();

    public interface Listener {
        void onStateChanged(@NonNull ServerProfile profile);
        void onLogLine(@NonNull String line);
        void onCatalogChanged();
        void onServerReady(@NonNull ServerProfile profile);
    }

    public interface BackupCallback {
        void onSuccess(File zip);
        void onError(Exception e);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final PublicAccessManager publicAccess = new PublicAccessManager();
    private @Nullable MinecraftServerProcess activeProcess;
    private @Nullable String activeProfileId;
    private List<ServerProfile> profiles = new ArrayList<>();

    private ServerController() {}
    public static ServerController get() { return INSTANCE; }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public synchronized void reloadCatalog() {
        profiles = ServerCatalog.load();
        for (Listener l : listeners) l.onCatalogChanged();
    }

    public synchronized List<ServerProfile> getProfiles() { return new ArrayList<>(profiles); }

    public synchronized @Nullable ServerProfile find(String id) {
        for (ServerProfile p : profiles) if (p.id.equals(id)) return p;
        return null;
    }

    public synchronized void upsert(ServerProfile p) throws Exception {
        if (!ServerPaths.isSafeDirectoryName(p.directoryName))
            throw new Exception("Unsafe directory name");
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(p.id)) { profiles.set(i, p); found = true; break; }
        }
        if (!found) profiles.add(p);
        ServerCatalog.save(profiles);
        for (Listener l : listeners) l.onCatalogChanged();
    }

    public synchronized void delete(String id) throws Exception {
        if (id.equals(activeProfileId) && activeProcess != null && activeProcess.isRunning())
            throw new Exception("Stop the server before deleting it");
        ServerProfile victim = find(id);
        profiles.removeIf(p -> p.id.equals(id));
        ServerCatalog.save(profiles);
        if (victim != null) deleteTree(victim.resolveRoot(ServerPaths.serversRoot()));
        for (Listener l : listeners) l.onCatalogChanged();
    }

    public synchronized ServerProfile duplicate(String id) throws Exception {
        ServerProfile src = find(id);
        if (src == null) throw new Exception("Unknown profile");
        ServerProfile p = new ServerProfile();
        p.name = src.name + " (copy)";
        p.software = src.software;
        p.mcVersion = src.mcVersion;
        p.softwareBuild = src.softwareBuild;
        p.port = src.port;
        p.maxPlayers = src.maxPlayers;
        p.viewDistance = src.viewDistance;
        p.simulationDistance = src.simulationDistance;
        p.heapMb = src.heapMb;
        p.ramPreset = src.ramPreset;
        p.jvmArgs = src.jvmArgs;
        p.authMode = src.authMode;
        p.whitelist = src.whitelist;
        p.requiredJavaMajor = src.requiredJavaMajor;
        p.preferredRuntimeName = src.preferredRuntimeName;
        p.eulaAccepted = false;
        p.directoryName = "server_" + System.currentTimeMillis();
        upsert(p);
        return p;
    }

    public PublicAccessManager publicAccess() { return publicAccess; }
    public @Nullable String getActiveProfileId() { return activeProfileId; }
    public boolean isRunning() { return activeProcess != null && activeProcess.isRunning(); }
    public boolean isReady() { return activeProcess != null && activeProcess.isReady(); }

    public void installAsync(@NonNull Context ctx, @NonNull String profileId) {
        io.execute(() -> {
            ServerProfile p = find(profileId);
            if (p == null) return;
            try {
                p.runtimeState = ServerProfile.State.INSTALLING;
                notifyState(p);
                ServerInstaller.install(p, new ServerInstaller.Progress() {
                    @Override public void onStatus(String message) { emitLog(message); }
                    @Override public void onProgress(long current, long total) {}
                });
                p.runtimeState = ServerProfile.State.STOPPED;
                p.lastError = null;
                upsert(p);
                notifyState(p);
                emitLog("Install complete.");
            } catch (Exception e) {
                Log.e(TAG, "install failed", e);
                p.runtimeState = ServerProfile.State.CRASHED;
                p.lastError = e.getMessage();
                notifyState(p);
                emitLog("Install error: " + e.getMessage());
            }
        });
    }

    public void startAsync(@NonNull Context ctx, @NonNull String profileId) {
        io.execute(() -> {
            try { startBlocking(ctx, profileId); }
            catch (Exception e) {
                Log.e(TAG, "start failed", e);
                ServerProfile p = find(profileId);
                if (p != null) {
                    p.runtimeState = ServerProfile.State.CRASHED;
                    p.lastError = e.getMessage();
                    p.lastCrashSummary = e.getMessage();
                    notifyState(p);
                }
                emitLog("Start error: " + e.getMessage());
            }
        });
    }

    private void startBlocking(Context ctx, String profileId) throws Exception {
        if (isRunning()) throw new Exception("A server is already running. Stop it first.");
        ServerProfile p = find(profileId);
        if (p == null) throw new Exception("Unknown profile");
        if (!p.eulaAccepted) throw new Exception("Accept the Minecraft EULA first");

        AdaptiveRamManager.Snapshot ram = AdaptiveRamManager.inspect(ctx);
        if (p.ramPreset == ServerProfile.RamPreset.AUTO || p.heapMb <= 0)
            p.heapMb = AdaptiveRamManager.heapForPreset(ram, p.ramPreset);
        int heap = AdaptiveRamManager.clampHeap(p.heapMb, ram.maxSafeHeapMb);
        p.heapMb = heap;

        if (ram.availMemMb < 400)
            throw new Exception("Available memory too low (" + ram.availMemMb + " MB).");
        if (ram.freeStorageMb >= 0 && ram.freeStorageMb < 512)
            throw new Exception("Free storage too low (" + ram.freeStorageMb + " MB).");

        if (!ServerInstaller.isInstalled(p)) {
            p.runtimeState = ServerProfile.State.INSTALLING;
            notifyState(p);
            ServerInstaller.install(p, new ServerInstaller.Progress() {
                @Override public void onStatus(String message) { emitLog(message); }
                @Override public void onProgress(long current, long total) {}
            });
        }
        ServerPropertiesEditor.writeCore(p);

        ServerRuntimePicker.Choice rt = ServerRuntimePicker.pick(p);
        emitLog("Using runtime " + rt.name + " (Java " + rt.javaMajor + ") heap " + heap + "M");

        p.runtimeState = ServerProfile.State.STARTING;
        p.lastStartMs = System.currentTimeMillis();
        notifyState(p);

        MinecraftServerProcess proc = new MinecraftServerProcess(
                p, heap, rt.javaBinary.getAbsolutePath(), p.jvmArgs);
        proc.addListener(new MinecraftServerProcess.LogListener() {
            @Override public void onLogLine(String line) { emitLog(line); }
            @Override public void onServerReady() {
                p.runtimeState = ServerProfile.State.RUNNING;
                p.lastSuccessfulStartMs = System.currentTimeMillis();
                p.uptimeStartMs = System.currentTimeMillis();
                p.lastError = null;
                notifyState(p);
                for (Listener l : listeners) {
                    try { l.onServerReady(p); } catch (Throwable ignored) {}
                }
            }
            @Override public void onProcessExited(int exitCode) {
                String hint = proc.getLastCrashHint();
                p.runtimeState = exitCode == 0 ? ServerProfile.State.STOPPED : ServerProfile.State.CRASHED;
                p.lastStopMs = System.currentTimeMillis();
                p.uptimeStartMs = 0;
                if (exitCode != 0) {
                    p.lastError = hint != null ? hint : ("Process exited with code " + exitCode);
                    p.lastCrashSummary = p.lastError;
                }
                activeProcess = null;
                activeProfileId = null;
                notifyState(p);
                try {
                    Intent stop = new Intent(ctx, ServerHostService.class);
                    stop.setAction(ServerHostService.ACTION_STOP);
                    ctx.startService(stop);
                } catch (Throwable ignored) {}
            }
        });
        proc.start();
        activeProcess = proc;
        activeProfileId = p.id;
        notifyState(p);

        Intent fg = new Intent(ctx, ServerHostService.class);
        fg.setAction(ServerHostService.ACTION_START);
        fg.putExtra(ServerHostService.EXTRA_PROFILE_ID, p.id);
        fg.putExtra(ServerHostService.EXTRA_PORT, p.port);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(fg);
        else ctx.startService(fg);
    }

    public void stopAsync(@NonNull Context ctx) {
        io.execute(() -> {
            MinecraftServerProcess proc = activeProcess;
            ServerProfile p = activeProfileId != null ? find(activeProfileId) : null;
            if (p != null) { p.runtimeState = ServerProfile.State.STOPPING; notifyState(p); }
            if (proc != null) proc.stopGracefully(25000);
            activeProcess = null;
            activeProfileId = null;
            if (p != null) {
                p.runtimeState = ServerProfile.State.STOPPED;
                p.lastStopMs = System.currentTimeMillis();
                p.uptimeStartMs = 0;
                notifyState(p);
            }
            publicAccess.disable();
            try {
                Intent stop = new Intent(ctx, ServerHostService.class);
                stop.setAction(ServerHostService.ACTION_STOP);
                ctx.startService(stop);
            } catch (Throwable ignored) {}
        });
    }

    public void restartAsync(@NonNull Context ctx) {
        final String id = activeProfileId;
        io.execute(() -> {
            if (id == null) return;
            MinecraftServerProcess proc = activeProcess;
            if (proc != null) proc.stopGracefully(25000);
            activeProcess = null;
            activeProfileId = null;
            try { startBlocking(ctx, id); }
            catch (Exception e) { emitLog("Restart failed: " + e.getMessage()); }
        });
    }

    public void sendCommand(String cmd) throws Exception {
        if (activeProcess == null || !activeProcess.isRunning()) throw new Exception("Server not running");
        activeProcess.sendCommand(cmd);
    }

    public void backupAsync(@NonNull String profileId, @Nullable BackupCallback cb) {
        io.execute(() -> {
            try {
                ServerProfile p = find(profileId);
                if (p == null) throw new Exception("Unknown profile");
                File zip = WorldBackupManager.create(p);
                emitLog("Backup created: " + zip.getName());
                if (cb != null) cb.onSuccess(zip);
            } catch (Exception e) {
                if (cb != null) cb.onError(e);
            }
        });
    }

    private void notifyState(ServerProfile p) {
        for (Listener l : listeners) {
            try { l.onStateChanged(p); } catch (Throwable ignored) {}
        }
    }

    private void emitLog(String line) {
        for (Listener l : listeners) {
            try { l.onLogLine(line); } catch (Throwable ignored) {}
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
