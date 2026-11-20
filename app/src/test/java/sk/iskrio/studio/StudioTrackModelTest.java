package sk.iskrio.studio;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudioTrackModelTest {
 private JSONObject video()throws Exception{return new JSONObject("{\"mode\":\"tracks\",\"tracks\":[{\"id\":\"top\"},{\"id\":\"lower\"}],\"clips\":[{\"id\":\"late\",\"trackId\":\"top\",\"atMs\":4000},{\"id\":\"under\",\"trackId\":\"lower\",\"atMs\":0},{\"id\":\"early\",\"trackId\":\"top\",\"atMs\":0}]}");}
 @Test public void trackRankThenTimeDeterminesExportOrder()throws Exception{JSONObject v=video();JSONArray ordered=StudioTrackModel.ordered(v);assertEquals("early",ordered.getJSONObject(0).getString("id"));assertEquals("late",ordered.getJSONObject(1).getString("id"));assertEquals("under",ordered.getJSONObject(2).getString("id"));assertEquals("late",v.getJSONArray("clips").getJSONObject(0).getString("id"));}
 @Test public void hiddenTrackAppliesToEveryVideoWithoutChangingSources()throws Exception{JSONObject v=video();v.getJSONArray("tracks").getJSONObject(0).put("hidden",true);JSONArray a=StudioTrackModel.ordered(v);assertTrue(a.getJSONObject(0).getBoolean("hidden"));assertTrue(a.getJSONObject(1).getBoolean("hidden"));assertFalse(a.getJSONObject(2).optBoolean("hidden"));assertFalse(v.getJSONArray("clips").getJSONObject(0).has("hidden"));}
 @Test public void emptyTracksAreValid()throws Exception{JSONObject v=video().put("clips",new JSONArray());assertEquals(0,StudioTrackModel.ordered(v).length());}
 @Test public void rejectsMissingAndDuplicateTracks()throws Exception{JSONObject v=video();v.getJSONArray("clips").getJSONObject(0).put("trackId","missing");assertThrows(IllegalArgumentException.class,()->StudioTrackModel.ordered(v));JSONObject d=video();d.getJSONArray("tracks").getJSONObject(1).put("id","top");assertThrows(IllegalArgumentException.class,()->StudioTrackModel.ordered(d));}
 @Test public void keepsLegacySequenceOrder()throws Exception{JSONObject v=video().put("mode","sequence");assertSame(v.getJSONArray("clips"),StudioTrackModel.ordered(v));}
}
