package checkvinyl.com.vinylbox;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;


class Firestore {

    private FirebaseFirestore db;

    Firestore(FirebaseFirestore db) {
        this.db = db;


//        db = FirebaseFirestore.getInstance();
        getVenue();
        getEvent();
    }

    private void getVenue() {
        DocumentReference venue = db.collection("venues").document("FHvoH3Wz2seGlB0pw1e8");
        venue.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Log.i("VENUE_DETAILS", "DocumentSnapshot data: " + document.getData());
                    } else {
                        Log.i("DB_ERROR", "No such document");
                    }
                } else {
                    Log.i("DB_ERROR", "get failed with ", task.getException());
                }
            }
        });
    }

    private void getEvent() {
        DocumentReference event = db.collection("events").document("YXaL6JNrjX92SNFO0xw1");
        event.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Log.i("EVENT_DETAILS", "DocumentSnapshot data: " + document.getData());

                    } else {
                        Log.i("DB_ERROR", "No such document");
                    }
                } else {
                    Log.i("DB_ERROR", "get failed with ", task.getException());
                }
            }
        });
    }

    public  ArrayList<TrackData> getTracks() {
        final ArrayList<TrackData> trackData = new ArrayList<>();
        db.collection("events").document("YXaL6JNrjX92SNFO0xw1").collection("songs")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (DocumentSnapshot doc : task.getResult()) {
                                if (doc.exists()) {
                                    TrackData track = new TrackData(doc.getString("title"), doc.getString("artist"), new Genre(doc.getString("genre_1"), doc.getString("genre_2")), new Mood(doc.getString("mood_1"), doc.getString("mood_2")), doc.getString("tempo"), doc.getString("id"), doc.getString("coverArt"));
                                    trackData.add(track);
                                } else {
                                    return;
                                }
                            }
                        } else {
                            Log.d("TRACK_SNAPSHOT", "Error getting documents: ", task.getException());
                        }
                    }
                });
        return trackData;
    }

    public ArrayList<TrackData> updateTracks() {
        final ArrayList<TrackData> trackData = new ArrayList<>();
        db.collection("events").document("YXaL6JNrjX92SNFO0xw1").collection("songs").orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot documentSnapshots, FirebaseFirestoreException e) {

                if (e != null) {
                    Log.i("TRACK_SNAPSHOT_FAILED", e.getMessage());
                }

                for (DocumentSnapshot doc : documentSnapshots) {

                    if (doc.exists()) {
                        TrackData track = new TrackData(doc.getString("title"), doc.getString("artist"), new Genre(doc.getString("genre_1"), doc.getString("genre_2")), new Mood(doc.getString("mood_1"), doc.getString("mood_2")), doc.getString("tempo"), doc.getString("id"), doc.getString("coverArt"));
                        trackData.add(track);
                    } else {
                        return;
                    }

                }
                Log.i("TRACK_SNAPSHOT", trackData.toString());
            }
        });

        return trackData;
    }

    public void saveTrackData(Map<String, Object> trackData) {
        Log.i("SENDING TRACK DATA", "Sending Track Data");
        db.collection("events").document("YXaL6JNrjX92SNFO0xw1").collection("songs")
                .add(trackData)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.i("SUCCESS", "Track data added with ID: " + documentReference.getId());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("DB_ERROR", "Error adding document", e);
                    }
                });
    }

}
