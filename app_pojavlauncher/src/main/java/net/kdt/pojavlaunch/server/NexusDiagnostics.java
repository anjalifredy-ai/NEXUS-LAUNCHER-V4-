package net.kdt.pojavlaunch.server;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class NexusDiagnostics {
    private NexusDiagnostics() {}

    @NonNull
    public static String buildReport(@NonNull Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nexus Launcher diagnostics\n");
        sb.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append('\n');

        AdaptiveRamManager.Snapshot ram = AdaptiveRamManager.inspect(ctx);
        sb.append("Memory (ActivityManager.MemoryInfo)\n");
        sb.append("  avail=").append(ram.availMemMb).append("MB total=").append(ram.totalMemMb)
                .append("MB low=").append(ram.lowMemory)
                .append(" maxSafeHeap=").append(ram.maxSafeHeapMb).append("MB\n");
        if (ram.notes != null) sb.append("  notes=").append(ram.notes).append('\n');
        sb.append("  freeStorage=").append(ram.freeStorageMb).append("MB\n");
        sb.append('\n');

        sb.append("MultiRT runtimes\n");
        try {
            List<Runtime> rts = MultiRTUtils.getRuntimes();
            if (rts == null || rts.isEmpty()) sb.append("  (none installed)\n");
            else for (Runtime r : rts)
                sb.append("  ").append(r.name).append(" java=").append(r.javaVersion).append('\n');
        } catch (Exception e) {
            sb.append("  error reading runtimes: ").append(e.getMessage()).append('\n');
        }
        sb.append('\n');

        sb.append("Server controller\n");
        ServerController ctrl = ServerController.get();
        sb.append("  running=").append(ctrl.isRunning()).append(" ready=").append(ctrl.isReady()).append('\n');
        String active = ctrl.getActiveProfileId();
        sb.append("  activeProfileId=").append(active != null ? active : "(none)").append('\n');
        for (ServerProfile p : ctrl.getProfiles()) {
            sb.append("  profile name=").append(p.name)
                    .append(" soft=").append(p.software)
                    .append(" mc=").append(p.mcVersion)
                    .append(" state=").append(p.runtimeState)
                    .append(" heap=").append(p.heapMb).append("MB\n");
            if (p.lastError != null && !p.lastError.isEmpty())
                sb.append("    lastError=").append(p.lastError).append('\n');
            if (p.lastCrashSummary != null && !p.lastCrashSummary.isEmpty())
                sb.append("    crash=").append(p.lastCrashSummary).append('\n');
        }
        sb.append('\n');

        PublicAccessManager.Snapshot pub = ctrl.publicAccess().getSnapshot();
        sb.append("Public access: ").append(pub.status);
        if (pub.message != null) sb.append(" — ").append(pub.message);
        sb.append('\n');

        sb.append("\nPaths (no secrets)\n");
        sb.append("  DIR_GAME_HOME set=").append(Tools.DIR_GAME_HOME != null).append('\n');
        sb.append("  serversRoot=").append(ServerPaths.serversRoot().getAbsolutePath()).append('\n');

        sb.append("\nRecovery tips\n");
        if (ram.availMemMb < 600)
            sb.append("- Free RAM is low; close apps or lower server heap.\n");
        if (ram.freeStorageMb >= 0 && ram.freeStorageMb < 1024)
            sb.append("- Storage under 1GB free; free space before downloads.\n");
        sb.append("- Install MultiRT Java matching the server MC version (17+ for 1.18+).\n");
        sb.append("- Port conflicts: change server port or stop other listeners.\n");
        sb.append("- Public access needs an external relay on CGNAT networks.\n");
        return sb.toString();
    }

    @NonNull
    public static File exportToFile(@NonNull Context ctx) throws Exception {
        File dir = new File(Tools.DIR_GAME_HOME, "nexus_diagnostics");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create diagnostics folder");
        String name = "diag_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
        File out = new File(dir, name);
        try (FileWriter w = new FileWriter(out)) {
            w.write(buildReport(ctx));
        }
        return out;
    }
}
