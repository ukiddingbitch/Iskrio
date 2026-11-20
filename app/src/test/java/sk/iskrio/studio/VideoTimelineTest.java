package sk.iskrio.studio;
import org.junit.Test;
import static org.junit.Assert.*;
public class VideoTimelineTest {
 @Test public void recoversTheReportedTwitterGif(){VideoTimeline t=new VideoTimeline();for(long time:new long[]{0,60000,130000,200000,260000,330000})t.add(time);assertEquals(400000,t.durationUs(0,15));}
 @Test public void usesValidTrackDuration(){VideoTimeline t=new VideoTimeline();t.add(0);t.add(330000);assertEquals(400000,t.durationUs(400000,15));}
 @Test public void normalizesNonzeroOriginsAndOutOfOrderPresentation(){VideoTimeline t=new VideoTimeline();for(long time:new long[]{1000000,1200000,1060000,1330000,1260000,1130000})t.add(time);assertEquals(400000,t.durationUs(0,15));}
 @Test public void singleFrameUsesFrameRate(){VideoTimeline t=new VideoTimeline();t.add(0);assertEquals(40000,t.durationUs(0,25));}
 @Test public void emptyAndOverLimitSourcesAreRejected(){assertThrows(IllegalArgumentException.class,()->new VideoTimeline().durationUs(0,30));VideoTimeline t=new VideoTimeline();t.add(0);assertThrows(IllegalArgumentException.class,()->t.add(600000000));}
 @Test public void detectsGifContentRegardlessOfLabel(){assertTrue(MediaFrames.isGifHeader("GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));assertTrue(MediaFrames.isGifHeader("GIF87a".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));assertFalse(MediaFrames.isGifHeader(new byte[]{0,0,0,24,102,116,121,112}));assertFalse(MediaFrames.isGifHeader(new byte[]{71,73,70}));}
 @Test public void invalidMetadataHasNoGuessedDuration(){assertEquals(0,VideoSource.positiveLong(null));assertEquals(0,VideoSource.positiveLong("N/A"));assertEquals(0,VideoSource.positiveLong("0"));assertEquals(400,VideoSource.positiveLong("400"));}
}
