package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.server.AdaptiveRamManager;
import net.kdt.pojavlaunch.server.LanNetworkHelper;
import net.kdt.pojavlaunch.server.PublicAccessManager;
import net.kdt.pojavlaunch.server.ServerController;
import net.kdt.pojavlaunch.server.ServerProfile;

import java.util.ArrayList;
import java.util.List;

public class ServerManagerFragment extends Fragment implements ServerController.Listener {
    public static final String TAG = "ServerManagerFragment";

    private TextView mStatus, mRamInfo, mLanInfo, mPublicInfo, mConsole;
    private Spinner mProfileSpinner;
    private EditText mName, mVersion, mPort, mHeap, mMaxPlayers, mCommand;
    private CheckBox mEula, mPaper;
    private final List<ServerProfile> mProfiles = new ArrayList<>();

    public ServerManagerFragment() { super(R.layout.fragment_server_manager); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mStatus = view.findViewById(R.id.server_status);
        mRamInfo = view.findViewById(R.id.server_ram_info);
        mLanInfo = view.findViewById(R.id.server_lan_info);
        mPublicInfo = view.findViewById(R.id.server_public_info);
        mConsole = view.findViewById(R.id.server_console);
        mConsole.setMovementMethod(new ScrollingMovementMethod());
        mProfileSpinner = view.findViewById(R.id.server_profile_spinner);
        mName = view.findViewById(R.id.server_name);
        mVersion = view.findViewById(R.id.server_version);
        mPort = view.findViewById(R.id.server_port);
        mHeap = view.findViewById(R.id.server_heap);
        mMaxPlayers = view.findViewById(R.id.server_max_players);
        mCommand = view.findViewById(R.id.server_command);
        mEula = view.findViewById(R.id.server_eula);
        mPaper = view.findViewById(R.id.server_use_paper);

        view.findViewById(R.id.server_btn_create).setOnClickListener(v -> createProfile());
        view.findViewById(R.id.server_btn_save).setOnClickListener(v -> saveCurrent());
        view.findViewById(R.id.server_btn_install).setOnClickListener(v -> {
            ServerProfile p = selected();
            if (p == null) return;
            applyForm(p);
            if (!p.eulaAccepted) { toast("Accept EULA first"); return; }
            try { ServerController.get().upsert(p); } catch (Exception e) { toast(e.getMessage()); return; }
            ServerController.get().installAsync(requireContext(), p.id);
        });
        view.findViewById(R.id.server_btn_start).setOnClickListener(v -> {
            ServerProfile p = selected();
            if (p == null) return;
            applyForm(p);
            try { ServerController.get().upsert(p); } catch (Exception e) { toast(e.getMessage()); return; }
            ServerController.get().startAsync(requireContext(), p.id);
        });
        view.findViewById(R.id.server_btn_stop).setOnClickListener(v -> ServerController.get().stopAsync(requireContext()));
        view.findViewById(R.id.server_btn_public).setOnClickListener(v -> togglePublic());
        view.findViewById(R.id.server_btn_send).setOnClickListener(v -> {
            try {
                ServerController.get().sendCommand(mCommand.getText().toString());
                mCommand.setText("");
            } catch (Exception e) { toast(e.getMessage()); }
        });
        view.findViewById(R.id.server_btn_refresh_ram).setOnClickListener(v -> updateRam());
        view.findViewById(R.id.server_btn_delete).setOnClickListener(v -> confirmDelete());

        ServerController.get().reloadCatalog();
        refreshSpinner();
        updateRam();
        updateLan();
        updatePublic();
    }

    @Override public void onStart() { super.onStart(); ServerController.get().addListener(this); }
    @Override public void onStop() { ServerController.get().removeListener(this); super.onStop(); }

