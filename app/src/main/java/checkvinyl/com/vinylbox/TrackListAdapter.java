package checkvinyl.com.vinylbox;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class TrackListAdapter extends ArrayAdapter<TrackData> {
    private Context aContext;
    TrackListAdapter(Context context, ArrayList<TrackData> trackData) {
        super(context, R.layout.track_item, trackData);
        aContext = context;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater trackInflater = LayoutInflater.from(aContext);
        final View trackView = trackInflater.inflate(R.layout.track_item, parent, false);

        TrackData singleTrack = getItem(position);
        TextView trackTitle = (TextView) trackView.findViewById(R.id.title);
        TextView trackArtist = (TextView) trackView.findViewById(R.id.artist);

        Log.i("TRACK_DETAILS", singleTrack.getArtist() + " " + singleTrack.getTitle());

        trackTitle.setText(singleTrack.getTitle());
        trackArtist.setText(singleTrack.getArtist());

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        if(position == 0){
                            trackView.setSelected(true);
                        }
                    }
                },
                100);

        return trackView;
    }
}
