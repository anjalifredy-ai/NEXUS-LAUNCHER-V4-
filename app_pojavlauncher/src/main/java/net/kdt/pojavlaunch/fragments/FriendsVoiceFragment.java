package net.kdt.pojavlaunch.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.voice.NexusVoiceSession;

import org.json.JSONObject;

public class FriendsVoiceFragment extends Fragment implements NexusVoiceSession.Listener {
    public static final String TAG = "FriendsVoiceFragment";
    private static final int REQ_MIC = 4401;

    private TextView mStatus;
    private EditText mSignalHost;
    private EditText mRoom;
    private EditText mName;

    public FriendsVoiceFragment() {
        super(R.layout.fragment_friends_voice);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mStatus = view.findViewById(R.id.voice_status);
        mSignalHost = view.findViewById(R.id.voice_signal_host);
        mRoom = view.findViewById(R.id.voice_room);
        mName = view.findViewById(R.id.voice_display_name);

        if (mSignalHost != null && mSignalHost.getText().length() == 0)
            mSignalHost.setText("127.0.0.1:7070");
        if (mRoom != null && mRoom.getText().length() == 0)
            mRoom.setText("nexus-party");
        if (mName != null && mName.getText().length() == 0)
            mName.setText("Player");

        Button requestMic = view.findViewById(R.id.voice_btn_mic);
        Button mute = view.findViewById(R.id.voice_btn_mute);
        Button join = view.findViewById(R.id.voice_btn_join);
        Button leave = view.findViewById(R.id.voice_btn_leave);
        Button openMods = view.findViewById(R.id.voice_btn_svc_mod);
        Button back = view.findViewById(R.id.voice_btn_back);

        refreshStatus();

        if (requestMic != null) requestMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                toast("Microphone already granted");
            } else {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }
            refreshStatus();
        });

        if (mute != null) mute.setOnClickListener(v -> {
            boolean next = !NexusVoiceSession.get().isMuted();
            NexusVoiceSession.get().setMuted(next);
            toast(next ? "Muted" : "Unmuted");
            refreshStatus();
        });

        if (join != null) join.setOnClickListener(v -> {
            String host = mSignalHost != null ? mSignalHost.getText().toString().trim() : "";
            String room = mRoom != null ? mRoom.getText().toString().trim() : "nexus-party";
            String name = mName != null ? mName.getText().toString().trim() : "Player";
            if (host.isEmpty()) { toast("Enter signaling host:port"); return; }
            NexusVoiceSession.get().join(host, room, name);
        });

        if (leave != null) leave.setOnClickListener(v -> NexusVoiceSession.get().leave());

        if (openMods != null) openMods.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance("mod").getArguments()));

        if (back != null) back.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
    }

    @Override public void onStart() {
        super.onStart();
        NexusVoiceSession.get().addListener(this);
        refreshStatus();
    }

    @Override public void onStop() {
        NexusVoiceSession.get().removeListener(this);
        super.onStop();
    }

    private void refreshStatus() {
        boolean mic = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        NexusVoiceSession s = NexusVoiceSession.get();
        StringBuilder sb = new StringBuilder();
        sb.append("State: ").append(s.getState()).append('\n');
        sb.append("User id: ").append(s.getUserId()).append('\n');
        if (s.getRoomId() != null && !s.getRoomId().isEmpty())
            sb.append("Room: ").append(s.getRoomId()).append('\n');
        sb.append("Muted: ").append(s.isMuted()).append('\n');
        sb.append("Mic permission: ").append(mic ? "granted" : "not granted").append('\n');
        if (s.getLastError() != null) sb.append("Last error: ").append(s.getLastError()).append('\n');
        sb.append('\n');
        sb.append("Signaling: TCP host:port (JSON lines). " +
                "IN_ROOM means signaling joined — not full WebRTC media until STUN/TURN + " +
                "PeerConnection is attached.\n\n");
        sb.append("In-game proximity voice: install Simple Voice Chat (or compatible) " +
                "from Mod store on client + matching server plugin/mod.");
        if (mStatus != null) mStatus.setText(sb.toString());
    }

    @Override
    public void onState(NexusVoiceSession.State state, @Nullable String detail) {
        Tools.runOnUiThread(() -> {
            refreshStatus();
            if (detail != null) toast(detail);
        });
    }

    @Override
    public void onPeerEvent(String type, @Nullable String userId) {
        Tools.runOnUiThread(() -> toast(type + (userId != null ? ": " + userId : "")));
    }

    @Override
    public void onRemoteSignal(@NonNull String from, @NonNull JSONObject payload) {
        // Hook for WebRTC PeerConnection when native stack is added.
        Tools.runOnUiThread(() -> toast("Signal from " + from + " (" + payload.optString("type", "?") + ")"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_MIC) refreshStatus();
    }

    private void toast(String m) {
        if (m != null && getContext() != null)
            Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
