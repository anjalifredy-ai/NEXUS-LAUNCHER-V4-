package net.kdt.pojavlaunch.fragments;

import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.server.AdaptiveRamManager;
import net.kdt.pojavlaunch.server.ServerController;
import net.kdt.pojavlaunch.server.ServerProfile;

public class PerformanceLabFragment extends Fragment {
    public static final String TAG = "PerformanceLabFragment";

    private TextView mBody;

    public PerformanceLabFragment() {
        super(R.layout.fragment_performance_lab);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mBody = view.findViewById(R.id.perf_metrics);
        view.findViewById(R.id.perf_btn_refresh).setOnClickListener(v -> render());
        view.findViewById(R.id.perf_btn_back).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        view.findViewById(R.id.perf_btn_servers).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ServerManagerFragment.class, ServerManagerFragment.TAG, null));
        View diag = view.findViewById(R.id.perf_btn_diag);
        if (diag != null) diag.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), DiagnosticsFragment.class, DiagnosticsFragment.TAG, null));

        view.findViewById(R.id.perf_btn_low).setOnClickListener(v -> showPreset("Low", ServerProfile.RamPreset.LOW));
        view.findViewById(R.id.perf_btn_balanced).setOnClickListener(v -> showPreset("Balanced", ServerProfile.RamPreset.BALANCED));
        view.findViewById(R.id.perf_btn_performance).setOnClickListener(v -> showPreset("Performance", ServerProfile.RamPreset.PERFORMANCE));

        render();
    }

    private void render() {
        AdaptiveRamManager.Snapshot s = AdaptiveRamManager.inspect(requireContext());
        StringBuilder sb = new StringBuilder();
        sb.append("Device memory (ActivityManager)\n");
        sb.append("  Available: ").append(s.availMemMb).append(" MB\n");
        sb.append("  Total: ").append(s.totalMemMb).append(" MB\n");
        sb.append("  Low-memory flag: ").append(s.lowMemory).append("\n");
        sb.append("  Free storage: ").append(s.freeStorageMb >= 0 ? s.freeStorageMb + " MB" : "unknown").append("\n");
        if (s.notes != null) sb.append("  ").append(s.notes).append("\n");

        Runtime rt = Runtime.getRuntime();
        sb.append("\nLauncher JVM (this process)\n");
        sb.append("  used=").append((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)).append(" MB");
        sb.append("  total=").append(rt.totalMemory() / (1024 * 1024)).append(" MB");
        sb.append("  max=").append(rt.maxMemory() / (1024 * 1024)).append(" MB\n");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                PowerManager pm = requireContext().getSystemService(PowerManager.class);
                if (pm != null) {
                    int status = pm.getCurrentThermalStatus();
                    sb.append("\nThermal status: ").append(thermalName(status)).append("\n");
                }
            } catch (Throwable t) {
                sb.append("\nThermal status: unavailable\n");
            }
        } else {
            sb.append("\nThermal status: requires Android 10+\n");
        }

        ServerController ctrl = ServerController.get();
        sb.append("\nServer process\n");
        sb.append("  running=").append(ctrl.isRunning()).append(" ready=").append(ctrl.isReady()).append("\n");
        String aid = ctrl.getActiveProfileId();
        if (aid != null) {
            ServerProfile p = ctrl.find(aid);
            if (p != null) {
                sb.append("  profile=").append(p.name).append(" state=").append(p.runtimeState).append("\n");
                sb.append("  configured heap=").append(p.heapMb).append(" MB (JVM -Xmx; not live RSS)\n");
                if (p.uptimeStartMs > 0)
                    sb.append("  uptime≈").append((System.currentTimeMillis() - p.uptimeStartMs) / 1000).append("s\n");
                if (p.lastError != null) sb.append("  lastError=").append(p.lastError).append("\n");
            }
        } else {
            sb.append("  no active server\n");
        }

        sb.append("\nHeap recommendations (not auto-applied)\n");
        sb.append("  Auto: ").append(s.recommendedHeapMb).append(" MB\n");
        sb.append("  Low: ").append(s.lowPresetMb).append(" MB\n");
        sb.append("  Balanced: ").append(s.balancedPresetMb).append(" MB\n");
        sb.append("  Performance: ").append(s.performancePresetMb).append(" MB\n");
        sb.append("  Max safe: ").append(s.maxSafeHeapMb).append(" MB\n");

        sb.append("\nSafe tips\n");
        if (s.availMemMb < 800) sb.append("- Available RAM is tight; prefer Low heap.\n");
        if (s.freeStorageMb >= 0 && s.freeStorageMb < 2048) sb.append("- Free storage is low.\n");
        sb.append("- Do not allocate all device RAM to Java.\n");
        sb.append("- CPU % for child server is not measured (Android limitation).\n");

        if (mBody != null) mBody.setText(sb.toString());
    }

    private static String thermalName(int s) {
        switch (s) {
            case PowerManager.THERMAL_STATUS_NONE: return "NONE";
            case PowerManager.THERMAL_STATUS_LIGHT: return "LIGHT";
            case PowerManager.THERMAL_STATUS_MODERATE: return "MODERATE";
            case PowerManager.THERMAL_STATUS_SEVERE: return "SEVERE";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "CRITICAL";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "EMERGENCY";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "SHUTDOWN";
            default: return "UNKNOWN";
        }
    }

    private void showPreset(String name, ServerProfile.RamPreset preset) {
        AdaptiveRamManager.Snapshot s = AdaptiveRamManager.inspect(requireContext());
        int mb = AdaptiveRamManager.heapForPreset(s, preset);
        Toast.makeText(requireContext(),
                name + " recommends " + mb + " MB heap. Set on Server Hosting and restart.",
                Toast.LENGTH_LONG).show();
    }
}
