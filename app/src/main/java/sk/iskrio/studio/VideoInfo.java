package sk.iskrio.studio;
import android.content.Context;
import android.media.MediaFormat;
import android.net.Uri;
import androidx.media3.inspector.MediaExtractorCompat;
import java.util.function.IntConsumer;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class VideoInfo {
 final long durationMs;final int width,height,transfer;final boolean audio;
 private VideoInfo(long d,int w,int h,boolean a,int t){durationMs=d;width=w;height=h;audio=a;transfer=t;}
 boolean hdr(){return transfer==MediaFormat.COLOR_TRANSFER_ST2084||transfer==MediaFormat.COLOR_TRANSFER_HLG;}
 static VideoInfo inspect(Context context,Uri uri,IntConsumer progress)throws Exception {
  VideoSource.requireLocal(uri);MediaExtractorCompat extractor=new MediaExtractorCompat(context);
  try{extractor.setDataSource(context,uri,null);int video=-1,w=0,h=0,rotation=0,transfer=0;long duration=0;boolean audio=false;double rate=30;
   for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime==null)continue;if(mime.startsWith("audio/"))audio=true;if(video<0&&mime.startsWith("video/")){video=i;w=integer(f,MediaFormat.KEY_WIDTH,0);h=integer(f,MediaFormat.KEY_HEIGHT,0);rotation=integer(f,MediaFormat.KEY_ROTATION,0);transfer=integer(f,MediaFormat.KEY_COLOR_TRANSFER,0);duration=longValue(f,MediaFormat.KEY_DURATION,0);rate=integer(f,MediaFormat.KEY_FRAME_RATE,30);}}
   if(video<0||w<1||h<1)throw new IllegalArgumentException("This file has no playable video track.");
   if(w>4096||h>4096||(long)w*h>16777216)throw new IllegalArgumentException("This video's resolution is too large. Use a 4K or smaller source.");
   if(duration<=0){extractor.selectTrack(video);VideoTimeline timeline=new VideoTimeline();long deadline=System.nanoTime()+60000000000L;do{progress.accept(0);if(System.nanoTime()>deadline)throw new IllegalArgumentException("Could not inspect this video in time.");long time=extractor.getSampleTime();if(time<0)break;timeline.add(time);}while(extractor.advance());duration=timeline.durationUs(0,rate);}
   long ms=(duration+999)/1000;MediaLimits.video(1,ms);if(rotation==90||rotation==270){int t=w;w=h;h=t;}return new VideoInfo(ms,w,h,audio,transfer);
  }finally{extractor.release();}
 }
 private static int integer(MediaFormat f,String key,int fallback){try{return f.getInteger(key);}catch(Exception e){return fallback;}}
 private static long longValue(MediaFormat f,String key,long fallback){try{return f.getLong(key);}catch(Exception e){return fallback;}}
}
