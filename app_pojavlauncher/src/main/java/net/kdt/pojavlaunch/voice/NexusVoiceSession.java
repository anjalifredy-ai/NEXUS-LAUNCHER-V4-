package net.kdt.pojavlaunch.voice;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Party-voice session: line-delimited JSON signaling over TCP.
 * <p>
 * WebRTC media requires a native WebRTC stack + STUN/TURN. This class implements
 * real room join/leave and SDP/ICE message relay so a WebRTC PeerConnection can
 * be attached later. Without a reachable signaling host, status stays DISCONNECTED.
 * Never reports CONNECTED unless the socket is open and a join-ack is received.
 *
 * Signaling message types (JSON, one object per line):
 *  {"type":"join","room":"...","userId":"...","displayName":"..."}
 *  {"type":"joined","room":"...","peers":[...]}
 *  {"type":"peer-joined","userId":"..."}
 *  {"type":"peer-left","userId":"..."}
 *  {"type":"signal","to":"...","from":"...","payload":{...}}  // SDP/ICE
 *  {"type":"leave"}
 *  {"type":"error","message":"..."}
 */
public final class NexusVoiceSession {
    private static final String TAG = "NexusVoice";
    private static final NexusVoiceSession INSTANCE = new NexusVoiceSession();

    public enum State {
        DISCONNECTED, CONNECTING, IN_ROOM, ERROR
    }

    public interface Listener {
        void onState(State state, @Nullable String detail);
        void onPeerEvent(String type, @Nullable String userId);
        void onRemoteSignal(@NonNull String from, @NonNull JSONObject payload);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean muted = new AtomicBoolean(true);
    private final String userId = UUID.randomUUID().toString().substring(0, 8);

    private volatile State state = State.DISCONNECTED;
    private volatile String roomId = "";
    private volatile String lastError = null;
    private @Nullable Socket socket;
    private @Nullable BufferedWriter writer;

    private NexusVoiceSession() {}
    public static NexusVoiceSession get() { return INSTANCE; }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public State getState() { return state; }
    public boolean isMuted() { return muted.get(); }
    public void setMuted(boolean m) { muted.set(m); }
    public String getUserId() { return userId; }
    public String getRoomId() { return roomId; }
    @Nullable public String getLastError() { return lastError; }

    /** host:port for signaling (e.g. 192.168.1.10:7070). No wss yet — plain TCP. */
    public void join(@NonNull String hostPort, @NonNull String room, @NonNull String displayName) {
        leave();
        setState(State.CONNECTING, "Connecting to " + hostPort);
        io.execute(() -> {
            try {
                String host = hostPort;
                int port = 7070;
                if (hostPort.contains(":")) {
                    String[] p = hostPort.split(":");
                    host = p[0].trim();
                    port = Integer.parseInt(p[1].trim());
                }
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 8000);
                s.setTcpNoDelay(true);
                socket = s;
                writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                roomId = room;

                JSONObject join = new JSONObject();
                join.put("type", "join");
                join.put("room", room);
                join.put("userId", userId);
                join.put("displayName", displayName);
                sendRaw(join.toString());

                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    handleLine(line);
                }
                setState(State.DISCONNECTED, "Signaling closed");
            } catch (Exception e) {
                Log.e(TAG, "join failed", e);
                lastError = e.getMessage();
                setState(State.ERROR, e.getMessage());
                cleanupSocket();
            }
        });
    }

    public void leave() {
        io.execute(() -> {
            try {
                if (writer != null) {
                    JSONObject leave = new JSONObject();
                    leave.put("type", "leave");
                    leave.put("userId", userId);
                    sendRaw(leave.toString());
                }
            } catch (Exception ignored) {}
            cleanupSocket();
            roomId = "";
            setState(State.DISCONNECTED, "Left room");
        });
    }

    /** Forward WebRTC SDP/ICE JSON to a peer via signaling. */
    public void sendSignal(@NonNull String toUserId, @NonNull JSONObject payload) {
        io.execute(() -> {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "signal");
                msg.put("to", toUserId);
                msg.put("from", userId);
                msg.put("payload", payload);
                sendRaw(msg.toString());
            } catch (Exception e) {
                Log.e(TAG, "sendSignal", e);
            }
        });
    }

    private void handleLine(String line) {
        try {
            JSONObject o = new JSONObject(line);
            String type = o.optString("type", "");
            switch (type) {
                case "joined":
                    setState(State.IN_ROOM, "In room " + roomId);
                    break;
                case "peer-joined":
                case "peer-left":
                    for (Listener l : listeners) {
                        try { l.onPeerEvent(type, o.optString("userId", null)); } catch (Throwable ignored) {}
                    }
                    break;
                case "signal":
                    JSONObject payload = o.optJSONObject("payload");
                    String from = o.optString("from", "");
                    if (payload != null && !from.isEmpty()) {
                        for (Listener l : listeners) {
                            try { l.onRemoteSignal(from, payload); } catch (Throwable ignored) {}
                        }
                    }
                    break;
                case "error":
                    lastError = o.optString("message", "server error");
                    setState(State.ERROR, lastError);
                    break;
                default:
                    Log.d(TAG, "unknown: " + type);
            }
        } catch (Exception e) {
            Log.w(TAG, "bad line: " + line);
        }
    }

    private synchronized void sendRaw(String line) throws Exception {
        if (writer == null) throw new Exception("Not connected");
        writer.write(line);
        writer.write("\n");
        writer.flush();
    }

    private void cleanupSocket() {
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        writer = null;
        socket = null;
    }

    private void setState(State s, String detail) {
        state = s;
        for (Listener l : listeners) {
            try { l.onState(s, detail); } catch (Throwable ignored) {}
        }
    }
}
