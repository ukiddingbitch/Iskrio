package sk.iskrio.studio;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import androidx.media3.common.audio.AudioProcessor;
import java.io.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Local hardware-assisted H.264/AAC export, preserving source audio by default. Main-thread API. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class VideoExporter {
 interface Listener{void ready(File file);void failed(String message);void cancelled();}
 private Transformer transformer;private File output;private Bitmap overlay;private final Handler handler=new Handler(Looper.getMainLooper());private boolean done;
 boolean active(){return transformer!=null;}
 void start(Context context,File source,long startMs,long endMs,int width,int height,float volume,boolean mute,Bitmap bitmap,IntConsumer progress,Listener listener)throws Exception {
  if(active())throw new IllegalArgumentException("A video export is already running.");
  if(startMs<0||endMs<=startMs||endMs-startMs>MediaLimits.VIDEO_DURATION_MS||width<2||height<2||width>1920||height>1920||!Float.isFinite(volume)||volume<0||volume>1)throw new IllegalArgumentException("Invalid video export settings.");
  if(context.getCacheDir().getUsableSpace()<Math.min(MediaLimits.VIDEO_BYTES,Math.max(64L*1024*1024,source.length()*2)))throw new IllegalArgumentException("Free more storage before exporting this video.");
  File folder=new File(context.getCacheDir(),"video-exports");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Could not create export storage.");
  output=new File(folder,UUID.randomUUID()+".mp4");overlay=bitmap;done=false;
  List<Effect> effects=new ArrayList<>();effects.add(Presentation.createForWidthAndHeight(width,height,Presentation.LAYOUT_SCALE_TO_FIT));
  if(bitmap!=null)effects.add(new OverlayEffect(Collections.singletonList(BitmapOverlay.createStaticBitmapOverlay(bitmap))));
  List<AudioProcessor> audio=new ArrayList<>();if(volume!=1&&!mute)audio.add(new VolumeAudioProcessor(volume));
  MediaItem item=new MediaItem.Builder().setUri(Uri.fromFile(source)).setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build();
  EditedMediaItem edited=new EditedMediaItem.Builder(item).setRemoveAudio(mute).setEffects(new Effects(audio,effects)).build();
  transformer=new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(new Transformer.Listener(){
   @Override public void onCompleted(Composition composition,ExportResult result){if(done)return;File file=output;finish(false);if(file.length()>MediaLimits.VIDEO_BYTES){file.delete();listener.failed("The exported video exceeds 1 GB. Choose a lower resolution.");}else listener.ready(file);}
   @Override public void onError(Composition composition,ExportResult result,ExportException exception){if(done)return;finish(true);listener.failed("Could not export this video on this device. Try 720 px or a different source codec.");}
  }).build();
  transformer.start(edited,output.getAbsolutePath());
  handler.post(new Runnable(){@Override public void run(){if(transformer==null||done)return;ProgressHolder holder=new ProgressHolder();int state=transformer.getProgress(holder);progress.accept(state==Transformer.PROGRESS_STATE_AVAILABLE?holder.progress:-1);if(output.length()>MediaLimits.VIDEO_BYTES){cancel(listener);return;}handler.postDelayed(this,350);}});
 }
 void cancel(Listener listener){if(transformer==null)return;transformer.cancel();finish(true);listener.cancelled();}
 void destroy(){if(transformer!=null){transformer.cancel();finish(true);}}
 private void finish(boolean delete){done=true;handler.removeCallbacksAndMessages(null);transformer=null;if(delete&&output!=null)output.delete();output=null;if(overlay!=null){overlay.recycle();overlay=null;}}
 static void cleanup(Context context){File[] files=new File(context.getCacheDir(),"video-exports").listFiles();if(files!=null)for(File f:files)if(f.isFile()&&System.currentTimeMillis()-f.lastModified()>86400000L&&f.getName().matches("[0-9a-f-]{36}\\.(mp4|iskrio)"))f.delete();}
}
