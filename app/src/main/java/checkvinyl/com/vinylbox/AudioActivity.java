package checkvinyl.com.vinylbox;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.gracenote.gnsdk.GnException;

import java.util.ArrayList;
import java.util.List;

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

        try {
            audioModule.initializeUser(getApplicationContext());
        } catch (GnException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        db.collection("events").document("YXaL6JNrjX92SNFO0xw1").collection("songs").orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener(this, new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot documentSnapshots, FirebaseFirestoreException e) {

                if (e != null) {
                    Log.i("TRACK_SNAPSHOT_FAILED", e.getMessage());
                }

                for (DocumentChange change : documentSnapshots.getDocumentChanges()) {
                    Log.i("DB_CHANGES", "Title: " + change.getDocument().getString("title") + "Type: " + change.getType().name());
                    DocumentSnapshot doc = change.getDocument();
                    TrackData track = new TrackData(doc.getString("title"), doc.getString("artist"), new Genre(doc.getString("genre_1"), doc.getString("genre_2")), new Mood(doc.getString("mood_1"), doc.getString("mood_2")), doc.getString("tempo"), doc.getString("id"));

                    if (change.getType() == DocumentChange.Type.ADDED) {
                        audioModule.addTrack(track);
                    } else if (change.getType() == DocumentChange.Type.REMOVED) {
                        audioModule.removeTrack(track);
                    }
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            audioModule.startListening();
        } catch (GnException e) {
            e.printStackTrace();
        }
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
}
