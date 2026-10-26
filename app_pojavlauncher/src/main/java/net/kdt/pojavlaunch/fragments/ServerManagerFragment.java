package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.AdapterView;
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
import net.kdt.pojavlaunch.server.ServerCommandHints;
import net.kdt.pojavlaunch.server.ServerController;
import net.kdt.pojavlaunch.server.ServerProfile;
import net.kdt.pojavlaunch.server.ServerVersionCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ServerManagerFragment extends Fragment implements ServerController.Listener {
    public static final String TAG = "ServerManagerFragment";

    private TextView mStatus, mRamInfo, mLanInfo, mPublicInfo, mConsole;
    private Spinner mProfileSpinner;
    private EditText mName, mVersion, mPort, mHeap, mMaxPlayers, mCommand;
    private CheckBox mEula, mPaper, mFabric, mOffline;
    private final List<ServerProfile> mProfiles = new ArrayList<>();
    private boolean mSpinnerLock;

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
        mFabric = view.findViewById(R.id.server_use_fabric);
        mOffline = view.findViewById(R.id.server_offline);

        mPaper.setOnCheckedChangeListener((b, c) -> { if (c) mFabric.setChecked(false); });
        mFabric.setOnCheckedChangeListener((b, c) -> { if (c) mPaper.setChecked(false); });

        view.findViewById(R.id.server_btn_create).setOnClickListener(v -> createProfile());
        view.findViewById(R.id.server_btn_save).setOnClickListener(v -> saveCurrent());
        view.findViewById(R.id.server_btn_install).setOnClickListener(v -> {
            ServerProfile p = selected();
            if (p == null) { toast("Create a profile first"); return; }
            applyForm(p);
            try { ServerController.get().upsert(p); } catch (Exception e) { toast(e.getMessage()); return; }
            mConsole.append("Installing…\n");
            ServerController.get().installAsync(requireContext(), p.id);
        });
        view.findViewById(R.id.server_btn_start).setOnClickListener(v -> {
            ServerProfile p = selected();
            if (p == null) { toast("Create a profile first"); return; }
            applyForm(p);
            try { ServerController.get().upsert(p); } catch (Exception e) { toast(e.getMessage()); return; }
            mConsole.append("Starting…\n");
            ServerController.get().startAsync(requireContext(), p.id);
        });
        view.findViewById(R.id.server_btn_stop).setOnClickListener(v ->
                ServerController.get().stopAsync(requireContext()));
        view.findViewById(R.id.server_btn_restart).setOnClickListener(v ->
                ServerController.get().restartAsync(requireContext()));
        view.findViewById(R.id.server_btn_public).setOnClickListener(v -> togglePublic());
        view.findViewById(R.id.server_btn_backup).setOnClickListener(v -> {
            ServerProfile p = selected();
            if (p == null) return;
            toast("Backup starting…");
            ServerController.get().backupAsync(p.id, new ServerController.BackupCallback() {
                @Override public void onSuccess(java.io.File zip) {
                    Tools.runOnUiThread(() -> toast("Backup: " + zip.getName()));
                }
                @Override public void onError(Exception e) {
                    Tools.runOnUiThread(() -> toast("Backup failed: " + e.getMessage()));
                }
            });
        });
        view.findViewById(R.id.server_btn_send).setOnClickListener(v -> {
            try {
                ServerController.get().sendCommand(mCommand.getText().toString());
                mCommand.setText("");
            } catch (Exception e) { toast(e.getMessage()); }
        });
        view.findViewById(R.id.server_btn_refresh_ram).setOnClickListener(v -> updateRam());
        view.findViewById(R.id.server_btn_delete).setOnClickListener(v -> confirmDelete());
        view.findViewById(R.id.server_btn_back).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        View pick = view.findViewById(R.id.server_btn_pick_version);
        if (pick != null) pick.setOnClickListener(v -> pickVersion());
        View hints = view.findViewById(R.id.server_btn_hints);
        if (hints != null) hints.setOnClickListener(v -> showCommandHints());

        mProfileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mSpinnerLock || position < 0 || position >= mProfiles.size()) return;
                fillForm(mProfiles.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

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
        p.name = mName.getText().toString().trim().isEmpty() ? "My Server" : mName.getText().toString().trim();
        p.mcVersion = mVersion.getText().toString().trim().isEmpty() ? "1.20.4" : mVersion.getText().toString().trim();
        applyForm(p);
        try {
            ServerController.get().upsert(p);
            ServerController.get().reloadCatalog();
            refreshSpinner();
            selectById(p.id);
            toast("Profile created — accept EULA, Install, then Start");
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void saveCurrent() {
        ServerProfile p = selected();
        if (p == null) { toast("No profile"); return; }
        applyForm(p);
        try { ServerController.get().upsert(p); toast("Saved"); refreshSpinner(); }
        catch (Exception e) { toast(e.getMessage()); }
    }

    private void confirmDelete() {
        ServerProfile p = selected();
        if (p == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete server?")
                .setMessage("Deletes config and server files for \"" + p.name + "\". Worlds in that folder will be removed.")
                .setPositiveButton("Delete", (d, w) -> {
                    try {
                        ServerController.get().delete(p.id);
                        ServerController.get().reloadCatalog();
                        refreshSpinner();
                        toast("Deleted");
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
        if (mFabric.isChecked()) p.software = ServerProfile.Software.FABRIC;
        else if (mPaper.isChecked()) p.software = ServerProfile.Software.PAPER;
        else p.software = ServerProfile.Software.VANILLA;
        p.authMode = mOffline.isChecked() ? ServerProfile.AuthMode.OFFLINE : ServerProfile.AuthMode.ONLINE;
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
        mFabric.setChecked(p.software == ServerProfile.Software.FABRIC);
        mOffline.setChecked(p.authMode == ServerProfile.AuthMode.OFFLINE);
        mStatus.setText("State: " + p.runtimeState + (p.lastError != null ? (" — " + p.lastError) : ""));
    }

    private void refreshSpinner() {
        mProfiles.clear();
        mProfiles.addAll(ServerController.get().getProfiles());
        List<String> labels = new ArrayList<>();
        for (ServerProfile p : mProfiles)
            labels.add(p.name + " · " + p.software.label + " " + p.mcVersion);
        mSpinnerLock = true;
        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels);
        mProfileSpinner.setAdapter(ad);
        mSpinnerLock = false;
        if (!mProfiles.isEmpty()) {
            mProfileSpinner.setSelection(0);
            fillForm(mProfiles.get(0));
        }
    }

    private void selectById(String id) {
        for (int i = 0; i < mProfiles.size(); i++) {
            if (mProfiles.get(i).id.equals(id)) {
                mProfileSpinner.setSelection(i);
                fillForm(mProfiles.get(i));
                return;
            }
        }
    }

    private @Nullable ServerProfile selected() {
        int i = mProfileSpinner.getSelectedItemPosition();
        if (i < 0 || i >= mProfiles.size()) return null;
        return mProfiles.get(i);
    }

    private void updateRam() {
        AdaptiveRamManager.Snapshot s = AdaptiveRamManager.inspect(requireContext());
        mRamInfo.setText("RAM avail " + s.availMemMb + "MB · rec heap " + s.recommendedHeapMb
                + "MB · max safe " + s.maxSafeHeapMb + "MB"
                + (s.notes != null ? (" · " + s.notes) : ""));
    }

    private void updateLan() {
        String lan = LanNetworkHelper.getLanIpv4();
        int port = 25565;
        try { port = Integer.parseInt(mPort.getText().toString().trim()); } catch (Exception ignored) {}
        boolean up = ServerController.get().isRunning() && LanNetworkHelper.canConnect("127.0.0.1", port, 400);
        mLanInfo.setText("LAN: " + (lan != null ? lan : "?") + ":" + port
                + (ServerController.get().isReady() ? " · READY" : (up ? " · port open" : " · start server")));
    }

    private void updatePublic() {
        PublicAccessManager.Snapshot s = ServerController.get().publicAccess().getSnapshot();
        mPublicInfo.setText(s.message != null ? s.message : ("Public: " + s.status));
    }

    private void pickVersion() {
        toast("Loading versions…");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ServerProfile.Software soft = ServerProfile.Software.VANILLA;
                if (mFabric != null && mFabric.isChecked()) soft = ServerProfile.Software.FABRIC;
                else if (mPaper != null && mPaper.isChecked()) soft = ServerProfile.Software.PAPER;
                final java.util.List<String> ids = new java.util.ArrayList<>();
                if (soft == ServerProfile.Software.PAPER) {
                    ids.addAll(ServerVersionCatalog.listPaperVersions());
                } else if (soft == ServerProfile.Software.FABRIC) {
                    for (ServerVersionCatalog.VersionEntry e : ServerVersionCatalog.listFabricGameVersions(true))
                        ids.add(e.id);
                } else {
                    for (ServerVersionCatalog.VersionEntry e : ServerVersionCatalog.listVanillaReleases(false))
                        ids.add(e.id);
                }
                if (ids.size() > 80) ids.subList(80, ids.size()).clear();
                Tools.runOnUiThread(() -> {
                    if (ids.isEmpty()) { toast("No versions returned"); return; }
                    String[] arr = ids.toArray(new String[0]);
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Select Minecraft version")
                            .setItems(arr, (d, which) -> mVersion.setText(arr[which]))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> toast("Version list failed: " + e.getMessage()));
            }
        });
    }

    private void showCommandHints() {
        ServerProfile.Software soft = ServerProfile.Software.VANILLA;
        if (mFabric != null && mFabric.isChecked()) soft = ServerProfile.Software.FABRIC;
        else if (mPaper != null && mPaper.isChecked()) soft = ServerProfile.Software.PAPER;
        java.util.List<String> hints = ServerCommandHints.forSoftware(soft);
        String[] arr = hints.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle("Send command")
                .setItems(arr, (d, which) -> {
                    mCommand.setText(arr[which]);
                    try {
                        ServerController.get().sendCommand(arr[which]);
                        toast("Sent: " + arr[which]);
                    } catch (Exception e) {
                        toast(e.getMessage());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String m) {
        if (m != null && getContext() != null)
            Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onStateChanged(@NonNull ServerProfile profile) {
        Tools.runOnUiThread(() -> {
            mStatus.setText("State: " + profile.runtimeState
                    + (profile.lastError != null ? (" — " + profile.lastError) : ""));
            updateLan();
        });
    }

    @Override
    public void onLogLine(@NonNull String line) {
        Tools.runOnUiThread(() -> {
            mConsole.append(line);
            mConsole.append("\n");
        });
    }

    @Override
    public void onCatalogChanged() {
        Tools.runOnUiThread(this::refreshSpinner);
    }

    @Override
    public void onServerReady(@NonNull ServerProfile profile) {
        Tools.runOnUiThread(() -> {
            toast("Server READY — join " + LanNetworkHelper.getLanIpv4() + ":" + profile.port);
            updateLan();
        });
    }
}
