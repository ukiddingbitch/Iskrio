package sk.iskrio.studio;
import org.json.*;
import java.util.*;

/** Resolve track containers to a top-first render order without rewriting project media. */
final class StudioTrackModel {
 static JSONArray ordered(JSONObject video)throws JSONException {
  JSONArray clips=video.getJSONArray("clips"),tracks=video.optJSONArray("tracks");if(!video.optString("mode").equals("tracks")||tracks==null)return clips;
  if(tracks.length()<1||tracks.length()>20)throw new IllegalArgumentException("Use between 1 and 20 tracks.");
  Map<String,Integer> order=new HashMap<>();Set<String> hidden=new HashSet<>();for(int i=0;i<tracks.length();i++){JSONObject t=tracks.getJSONObject(i);String id=t.getString("id");if(!id.matches("[A-Za-z0-9_-]{1,80}")||order.put(id,i)!=null)throw new IllegalArgumentException("Invalid video track.");if(t.optBoolean("hidden"))hidden.add(id);}
  List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<clips.length();i++){JSONObject c=new JSONObject(clips.getJSONObject(i).toString());String id=c.getString("trackId");if(!order.containsKey(id))throw new IllegalArgumentException("A video track is missing.");if(hidden.contains(id))c.put("hidden",true);sorted.add(c);}
  sorted.sort(Comparator.comparingInt((JSONObject c)->order.get(c.optString("trackId"))).thenComparingDouble(c->c.optDouble("atMs")));JSONArray result=new JSONArray();for(JSONObject c:sorted)result.put(c);return result;
 }
}
