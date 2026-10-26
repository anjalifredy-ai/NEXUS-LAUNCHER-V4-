package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

public class DownloadsFragment extends Fragment implements TaskCountListener {
    public static final String TAG = "DownloadsFragment";

    private TextView mStatus, mDetail;

    public DownloadsFragment() { super(R.layout.fragment_downloads); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mStatus = view.findViewById(R.id.dl_status);
        mDetail = view.findViewById(R.id.dl_detail);
        view.findViewById(R.id.dl_refresh).setOnClickListener(v -> render());
        view.findViewById(R.id.dl_mods).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("mod").getArguments()));
        view.findViewById(R.id.dl_versions).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), VersionBrowserFragment.class, VersionBrowserFragment.TAG, null));
        view.findViewById(R.id.dl_home).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        render();
    }

    @Override public void onStart() {
        super.onStart();
        ProgressKeeper.addTaskCountListener(this);
    }

    @Override public void onStop() {
        ProgressKeeper.removeTaskCountListener(this);
        super.onStop();
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        Tools.runOnUiThread(this::render);
    }

    private void render() {
        int n = ProgressKeeper.getTaskCount();
        mStatus.setText(n == 0
                ? "No active downloads"
                : n + " active task(s) in ProgressKeeper");
        StringBuilder sb = new StringBuilder();
        sb.append("Nexus uses the launcher engine's ProgressKeeper for version, mod, and asset downloads.\n\n");
        if (n == 0) {
            sb.append("Empty state: start a version install (Profiles / Play) or install a mod from Mods library.\n");
        } else {
            sb.append("Tasks running in background. Progress shows on the relevant engine screens.\n");
            sb.append("Do not force-close the app while downloads are active.\n");
        }
        sb.append("\nShortcuts:\n• Mods — Modrinth\n• Versions — Mojang list\n• Home spinner — client install on Play");
        mDetail.setText(sb.toString());
    }
}
