package sk.iskrio.studio;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class StudioLooksTest {
 @Test public void rejectsArbitraryFilterText()throws Exception {JSONObject c=new JSONObject().put("creative","movie=/secret");assertThrows(IllegalArgumentException.class,()->StudioLooks.apply("[0:v]null",c,720,1280,30,3));}
 @Test public void regionStaysInsideEvenFrame()throws Exception {JSONObject c=new JSONObject().put("region",new JSONObject().put("mode","pixelate").put("x",.25).put("y",.25).put("w",.5).put("h",.5).put("strength",15));String s=StudioLooks.apply("[0:v]null",c,180,320,30,3);assertTrue(s.contains("crop=90:160:44:80"));assertTrue(s.contains("flags=neighbor"));c.getJSONObject("region").put("x",.9);assertThrows(IllegalArgumentException.class,()->StudioLooks.apply("[0:v]null",c,180,320,30,3));}
 @Test public void numericFiltersHaveBounds()throws Exception {JSONObject c=new JSONObject().put("temperature",2.0);assertThrows(IllegalArgumentException.class,()->StudioLooks.apply("[0:v]null",c,720,1280,30,3));}
 @Test public void cameraMotionUsesActualDuration()throws Exception {String s=StudioLooks.apply("[0:v]null",new JSONObject().put("cameraMotion","in"),720,1280,30,4);assertTrue(s.contains("120.000000"));assertTrue(s.contains("s=720x1280:fps=30"));}
}
