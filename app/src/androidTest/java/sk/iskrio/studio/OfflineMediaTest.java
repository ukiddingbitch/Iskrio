package sk.iskrio.studio;
import android.app.*;
import android.os.Bundle;
import android.media.MediaExtractor;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;

/** Device regression for partial, audio-only demuxing. Optional public-HTTPS integration input. */
public final class OfflineMediaTest extends android.app.Instrumentation {
 private String url;
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);url=arguments==null?null:arguments.getString("audioUrl");start();}
 @Override public void onStart(){Bundle result=new Bundle();File out=null;LinkImporter task=null;try{
  final long[] bytes={0};long sourceSize;JSONObject report=new JSONObject();
  if(url!=null){task=new LinkImporter(()->true);out=task.downloadAudio(getTargetContext(),url,p->{});sourceSize=task.audioSourceSize();bytes[0]=task.audioBytesReceived();task.close();if(task.connectionCount()!=0)throw new AssertionError("Network connections remained open.");report.put("network",true);}
  else{File input=new File(getTargetContext().getCacheDir(),"Iskrio25-range.mp4");sourceSize=input.length();out=File.createTempFile("range-audio-",".m4a",getTargetContext().getCacheDir());try(RandomAccessFile file=new RandomAccessFile(input,"r")){final RemoteAudioSource.Reader reader=(offset,length)->{synchronized(file){byte[] data=new byte[length];file.seek(offset);file.readFully(data);bytes[0]+=length;return data;}};AudioOnlyDownload.copyRanges(sourceSize,reader,out,p->{},()->{});}if(bytes[0]>=sourceSize/2)throw new AssertionError("Audio-only demux read too much video data: "+bytes[0]+" / "+sourceSize);report.put("network",false);}
  MediaExtractor extractor=new MediaExtractor();int audio=0,video=0;try{extractor.setDataSource(out.getAbsolutePath());for(int i=0;i<extractor.getTrackCount();i++){String mime=extractor.getTrackFormat(i).getString("mime");if(mime.startsWith("audio/"))audio++;if(mime.startsWith("video/"))video++;}}finally{extractor.release();}
  if(audio!=1||video!=0)throw new AssertionError("Output contains a video stream.");
  report.put("audioStreams",audio).put("videoStreams",video).put("durationMs",StudioMedia.audioDuration(getTargetContext(),android.net.Uri.fromFile(out))).put("bytes",out.length()).put("sourceBytes",sourceSize).put("readBytes",bytes[0]);
  if(url==null){JSONObject imported=StudioMedia.importMedia(getTargetContext(),android.net.Uri.fromFile(out),true,p->{});Files.write(new File(getTargetContext().getCacheDir(),"audio-import-info.json").toPath(),imported.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  Files.copy(out.toPath(),new File(getTargetContext().getCacheDir(),"range-audio-result.m4a").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  Files.write(new File(getTargetContext().getCacheDir(),url==null?"range-audio-test.json":"network-audio-test.json").toPath(),report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}finally{if(task!=null)task.close();if(out!=null)out.delete();}}
}
