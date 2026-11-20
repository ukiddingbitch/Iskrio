package sk.iskrio.studio;
import org.junit.Test;import static org.junit.Assert.*;import java.nio.*;import androidx.media3.common.C;import androidx.media3.common.audio.AudioProcessor;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class VolumeAudioProcessorTest {
 @Test public void halvesOriginalPcmVolume()throws Exception{VolumeAudioProcessor processor=new VolumeAudioProcessor(.5f);processor.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT));processor.flush();ByteBuffer samples=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());samples.putShort((short)32000).putShort((short)-32000).flip();processor.queueInput(samples);ByteBuffer out=processor.getOutput().order(ByteOrder.nativeOrder());assertEquals(16000,out.getShort());assertEquals(-16000,out.getShort());}
 @Test public void supportsFloatAudio()throws Exception{VolumeAudioProcessor processor=new VolumeAudioProcessor(.25f);processor.configure(new AudioProcessor.AudioFormat(48000,1,C.ENCODING_PCM_FLOAT));processor.flush();ByteBuffer samples=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());samples.putFloat(.8f).flip();processor.queueInput(samples);assertEquals(.2f,processor.getOutput().order(ByteOrder.nativeOrder()).getFloat(),.0001f);}
}
