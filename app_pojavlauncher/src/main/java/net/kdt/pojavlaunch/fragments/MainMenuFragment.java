package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.MineButton;
import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    private mcVersionSpinner mVersionSpinner;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        MineButton mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        if (mNewsButton != null)
            mNewsButton.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(), NexusHubFragment.class, NexusHubFragment.TAG, null));

        if (mDiscordButton != null)
            mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));

        if (mCustomControlButton != null)
            mCustomControlButton.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(), ControlButtonFragment.class, ControlButtonFragment.TAG, null));

        if (mInstallJarButton != null)
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));

        if (mEditProfileButton != null)
            mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        if (mPlayButton != null)
            mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        if (mShareLogsButton != null)
            mShareLogsButton.setOnClickListener(v -> shareLog(requireContext()));

        if (mOpenDirectoryButton != null)
            mOpenDirectoryButton.setOnClickListener(v -> openPath(v.getContext(), getCurrentProfileDirectory(), false));

        View.OnClickListener openServers = v ->
                Tools.swapFragment(requireActivity(), ServerListFragment.class, ServerListFragment.TAG, null);
        View.OnClickListener openHub = v ->
                Tools.swapFragment(requireActivity(), NexusHubFragment.class, NexusHubFragment.TAG, null);
        View.OnClickListener openMods = v ->
                Tools.swapFragment(requireActivity(), ContentLibraryFragment.class, ContentLibraryFragment.TAG,
                        ContentLibraryFragment.newInstance("mod").getArguments());
        View.OnClickListener openPerf = v ->
                Tools.swapFragment(requireActivity(), PerformanceLabFragment.class, PerformanceLabFragment.TAG, null);
        View.OnClickListener openSettings = v ->
                Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class, "SETTINGS_FRAGMENT", null);
        View.OnClickListener openProfiles = v ->
                Tools.swapFragment(requireActivity(), ProfilesFragment.class, ProfilesFragment.TAG, null);

        bind(view, R.id.server_host_button, openServers);
        bind(view, R.id.nexus_hub_button, openHub);
        bind(view, R.id.mods_browser_button, openMods);
        bind(view, R.id.perf_lab_button, openPerf);
        bind(view, R.id.nav_home, v -> Tools.backToMainMenu(requireActivity()));
        bind(view, R.id.nav_profiles, openProfiles);
        bind(view, R.id.nav_mods, openMods);
        bind(view, R.id.nav_settings, openSettings);

        ProgressKeeper.waitUntilDone(() -> {
            /* optional ready hook */
        });
    }

    private void bind(View root, int id, View.OnClickListener listener) {
        View v = root.findViewById(id);
        if (v != null) v.setOnClickListener(listener);
    }

    private File getCurrentProfileDirectory() {
        try {
            LauncherProfiles.load();
            return Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());
        } catch (Exception e) {
            return new File(Tools.DIR_GAME_NEW);
        }
    }

    private void runInstallerWithConfirmation(boolean force) {
        // Prefer existing install-jar path when available
        try {
            Tools.installMod(requireActivity(), force);
        } catch (Throwable t) {
            Toast.makeText(requireContext(), "Install: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
