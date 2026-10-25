package net.kdt.pojavlaunch.server;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import androidx.annotation.NonNull;

public final class AdaptiveRamManager {
    private AdaptiveRamManager() {}

    public static final class Snapshot {
        public long totalMemMb;
        public long availMemMb;
        public long thresholdMb;
        public boolean lowMemory;
        public long freeStorageMb;
        public int recommendedHeapMb;
        public int maxSafeHeapMb;
        public int lowPresetMb;
        public int balancedPresetMb;
        public int performancePresetMb;
        public String notes;
    }

    @NonNull
    public static Snapshot inspect(@NonNull Context context) {
        Snapshot s = new Snapshot();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            s.totalMemMb = mi.totalMem / (1024 * 1024);
            s.availMemMb = mi.availMem / (1024 * 1024);
            s.thresholdMb = mi.threshold / (1024 * 1024);
            s.lowMemory = mi.lowMemory;
        }
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long free = Build.VERSION.SDK_INT >= 18
                    ? stat.getAvailableBlocksLong() * stat.getBlockSizeLong()
                    : (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
            s.freeStorageMb = free / (1024 * 1024);
        } catch (Throwable t) {
            s.freeStorageMb = -1;
        }
        long budget = Math.max(0, s.availMemMb - Math.max(256, s.thresholdMb));
        int auto = (int) Math.min(1024, Math.max(256, budget * 35 / 100));
        int maxSafe = (int) Math.min(1536, Math.max(256, budget * 50 / 100));
        if (s.availMemMb < 700) {
            auto = 384; maxSafe = 512;
            s.notes = "Low available memory; use Vanilla, view-distance 6-8, few players.";
        } else if (s.availMemMb < 1200) {
            auto = Math.min(auto, 512); maxSafe = Math.min(maxSafe, 768);
            s.notes = "Moderate memory; prefer light configs over heavy modpacks.";
        } else {
            s.notes = "Heap is a recommendation; free memory changes under load.";
        }
        if (s.freeStorageMb >= 0 && s.freeStorageMb < 1024) {
            s.notes = (s.notes == null ? "" : s.notes + " ") + "Low free storage (<1 GB).";
        }
        s.recommendedHeapMb = align64(auto);
        s.maxSafeHeapMb = align64(maxSafe);
        s.lowPresetMb = align64(Math.min(s.recommendedHeapMb, Math.max(256, auto * 70 / 100)));
        s.balancedPresetMb = s.recommendedHeapMb;
        s.performancePresetMb = align64(Math.min(maxSafe, Math.max(s.recommendedHeapMb, auto * 120 / 100)));
        return s;
    }

    public static int heapForPreset(@NonNull Snapshot s, @NonNull ServerProfile.RamPreset preset) {
        switch (preset) {
            case LOW: return s.lowPresetMb;
            case PERFORMANCE: return s.performancePresetMb;
            case BALANCED: return s.balancedPresetMb;
            case AUTO:
            default: return s.recommendedHeapMb;
        }
    }

    public static int clampHeap(int requestedMb, int maxSafeMb) {
        int v = Math.max(256, requestedMb);
        return align64(Math.min(v, Math.max(256, maxSafeMb)));
    }

    private static int align64(int mb) {
        return Math.max(256, (mb / 64) * 64);
    }
}
