package sk.iskrio.studio;
import org.junit.Test;
import static org.junit.Assert.*;
public class ElementaryAudioTest {
 @Test public void audioHeadersCannotSelectAnMp4VideoAsAWholeFile(){assertTrue(AudioOnlyDownload.elementaryAudio(new byte[]{'I','D','3',4,0,0,0,0,0,0,0,0}));assertTrue(AudioOnlyDownload.elementaryAudio(new byte[]{'R','I','F','F',0,0,0,0,'W','A','V','E'}));assertFalse(AudioOnlyDownload.elementaryAudio(new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m'}));assertFalse(AudioOnlyDownload.elementaryAudio(new byte[]{'G','I','F','8','9','a',0,0,0,0,0,0}));}
}
