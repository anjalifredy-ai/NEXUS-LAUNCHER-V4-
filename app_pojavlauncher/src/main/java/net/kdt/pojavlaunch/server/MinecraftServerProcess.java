package net.kdt.pojavlaunch.server;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MinecraftServerProcess {
    private static final String TAG = "NexusServerProc";

    public interface LogListener {
        void onLogLine(String line);
        void onProcessExited(int exitCode);
        void onServerReady();
    }

    private final ServerProfile profile;
    private final int heapMb;
    private final String javaBinaryPath;
    private final String extraJvmArgs;
    private @Nullable Process process;
    private @Nullable OutputStreamWriter stdin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<LogListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> logBuffer = new ArrayList<>();
    private static final int LOG_CAP = 500;
    private final AtomicReference<String> lastCrashHint = new AtomicReference<>(null);

    public MinecraftServerProcess(@NonNull ServerProfile profile, int heapMb,
                                  @NonNull String javaBinaryPath, @Nullable String extraJvmArgs) {
        this.profile = profile;
        this.heapMb = heapMb;
        this.javaBinaryPath = javaBinaryPath;
        this.extraJvmArgs = extraJvmArgs != null ? extraJvmArgs : "";
    }

    public void addListener(LogListener l) { listeners.add(l); }
    public void removeListener(LogListener l) { listeners.remove(l); }
    public boolean isRunning() { return running.get() && process != null && process.isAlive(); }
    public boolean isReady() { return ready.get() && isRunning(); }
    @Nullable public String getLastCrashHint() { return lastCrashHint.get(); }
    public synchronized List<String> snapshotLogs() { return new ArrayList<>(logBuffer); }

    public synchronized void start() throws Exception {
        if (isRunning()) throw new Exception("Server already running");
        File root = profile.resolveRoot(ServerPaths.serversRoot());
        File jar = profile.serverJar(root);
        if (!jar.isFile() || jar.length() < 1000)
            throw new Exception("Server jar missing. Install the server first: " + jar.getAbsolutePath());
        File javaBin = new File(javaBinaryPath);
        if (!javaBin.isFile())
            throw new Exception("Java binary not found: " + javaBinaryPath);
        //noinspection ResultOfMethodCallIgnored
        javaBin.setExecutable(true);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin.getAbsolutePath());
        cmd.add("-Xms" + Math.max(128, heapMb / 2) + "M");
        cmd.add("-Xmx" + heapMb + "M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:+ParallelRefProcEnabled");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Djdk.lang.Process.launchMechanism=FORK");
        if (extraJvmArgs != null && !extraJvmArgs.trim().isEmpty()) {
            for (String part : extraJvmArgs.trim().split("\\s+")) {
                if (!part.isEmpty()) cmd.add(part);
            }
        }
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        cmd.add("nogui");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root);
        pb.redirectErrorStream(true);

        File jreHome = javaBin.getParentFile() != null ? javaBin.getParentFile().getParentFile() : null;
        String ld = buildLdLibraryPath(jreHome);
        if (ld != null && !ld.isEmpty()) {
            String existing = pb.environment().get("LD_LIBRARY_PATH");
            if (existing == null || existing.isEmpty()) pb.environment().put("LD_LIBRARY_PATH", ld);
            else pb.environment().put("LD_LIBRARY_PATH", ld + ":" + existing);
        }
        if (jreHome != null) pb.environment().put("JAVA_HOME", jreHome.getAbsolutePath());

        Log.i(TAG, "Starting: " + cmd);
        ready.set(false);
        lastCrashHint.set(null);
        process = pb.start();
        stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        running.set(true);

        Thread t = new Thread(this::readLoop, "nexus-server-log");
        t.setDaemon(true);
        t.start();
        Thread waiter = new Thread(() -> {
            try {
                int code = process.waitFor();
                running.set(false);
                ready.set(false);
                for (LogListener l : listeners) {
                    try { l.onProcessExited(code); } catch (Throwable ignored) {}
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "nexus-server-wait");
        waiter.setDaemon(true);
        waiter.start();
    }

    private static String buildLdLibraryPath(@Nullable File jreHome) {
        if (jreHome == null || !jreHome.isDirectory()) return null;
        StringBuilder sb = new StringBuilder();
        String[] rel = new String[] {
                "lib", "lib/jli", "lib/server", "lib/client",
                "lib/aarch64", "lib/arm", "lib/amd64", "lib/i386",
                "jre/lib", "jre/lib/aarch64", "jre/lib/arm", "jre/lib/amd64"
        };
        for (String r : rel) {
            File d = new File(jreHome, r);
            if (d.isDirectory()) {
                if (sb.length() > 0) sb.append(':');
                sb.append(d.getAbsolutePath());
            }
        }
        return sb.toString();
    }

    public synchronized void sendCommand(@NonNull String command) throws Exception {
        if (!isRunning() || stdin == null) throw new Exception("Server is not running");
        stdin.write(command);
        if (!command.endsWith("\n")) stdin.write("\n");
        stdin.flush();
    }

    public synchronized void stopGracefully(long timeoutMs) {
        if (!isRunning()) {
            running.set(false);
            ready.set(false);
            return;
        }
        try { sendCommand("stop"); } catch (Exception e) {
            Log.w(TAG, "stop command failed", e);
        }
        long deadline = System.currentTimeMillis() + Math.max(3000, timeoutMs);
        while (isRunning() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(200); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (isRunning() && process != null) {
            process.destroy();
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            if (isRunning()) process.destroyForcibly();
        }
        running.set(false);
        ready.set(false);
        try { if (stdin != null) stdin.close(); } catch (Exception ignored) {}
        stdin = null;
        process = null;
    }

    private void readLoop() {
        Process p = process;
        if (p == null) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                appendLog(line);
                detectCrashHints(line);
                if (!ready.get() && isReadinessLine(line)) {
                    ready.set(true);
                    for (LogListener l : listeners) {
                        try { l.onServerReady(); } catch (Throwable ignored) {}
                    }
                }
                for (LogListener l : listeners) {
                    try { l.onLogLine(line); } catch (Throwable ignored) {}
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "log reader ended", e);
        }
    }

    private static boolean isReadinessLine(String line) {
        String s = line.toLowerCase();
        if (s.contains("done (") && s.contains("s)!")) return true;
        if (s.contains("for help, type \"help\"")) return true;
        if (s.contains("listening on")) return true;
        if (s.contains("started the minecraft server")) return true;
        return false;
    }

    private void detectCrashHints(String line) {
        String s = line.toLowerCase();
        if (s.contains("unsupportedclassversionerror") || s.contains("class file version"))
            lastCrashHint.set("Java version too old for this server. Install a newer MultiRT JRE.");
        else if (s.contains("outofmemoryerror") || s.contains("java.lang.outofmemory"))
            lastCrashHint.set("Out of memory. Lower heap or close other apps.");
        else if (s.contains("failed to bind to port") || s.contains("address already in use"))
            lastCrashHint.set("Port already in use. Change port or stop the other process.");
        else if (s.contains("you need to agree to the eula"))
            lastCrashHint.set("EULA not accepted.");
    }

    private synchronized void appendLog(String line) {
        logBuffer.add(line);
        while (logBuffer.size() > LOG_CAP) logBuffer.remove(0);
    }
}
