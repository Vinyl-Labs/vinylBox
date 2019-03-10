package checkvinyl.com.vinylbox

import android.graphics.Bitmap

data class TrackData(val title: String, val artist: String, val genre: Genre, val mood: Mood, val tempo: String, val id: String?, val coverArt: String?)

class Genre(var genre_1: String?, var genre_2: String?)
class Mood(var mood_1: String?, var mood_2: String?)

//fun main(args: Array<String>) {
//    val sampleTrack = TrackData("Slays Ways", "Vinyl Labs", Genre("Hip Hop", "90s Rock"), Mood("dark", "exciting"), 98)
//}