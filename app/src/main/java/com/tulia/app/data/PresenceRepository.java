package com.tulia.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Online / last-seen state in the Realtime Database at status/{uid}.
 * The connection listener re-arms onDisconnect on every (re)connect, so a
 * killed process still leaves a fresh lastSeen behind.
 */
public final class PresenceRepository {

    public interface PresenceCallback {
        void onPresence(boolean online, long lastSeenMillis);
    }

    public interface PresenceHandle {
        void remove();
    }

    private static final String STATUS = "status";

    private static String boundUid;
    private static DatabaseReference myStatus;
    private static ValueEventListener connectionListener;

    private PresenceRepository() {
    }

    public static void start(@NonNull String uid) {
        FirebaseDatabase database;
        try {
            database = FirebaseDatabase.getInstance();
        } catch (IllegalStateException exception) {
            return;
        }
        if (uid.equals(boundUid)) {
            return;
        }
        stop();

        boundUid = uid;
        myStatus = database.getReference(STATUS).child(uid);
        connectionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    return;
                }
                armDisconnect();
                goOnline();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        database.getReference(".info/connected").addValueEventListener(connectionListener);
    }

    public static void setForeground(boolean foreground) {
        if (myStatus == null) {
            return;
        }
        if (foreground) {
            goOnline();
        } else {
            writeState(false);
        }
    }

    public static void stop() {
        if (connectionListener != null) {
            try {
                FirebaseDatabase.getInstance().getReference(".info/connected")
                        .removeEventListener(connectionListener);
            } catch (IllegalStateException ignored) {
                // Firebase was never configured; nothing to remove.
            }
        }
        if (myStatus != null) {
            writeState(false);
        }
        connectionListener = null;
        myStatus = null;
        boundUid = null;
    }

    @NonNull
    public static PresenceHandle listen(@NonNull String uid,
                                        @NonNull PresenceCallback callback) {
        try {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference(STATUS)
                    .child(uid);
            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean online = snapshot.child("online").getValue(Boolean.class);
                    Long lastSeen = snapshot.child("lastSeen").getValue(Long.class);
                    callback.onPresence(Boolean.TRUE.equals(online),
                            lastSeen == null ? 0L : lastSeen);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };
            ref.addValueEventListener(listener);
            return () -> ref.removeEventListener(listener);
        } catch (IllegalStateException exception) {
            return () -> {
            };
        }
    }

    private static void armDisconnect() {
        if (myStatus == null) {
            return;
        }
        myStatus.onDisconnect().setValue(offlineState());
    }

    private static void goOnline() {
        if (myStatus == null) {
            return;
        }
        Map<String, Object> online = new HashMap<>();
        online.put("online", true);
        online.put("lastSeen", ServerValue.TIMESTAMP);
        myStatus.setValue(online);
    }

    private static void writeState(boolean online) {
        Map<String, Object> state = new HashMap<>();
        state.put("online", online);
        state.put("lastSeen", ServerValue.TIMESTAMP);
        myStatus.setValue(state);
    }

    @NonNull
    private static Map<String, Object> offlineState() {
        Map<String, Object> offline = new HashMap<>();
        offline.put("online", false);
        offline.put("lastSeen", ServerValue.TIMESTAMP);
        return offline;
    }
}
