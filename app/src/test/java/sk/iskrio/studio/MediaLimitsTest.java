package sk.iskrio.studio;
import org.junit.Test;import static org.junit.Assert.*;
public class MediaLimitsTest {
 @Test public void acceptsTenMinutesAndOneGigabyte(){MediaLimits.video(1024L*1024*1024,600000);}
 @Test public void rejectsAboveEitherBound(){assertThrows(IllegalArgumentException.class,()->MediaLimits.video(1024L*1024*1024+1,600000));assertThrows(IllegalArgumentException.class,()->MediaLimits.video(1,600001));}
 @Test public void imagesKeepSeparateMemorySafeLimit(){assertEquals(40L*1024*1024,MediaLimits.IMAGE_BYTES);assertTrue(MediaLimits.VIDEO_BYTES>MediaLimits.IMAGE_BYTES);}
 @Test public void videoPackageNamesCannotEscapePrivateStorage(){assertTrue(VideoArchive.validMediaEntry("videos/12345678-1234-1234-1234-123456789012/source.mp4"));for(String path:new String[]{"../source.mp4","videos/../../secret","/videos/a/source.mp4","videos/12345678-1234-1234-1234-123456789012/other.mp4"})assertFalse(VideoArchive.validMediaEntry(path));}
}