    private void createProfile() {
        ServerProfile p = new ServerProfile();
        AdaptiveRamManager.Snapshot ram = AdaptiveRamManager.inspect(requireContext());
        p.heapMb = ram.recommendedHeapMb;
        p.ramPreset = ServerProfile.RamPreset.AUTO;
        p.name = "Server " + (mProfiles.size() + 1);
        try {
            ServerController.get().upsert(p);
            ServerController.get().reloadCatalog();
            refreshSpinner();
            toast("Profile created");
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void saveCurrent() {
        ServerProfile p = selected();
        if (p == null) { toast("Create a profile first"); return; }
        applyForm(p);
        try { ServerController.get().upsert(p); toast("Saved"); refreshSpinner(); }
        catch (Exception e) { toast(e.getMessage()); }
    }

    private void confirmDelete() {
        ServerProfile p = selected();
        if (p == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete server?")
                .setMessage("Deletes profile and files for \"" + p.name + "\".")
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        ServerController.get().delete(p.id);
                        ServerController.get().reloadCatalog();
                        refreshSpinner();
                    } catch (Exception e) { toast(e.getMessage()); }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void togglePublic() {
        ServerProfile p = selected();
        if (p == null) return;
        if (p.publicAccessEnabled) {
            ServerController.get().publicAccess().disable();
            p.publicAccessEnabled = false;
        } else {
            String lan = LanNetworkHelper.getLanIpv4();
            if (lan == null) lan = "127.0.0.1";
            PublicAccessManager.Snapshot s = ServerController.get().publicAccess()
                    .requestPublic(lan, p.port, !p.isOnlineMode());
            p.publicAccessEnabled = true;
            toast(s.message != null ? s.message : "Public access needs a relay");
        }
        try { ServerController.get().upsert(p); } catch (Exception ignored) {}
        updatePublic();
    }

    private void applyForm(ServerProfile p) {
        p.name = mName.getText().toString().trim().isEmpty() ? p.name : mName.getText().toString().trim();
        p.mcVersion = mVersion.getText().toString().trim();
        try { p.port = Integer.parseInt(mPort.getText().toString().trim()); } catch (Exception ignored) {}
        try { p.heapMb = Integer.parseInt(mHeap.getText().toString().trim()); } catch (Exception ignored) {}
        try { p.maxPlayers = Integer.parseInt(mMaxPlayers.getText().toString().trim()); } catch (Exception ignored) {}
        p.eulaAccepted = mEula.isChecked();
        p.software = mPaper.isChecked() ? ServerProfile.Software.PAPER : ServerProfile.Software.VANILLA;
        AdaptiveRamManager.Snapshot ram = AdaptiveRamManager.inspect(requireContext());
        p.heapMb = AdaptiveRamManager.clampHeap(p.heapMb, ram.maxSafeHeapMb);
        mHeap.setText(String.valueOf(p.heapMb));
    }

    private void fillForm(ServerProfile p) {
        mName.setText(p.name);
        mVersion.setText(p.mcVersion);
        mPort.setText(String.valueOf(p.port));
        mHeap.setText(String.valueOf(p.heapMb));
        mMaxPlayers.setText(String.valueOf(p.maxPlayers));
        mEula.setChecked(p.eulaAccepted);
        mPaper.setChecked(p.software == ServerProfile.Software.PAPER);
        mStatus.setText("State: " + p.runtimeState + (p.lastError != null ? (" — " + p.lastError) : ""));
    }

    private void refreshSpinner() {
        mProfiles.clear();
        mProfiles.addAll(ServerController.get().getProfiles());
        List<String> labels = new ArrayList<>();
        for (ServerProfile p : mProfiles) labels.add(p.name + " (" + p.mcVersion + ")");
        mProfileSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels));
        mProfileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < mProfiles.size()) fillForm(mProfiles.get(position));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        if (!mProfiles.isEmpty()) { mProfileSpinner.setSelection(0); fillForm(mProfiles.get(0)); }
    }

    private @Nullable ServerProfile selected() {
        int i = mProfileSpinner.getSelectedItemPosition();
        if (i < 0 || i >= mProfiles.size()) return null;
        return mProfiles.get(i);
    }

    private void updateRam() {
        AdaptiveRamManager.Snapshot s = AdaptiveRamManager.inspect(requireContext());
        mRamInfo.setText("RAM avail " + s.availMemMb + " MB / total " + s.totalMemMb
                + " · auto " + s.recommendedHeapMb + " · low " + s.lowPresetMb
                + " · perf " + s.performancePresetMb + " · max " + s.maxSafeHeapMb
                + " · storage " + s.freeStorageMb + " MB"
                + (s.notes != null ? ("\n" + s.notes) : ""));
    }

    private void updateLan() {
        String ip = LanNetworkHelper.getLanIpv4();
        ServerProfile p = selected();
        int port = p != null ? p.port : 25565;
        if (ip == null) mLanInfo.setText("LAN IP: unavailable. Port " + port);
        else {
            boolean up = ServerController.get().isRunning() && LanNetworkHelper.canConnect("127.0.0.1", port, 400);
            mLanInfo.setText("LAN: " + ip + ":" + port
                    + (ServerController.get().isReady() ? " · READY" : (up ? " · port open" : " · start server"))
                    + "\nSame Wi-Fi players use this address.");
        }
    }

    private void updatePublic() {
        PublicAccessManager.Snapshot s = ServerController.get().publicAccess().getSnapshot();
        if (s.status == PublicAccessManager.Status.DISABLED)
            mPublicInfo.setText("Public: off. Outside Wi-Fi needs a relay (CGNAT).");
        else if (s.status == PublicAccessManager.Status.NEEDS_RELAY)
            mPublicInfo.setText("Public: needs relay. " + (s.message != null ? s.message : ""));
        else if (s.status == PublicAccessManager.Status.ONLINE && s.publicHost != null)
            mPublicInfo.setText("Public: " + s.publicHost + ":" + s.publicPort);
        else mPublicInfo.setText("Public: " + s.status);
    }

    private void toast(String m) {
        if (m != null) Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onStateChanged(@NonNull ServerProfile profile) {
        Tools.runOnUiThread(() -> {
            ServerProfile sel = selected();
            if (sel != null && sel.id.equals(profile.id))
                mStatus.setText("State: " + profile.runtimeState + (profile.lastError != null ? (" — " + profile.lastError) : ""));
            updateLan();
            updatePublic();
        });
    }

    @Override
    public void onLogLine(@NonNull String line) {
        Tools.runOnUiThread(() -> { mConsole.append(line); mConsole.append("\n"); });
    }

    @Override
    public void onCatalogChanged() {
        Tools.runOnUiThread(this::refreshSpinner);
    }

    @Override
    public void onServerReady(@NonNull ServerProfile profile) {
        Tools.runOnUiThread(() -> {
            toast("Server ready — players can join");
            updateLan();
            updatePublic();
        });
    }
}
