package sk.iskrio.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
public class Mp4AudioMapTest {
 @Test public void indexesAudioWithoutReadingVideoPayload()throws Exception{byte[] moov=getClass().getResourceAsStream("/audio-index.moov").readAllBytes();JSONObject meta=new JSONObject(new String(getClass().getResourceAsStream("/audio-index.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));long offset=meta.getLong("offset");Mp4AudioMap map=Mp4AudioMap.read(meta.getLong("size"),(at,n)->{if(at>=offset&&at+n<=offset+moov.length)return Arrays.copyOfRange(moov,(int)(at-offset),(int)(at-offset+n));JSONArray header=meta.optJSONObject("headers").optJSONArray(Long.toString(at));if(header==null||n>16)throw new AssertionError("Read attempted outside metadata");byte[] bytes=new byte[n];for(int i=0;i<n;i++)bytes[i]=(byte)header.optInt(i);return bytes;});assertEquals(48000,map.sampleRate);assertEquals(1,map.channels);assertTrue(map.sizes.length>500);assertTrue(map.times[map.times.length-1]>11000000);for(int i=0;i<map.sizes.length;i++){assertTrue(map.sizes[i]>0);assertTrue(map.offsets[i]+map.sizes[i]<=meta.getLong("size"));}}
 @Test public void rejectsTruncatedAndOversizedBoxes(){for(byte[] input:new byte[][]{new byte[0],new byte[16],new byte[]{127,127,127,127,109,111,111,118,0,0,0,0,0,0,0,0}})assertThrows(RuntimeException.class,()->Mp4AudioMap.read(input.length,(at,n)->Arrays.copyOfRange(input,(int)at,(int)at+n)));}
}

