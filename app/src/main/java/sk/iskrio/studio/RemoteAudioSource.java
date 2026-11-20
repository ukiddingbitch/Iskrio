package sk.iskrio.studio;
import java.io.IOException;
/** Exact bounded byte-range transport; callers never request a complete video stream. */
final class RemoteAudioSource {
 interface Reader {byte[] read(long offset,int length)throws IOException;}
 private RemoteAudioSource(){}
}
