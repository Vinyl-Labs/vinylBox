package checkvinyl.com.vinylbox;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.gracenote.gnsdk.GnException;

public class AudioActivity extends AppCompatActivity {

    AudioModule audioModule;
    Firestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        firestore = new Firestore(this);
        audioModule = new AudioModule(firestore);


        try {
            audioModule.initializeUser(getApplicationContext());
        } catch (GnException e) {
            e.printStackTrace();
        }

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
