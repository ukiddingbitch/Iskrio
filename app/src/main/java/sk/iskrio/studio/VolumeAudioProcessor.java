package sk.iskrio.studio;
import androidx.media3.common.C;
import androidx.media3.common.audio.*;
import java.nio.*;
/** Constant gain applied only during local MP4 export. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class VolumeAudioProcessor extends BaseAudioProcessor {
 private final float gain;
 VolumeAudioProcessor(float gain){this.gain=gain;}
 @Override public AudioFormat onConfigure(AudioFormat input)throws UnhandledAudioFormatException{if(input.encoding!=C.ENCODING_PCM_16BIT&&input.encoding!=C.ENCODING_PCM_FLOAT)throw new UnhandledAudioFormatException(input);return input;}
 @Override public void queueInput(ByteBuffer input){input.order(ByteOrder.nativeOrder());ByteBuffer output=replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder());if(inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT){while(input.remaining()>=4)output.putFloat(Math.max(-1f,Math.min(1f,input.getFloat()*gain)));}else{while(input.remaining()>=2)output.putShort((short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(input.getShort()*gain))));}output.flip();}
}
