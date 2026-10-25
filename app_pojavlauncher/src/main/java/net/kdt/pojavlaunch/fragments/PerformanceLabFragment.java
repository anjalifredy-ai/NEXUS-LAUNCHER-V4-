package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.server.AdaptiveRamManager;
import net.kdt.pojavlaunch.server.ServerController;

/**
 * Real device memory/storage diagnostics for hosting and launch decisions.
 * Preset buttons show recommended heap values from AdaptiveRamManager — they do not
 * invent TPS/FPS claims.
 */
public class PerformanceLabFragment extends Fragment {
    public static final String TAG = "PerformanceLabFragment";

    private TextView mMetrics;

    public PerformanceLabFragment() {
        super(R.layout.fragment_performance_lab);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mMetrics = view.findViewById(R.id.perf_metrics);
        refresh();

        Button refresh = view.findViewById(R.id.perf_btn_refresh);
        Button low = view.findViewById(R.id.perf_btn_low);
        Button bal = view.findViewById(R.id.perf_btn_balanced);
        Button perf = view.findViewById(R.id.perf_btn_performance);
        Button servers = view.findViewById(R.id.perf_btn_servers);
        Button back = view.findViewById(R.id.perf_btn_back);

        if (refresh != null) refresh.setOnClickListener(v -> refresh());
        if (low != null) low.setOnClickListener(v -> showPreset("Low"));
        if (bal != null) bal.setOnClickListener(v -> showPreset("Balanced"));
        if (perf != null) perf.setOnClickListener(v -> showPreset("Performance"));
        if (servers != null) servers.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ServerManagerFragment.class, ServerManagerFragment.TAG, null));
        if (back != null) back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
    }

    private void refresh() {
        AdaptiveRamManager.Snapshot s = AdaptiveRamManager.inspect(requireContext());
        boolean srv = ServerController.get().isRunning();
        boolean ready = ServerController.get().isReady();
        StringBuilder sb = new StringBuilder();
        sb.append("Device memory\n");
        sb.append("  Total: ").append(s.totalMemMb).append(" MB\n");
        sb.append("  Available: ").append(s.availMemMb).append(" MB\n");
        sb.append("  Low-memory threshold: ").append(s.thresholdMb).append(" MB\n");
        sb.append("  System lowMemory flag: ").append(s.lowMemory).append('\n');
        sb.append("  Free storage: ").append(s.freeStorageMb).append(" MB\n\n");
        sb.append("Server heap recommendations\n");
        sb.append("  Auto / recommended: ").append(s.recommendedHeapMb).append(" MB\n");
        sb.append("  Max safe: ").append(s.maxSafeHeapMb).append(" MB\n");
        sb.append("  Low preset: ").append(s.lowPresetMb).append(" MB\n");
        sb.append("  Balanced preset: ").append(s.balancedPresetMb).append(" MB\n");
        sb.append("  Performance preset: ").append(s.performancePresetMb).append(" MB\n\n");
        if (s.notes != null && !s.notes.isEmpty()) {
            sb.append("Notes: ").append(s.notes).append("\n\n");
        }
        sb.append("Server process\n");
        sb.append("  Running: ").append(srv).append('\n');
        sb.append("  Ready (Done signal): ").append(ready).append('\n');
        sb.append('\n');
        sb.append("TPS/FPS are not invented here. Apply heap presets on the Server form, then restart.");
        if (mMetrics != null) mMetrics.setText(sb.toString());
    }

    private void showPreset(String name) {
        AdaptiveRamManager.Snapshot s = AdaptiveRamManager.inspect(requireContext());
        int mb;
        if ("Low".equals(name)) mb = s.lowPresetMb;
        else if ("Performance".equals(name)) mb = s.performancePresetMb;
        else mb = s.balancedPresetMb;
        Toast.makeText(requireContext(),
                name + " preset recommends " + mb + " MB heap. Set this on Server Hosting form and restart the server.",
                Toast.LENGTH_LONG).show();
        refresh();
    }
}
