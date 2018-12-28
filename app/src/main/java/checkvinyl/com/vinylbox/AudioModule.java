package checkvinyl.com.vinylbox;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ServerTimestamp;
import com.gracenote.gnsdk.GnDataLevel;
import com.gracenote.gnsdk.GnDescriptor;
import com.gracenote.gnsdk.GnError;
import com.gracenote.gnsdk.GnException;
import com.gracenote.gnsdk.GnLanguage;
import com.gracenote.gnsdk.GnLicenseInputMode;
import com.gracenote.gnsdk.GnList;
import com.gracenote.gnsdk.GnLocale;
import com.gracenote.gnsdk.GnLocaleGroup;
import com.gracenote.gnsdk.GnLookupData;
import com.gracenote.gnsdk.GnLookupLocalStream;
import com.gracenote.gnsdk.GnLookupLocalStreamIngest;
import com.gracenote.gnsdk.GnLookupLocalStreamIngestStatus;
import com.gracenote.gnsdk.GnLookupMode;
import com.gracenote.gnsdk.GnManager;
import com.gracenote.gnsdk.GnMic;
import com.gracenote.gnsdk.GnMusicIdStream;
import com.gracenote.gnsdk.GnMusicIdStreamIdentifyingStatus;
import com.gracenote.gnsdk.GnMusicIdStreamPreset;
import com.gracenote.gnsdk.GnMusicIdStreamProcessingStatus;
import com.gracenote.gnsdk.GnRegion;
import com.gracenote.gnsdk.GnResponseAlbums;
import com.gracenote.gnsdk.GnStatus;
import com.gracenote.gnsdk.GnStorageSqlite;
import com.gracenote.gnsdk.GnUser;
import com.gracenote.gnsdk.GnUserStore;
import com.gracenote.gnsdk.IGnAudioSource;
import com.gracenote.gnsdk.IGnCancellable;
import com.gracenote.gnsdk.IGnLookupLocalStreamIngestEvents;
import com.gracenote.gnsdk.IGnMusicIdStreamEvents;
import com.gracenote.gnsdk.IGnSystemEvents;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AudioModule implements IGnMusicIdStreamEvents {
    private String appString;
    private GnUser gnUser;
    private IGnAudioSource gnMicrophone;
    private GnMusicIdStream gnMusicIdStream;
    private List<GnMusicIdStream> streamIdObjects;
    private Firestore firestore;
    private Map<String, Object> previousTrack;
    private Activity activity;


    private ArrayAdapter adapter;

    AudioModule(Firestore firestore, Activity activity) {
        this.firestore = firestore;
        this.activity = activity;
        streamIdObjects = new ArrayList<>();
    }

    public void initializeUser(Context context) throws GnException {
        String clientId = context.getString(R.string.client_id);
        String clientTag = context.getString(R.string.client_tag);
        String license = getAssetAsString(context);
        appString = "1.1";

        // GnManager must be created first, it initializes GNSDK
        GnManager gnManager = new GnManager(context, license, GnLicenseInputMode.kLicenseInputModeString);

        // provide handler to receive system events, such as locale update needed
        gnManager.systemEventHandler(new SystemEvents());

        // get a user, if no user stored persistently a new user is registered and stored
        // Note: Android persistent storage used, so no GNSDK storage provider needed to store a user

        gnUser = new GnUser(new GnUserStore(context), clientId, clientTag, appString);

        Log.i("GN_USER", gnUser.toString());

        // enable storage provider allowing GNSDK to use its persistent stores
        GnStorageSqlite.enable();

        // enable local MusicID-Stream recognition (GNSDK storage provider must be enabled as pre-requisite)
        GnLookupLocalStream.enable();

//        Loads data to support the requested locale, data is downloaded from Gracenote Service if not
//        found in persistent storage. Once downloaded it is stored in persistent storage (if storage
//        provider is enabled). Download and write to persistent storage can be lengthy so perform in
//        another thread

        Thread localeThread = new Thread(
                new LocaleLoadRunnable(GnLocaleGroup.kLocaleGroupMusic,
                        GnLanguage.kLanguageEnglish,
                        GnRegion.kRegionGlobal,
                        GnDescriptor.kDescriptorDefault,
                        gnUser)
        );
        localeThread.start();

//         Ingest MusicID-Stream local bundle, perform in another thread as it can be lengthy
        Thread ingestThread = new Thread(new LocalBundleIngestRunnable(context));
        ingestThread.start();
    }

    public void updateTrackList(TrackData track) {
        adapter.insert(track, 0);
        adapter.notifyDataSetChanged();
    }

    public void createUi() {
        ListView listView = activity.findViewById(R.id.track_list);
            adapter = new TrackListAdapter(activity.getApplicationContext(), new ArrayList<TrackData>());
            listView.setAdapter(adapter);
    }

    public void startListening() throws GnException {
        if (gnMicrophone == null) {
            gnMicrophone = new AudioVisualizeAdapter(new GnMic());
            gnMusicIdStream = new GnMusicIdStream(gnUser, GnMusicIdStreamPreset.kPresetMicrophone, this);
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataContent, true);
            gnMusicIdStream.options().lookupData(GnLookupData.kLookupDataSonicData, true);
            gnMusicIdStream.options().lookupMode(GnLookupMode.kLookupModeOnline);
            gnMusicIdStream.options().resultSingle(true);
            streamIdObjects.add(gnMusicIdStream);
        }

        Thread audioProcessThread = new Thread(new AudioProcessRunnable());
        audioProcessThread.start();

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        try {
                            idTrack();
                        } catch (GnException e) {
                            e.printStackTrace();
                        }
                    }
                },
                300);
    }

    private void idTrack() throws GnException {
        gnMusicIdStream.identifyAlbumAsync();

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        try {
                            idTrack();
                        } catch (GnException e) {
                            e.printStackTrace();
                        }
                    }
                },
                120000);

    }

    public void stopListening() throws GnException {
        if (gnMusicIdStream != null) {

            // to ensure no pending identifications deliver results while your app is
            // paused it is good practice to call cancel
            // it is safe to call identifyCancel if no identify is pending
            gnMusicIdStream.identifyCancel();

            // stopping audio processing stops the audio processing thread started
            // in onResume
            gnMusicIdStream.audioProcessStop();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // LISTENER METHODS ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void musicIdStreamProcessingStatusEvent(GnMusicIdStreamProcessingStatus gnMusicIdStreamProcessingStatus, IGnCancellable iGnCancellable) {
        Log.i("NOT IMPLEMENTED", gnMusicIdStreamProcessingStatus.toString());
    }

    @Override
    public void musicIdStreamIdentifyingStatusEvent(GnMusicIdStreamIdentifyingStatus gnMusicIdStreamIdentifyingStatus, IGnCancellable iGnCancellable) {
        Log.i("ID_STATUS_EVENT", gnMusicIdStreamIdentifyingStatus.toString());
    }

    @Override
    public void musicIdStreamAlbumResult(GnResponseAlbums gnResponseAlbums, IGnCancellable iGnCancellable) {
        try {
            getTrackDetails(gnResponseAlbums);
        } catch (GnException e) {
            Log.w("ID_INCOMPLETE", e.errorDescription());
            e.printStackTrace();
        }
    }

    @Override
    public void musicIdStreamIdentifyCompletedWithError(GnError gnError) {
        Log.i("STREAM_ID_ERROR: ", gnError.errorDescription());
    }

    @Override
    public void statusEvent(GnStatus gnStatus, long l, long l1, long l2, IGnCancellable iGnCancellable) {
        Log.i("STATUS_EVENT", gnStatus.toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // RUNNABLE ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////


    class AudioProcessRunnable implements Runnable {

        @Override
        public void run() {
            try {

                // start audio processing with GnMic, GnMusicIdStream pulls data from GnMic internally
                gnMusicIdStream.audioProcessStart(gnMicrophone);

            } catch (GnException e) {

                Log.e(appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule());

            }
        }
    }

    private String getAssetAsString(Context context) {

        String assetString = null;
        InputStream assetStream;

        try {

            assetStream = context.getAssets().open("license.txt");
            if (assetStream != null) {

                java.util.Scanner s = new java.util.Scanner(assetStream).useDelimiter("\\A");

                assetString = s.hasNext() ? s.next() : "";
                assetStream.close();

            } else {
                Log.e(appString, "Asset not found:" + "license.txt");
            }

        } catch (IOException e) {

            Log.e(appString, "Error getting asset as string: " + "ERROR MESSAGE" + e.getMessage());

        }

        return assetString;
    }


    /**
     * Receives system events from GNSDK
     */

    class SystemEvents implements IGnSystemEvents {
        @Override
        public void localeUpdateNeeded(GnLocale locale) {
            // Locale update is detected
            Thread localeUpdateThread = new Thread(new LocaleUpdateRunnable(locale, gnUser));
            localeUpdateThread.start();
        }

        @Override
        public void listUpdateNeeded(GnList list) {
            // List update is detected
            Thread listUpdateThread = new Thread(new ListUpdateRunnable(list, gnUser));
            listUpdateThread.start();
        }

        @Override
        public void systemMemoryWarning(long currentMemorySize, long warningMemorySize) {
            // only invoked if a memory warning limit is configured
        }
    }

    /**
     * Updates a locale
     */
    class LocaleUpdateRunnable implements Runnable {
        GnLocale locale;
        GnUser user;

        LocaleUpdateRunnable(GnLocale locale, GnUser user) {
            this.locale = locale;
            this.user = user;
        }

        @Override
        public void run() {
            try {
                locale.update(user);
            } catch (GnException e) {
                Log.e(appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule());
            }
        }
    }

    class LocaleLoadRunnable implements Runnable {
        GnLocaleGroup group;
        GnLanguage language;
        GnRegion region;
        GnDescriptor descriptor;
        GnUser user;


        LocaleLoadRunnable(GnLocaleGroup group, GnLanguage language, GnRegion region, GnDescriptor descriptor, GnUser user) {
            this.group = group;
            this.language = language;
            this.region = region;
            this.descriptor = descriptor;
            this.user = user;
        }

        @Override
        public void run() {
            try {

                GnLocale locale = new GnLocale(group, language, region, descriptor, gnUser);
                locale.setGroupDefault();

            } catch (GnException e) {
                Log.e(appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule());
            }
        }
    }

    /**
     * Loads a local bundle for MusicID-Stream lookups
     */
    class LocalBundleIngestRunnable implements Runnable {
        Context context;

        LocalBundleIngestRunnable(Context context) {
            this.context = context;
        }

        public void run() {
            try {

                // our bundle is delivered as a package asset
                // to ingest the bundle access it as a stream and write the bytes to
                // the bundle ingester
                // bundles should not be delivered with the package as this, rather they
                // should be downloaded from your own online service

                InputStream bundleInputStream;
                int ingestBufferSize = 1024;
                byte[] ingestBuffer = new byte[ingestBufferSize];
                int bytesRead;

                GnLookupLocalStreamIngest ingester = new GnLookupLocalStreamIngest(new BundleIngestEvents());

                try {

                    bundleInputStream = context.getAssets().open("1557.b");

                    do {

                        bytesRead = bundleInputStream.read(ingestBuffer, 0, ingestBufferSize);
                        if (bytesRead == -1)
                            bytesRead = 0;

                        ingester.write(ingestBuffer, bytesRead);

                    } while (bytesRead != 0);

                } catch (IOException e) {
                    e.printStackTrace();
                }

                ingester.flush();

            } catch (GnException e) {
                Log.e(appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule());
            }

        }
    }

    private class BundleIngestEvents implements IGnLookupLocalStreamIngestEvents {

        @Override
        public void statusEvent(GnLookupLocalStreamIngestStatus status, String bundleId, IGnCancellable canceller) {
            Log.i("Status Updated: " + status + "  | " + bundleId, "Status Updated");
        }
    }

    /**
     * Updates a list
     */
    public class ListUpdateRunnable implements Runnable {
        GnList list;
        GnUser user;


        ListUpdateRunnable(
                GnList list,
                GnUser user) {
            this.list = list;
            this.user = user;
        }

        @Override
        public void run() {
            try {
                list.update(user);
            } catch (GnException e) {
                Log.e(appString, e.errorCode() + ", " + e.errorDescription() + ", " + e.errorModule());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // HELPERS ////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private void getTrackDetails(GnResponseAlbums gnResponseAlbums) throws GnException {
        String track = gnResponseAlbums.albums().getByIndex(0).next().trackMatched().title().display();
//        Log.i("RESULT_TITLE", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().title().display());
//        Log.i("RESULT_ARTIST_1", gnResponseAlbums.albums().getByIndex(0).next().artist().name().display());
//        Log.i("RESULT_ARTIST_2", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().artist().name().display());
//        Log.i("RESULT_GENRE_1", gnResponseAlbums.albums().getByIndex(0).next().genre(GnDataLevel.kDataLevel_1));
//        Log.i("RESULT_GENRE_2", gnResponseAlbums.albums().getByIndex(0).next().genre(GnDataLevel.kDataLevel_2));
//        Log.i("RESULT_GENRE_3", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().genre(GnDataLevel.kDataLevel_1));
//        Log.i("RESULT_MOOD_1", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().mood(GnDataLevel.kDataLevel_1));
//        Log.i("RESULT_MOOD_2", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().mood(GnDataLevel.kDataLevel_2));
//        Log.i("RESULT_TEMPO_1", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().tempo(GnDataLevel.kDataLevel_1));
//        Log.i("RESULT_TEMPO_2", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().tempo(GnDataLevel.kDataLevel_2));
//        Log.i("RESULT_TEMPO_3", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().tempo(GnDataLevel.kDataLevel_3));
//        Log.i("MATCH_CONFIDENCE", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().matchConfidence());
//        Log.i("MATCH_SCORE", "" + gnResponseAlbums.albums().getByIndex(0).next().trackMatched().matchScore());

        Map<String, Object> mood = new HashMap<>();
        mood.put("mood_1", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().mood(GnDataLevel.kDataLevel_1));
        mood.put("mood_2", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().mood(GnDataLevel.kDataLevel_2));

        Map<String, Object> genre = new HashMap<>();
        if (gnResponseAlbums.albums().getByIndex(0).next().trackMatched().genre(GnDataLevel.kDataLevel_1).isEmpty()) {
            genre.put("genre_1", gnResponseAlbums.albums().getByIndex(0).next().genre(GnDataLevel.kDataLevel_1));
            genre.put("genre_2", gnResponseAlbums.albums().getByIndex(0).next().genre(GnDataLevel.kDataLevel_2));
        } else {
            genre.put("genre_1", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().genre(GnDataLevel.kDataLevel_1));
            genre.put("genre_2", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().genre(GnDataLevel.kDataLevel_2));
        }

        Map<String, Object> trackDetails = new HashMap<>();
        trackDetails.put("title", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().title().display());
        trackDetails.put("genre", genre);
        trackDetails.put("timestamp", FieldValue.serverTimestamp());
        trackDetails.put("mood", mood);
        trackDetails.put("tempo", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().tempo(GnDataLevel.kDataLevel_3));
        if (gnResponseAlbums.albums().getByIndex(0).next().trackMatched().artist().name().display().isEmpty()) {
            trackDetails.put("artist", gnResponseAlbums.albums().getByIndex(0).next().artist().name().display());
        } else {
            trackDetails.put("artist", gnResponseAlbums.albums().getByIndex(0).next().trackMatched().artist().name().display());
        }

        if (duplicateTrack(trackDetails) || track.isEmpty()) {
            Log.i("DUPLICATE", "The song has not changed yet");
            return;
        }
        firestore.saveTrackData(trackDetails);
        previousTrack = trackDetails;
    }

    private boolean duplicateTrack(Map<String, Object> currentTrack) {
        return currentTrack.equals(previousTrack);
    }

}
