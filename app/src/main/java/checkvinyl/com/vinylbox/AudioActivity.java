package checkvinyl.com.vinylbox;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.gracenote.gnsdk.GnException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioActivity extends AppCompatActivity {

    AudioModule audioModule;
    Firestore firestore;
    private FirebaseFirestore db;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        db = FirebaseFirestore.getInstance();
        firestore = new Firestore(db);
        audioModule = new AudioModule(firestore, this);
        audioModule.createUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getEventInfo();
        setTrackList();

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            audioModule.stopListening();
        } catch (GnException e) {
            e.printStackTrace();
        }

    }

    public void setTrackList() {
        db.collection("host").document("YXaL6JNrjX92SNFO0xw1").collection("songs").orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener(this, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot documentSnapshots, FirebaseFirestoreException e) {

                if (e != null) {
                    Log.i("TRACK_SNAPSHOT_FAILED", e.getMessage());
                }

                for (DocumentChange change : documentSnapshots.getDocumentChanges()) {
                    Log.i("DB_CHANGES", "Title: " + change.getDocument().getString("title") + "Type: " + change.getType().name());
                    DocumentSnapshot doc = change.getDocument();
                    TrackData track = new TrackData(doc.getString("title"), doc.getString("artist"), new Genre(doc.getString("genre_1"), doc.getString("genre_2")), new Mood(doc.getString("mood_1"), doc.getString("mood_2")), doc.getString("tempo"), doc.getString("id"), doc.getString("artwork"));

                    if (change.getType() == DocumentChange.Type.ADDED) {
                        audioModule.addTrack(track);
                    } else if (change.getType() == DocumentChange.Type.REMOVED) {
                        audioModule.removeTrack(track);
                    }
                }
            }
        });
    }
//
//    public void getHostInfo(String hostId) {
//        db.collection("host").document(hostId).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
//            @Override
//            public void onSuccess(DocumentSnapshot documentSnapshot) {
//            }
//        });
//    }

    @TargetApi(Build.VERSION_CODES.O)
    public void getEventInfo() {
//        serial number will be used to link device to event
            String deviceId =  "V-" + Build.getSerial().substring(Build.getSerial().length() - 8);
            Log.i("Device ID", deviceId);
        db.collection("events").whereEqualTo("deviceId", deviceId).get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (DocumentSnapshot document : task.getResult()) {
                                Log.i("Event:", document.getId() + " => " + document.getData());

                                try {
                                    audioModule.initializeUser(getApplicationContext(), document.getId(), document.getString("hostId"));
                                } catch (GnException e) {
                                    e.printStackTrace();
                                }

                                try {
                                    audioModule.startListening();
                                } catch (GnException e) {
                                    e.printStackTrace();
                                }

                            }
                        } else {
                            Log.i("No Doc", "Error getting documents: ", task.getException());
                        }
                    }
                });
    }
}
