package net.kdt.pojavlaunch.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class FriendsVoiceFragment extends Fragment {
    public static final String TAG = "FriendsVoiceFragment";
    private static final int REQ_MIC = 4401;

    private TextView mStatus;
    private boolean mMicGranted;
    private boolean mMuted = true;

    public FriendsVoiceFragment() {
        super(R.layout.fragment_friends_voice);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mStatus = view.findViewById(R.id.voice_status);
        Button requestMic = view.findViewById(R.id.voice_btn_mic);
        Button mute = view.findViewById(R.id.voice_btn_mute);
        Button openMods = view.findViewById(R.id.voice_btn_svc_mod);
        Button back = view.findViewById(R.id.voice_btn_back);

        refreshStatus();

        requestMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                mMicGranted = true;
                toast("Microphone already granted");
                refreshStatus();
            } else {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }
        });

        mute.setOnClickListener(v -> {
            mMuted = !mMuted;
            toast(mMuted ? "Muted (no active voice session)" : "Unmuted (no active voice session)");
            refreshStatus();
        });

        openMods.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance("mod").getArguments()));

        back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
    }

    private void refreshStatus() {
        mMicGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        mStatus.setText(
                "Nexus party voice: not connected\n"
                        + "Reason: multi-device voice needs signaling + STUN/TURN (WebRTC). "
                        + "No fake peers are shown.\n\n"
                        + "Mic permission: " + (mMicGranted ? "granted" : "not granted") + "\n"
                        + "Local mute toggle: " + (mMuted ? "muted" : "unmuted") + "\n\n"
                        + "In-game proximity voice: install a compatible client mod "
                        + "from the Mod store into your profile, and the matching server "
                        + "mod/plugin on your hosted server."
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_MIC) {
            mMicGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            refreshStatus();
        }
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
