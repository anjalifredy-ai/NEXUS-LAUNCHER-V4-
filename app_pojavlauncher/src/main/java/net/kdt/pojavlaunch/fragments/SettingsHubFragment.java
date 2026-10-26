package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceExperimentalFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceMiscellaneousFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;

public class SettingsHubFragment extends Fragment {
    public static final String TAG = "SettingsHubFragment";

    public SettingsHubFragment() { super(R.layout.fragment_settings_hub); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        go(view, R.id.sh_general, LauncherPreferenceFragment.class, "SETTINGS_FRAGMENT");
        go(view, R.id.sh_java, LauncherPreferenceJavaFragment.class, "JAVA_SETTINGS");
        go(view, R.id.sh_video, LauncherPreferenceVideoFragment.class, "VIDEO_SETTINGS");
        go(view, R.id.sh_control, LauncherPreferenceControlFragment.class, "CONTROL_SETTINGS");
        go(view, R.id.sh_misc, LauncherPreferenceMiscellaneousFragment.class, "MISC_SETTINGS");
        go(view, R.id.sh_experimental, LauncherPreferenceExperimentalFragment.class, "EXP_SETTINGS");
        view.findViewById(R.id.sh_perf).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), PerformanceLabFragment.class, PerformanceLabFragment.TAG, null));
        view.findViewById(R.id.sh_diag).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), DiagnosticsFragment.class, DiagnosticsFragment.TAG, null));
        view.findViewById(R.id.sh_home).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
    }

    private void go(View root, int id, Class<? extends Fragment> cls, String tag) {
        View v = root.findViewById(id);
        if (v != null) v.setOnClickListener(x -> Tools.swapFragment(requireActivity(), cls, tag, null));
    }
}
