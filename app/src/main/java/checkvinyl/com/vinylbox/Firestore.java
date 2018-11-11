package checkvinyl.com.vinylbox;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

class Firestore {

    private FirebaseFirestore db;

    Firestore() {
        db = FirebaseFirestore.getInstance();
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

    public void saveTrackData(Map<String, Object> trackData) {

    }

}
