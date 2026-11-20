package sk.iskrio.studio;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Base64;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Converts already downloaded, private media. No remote protocols are enabled. */
final class StudioLinkMedia {
 static File photo(Context context,String data)throws Exception {
  if(!data.matches("(?s)^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\r\\n]+$"))throw new IOException("Unsupported downloaded image.");
  byte[] bytes=Base64.decode(data.substring(data.indexOf(',')+1),Base64.DEFAULT);
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
  if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>2048||bounds.outHeight>2048||(long)bounds.outWidth*bounds.outHeight>4194304)throw new IOException("Invalid downloaded image dimensions.");
  File out=File.createTempFile("studio-link-",".png",context.getCacheDir());try{Files.write(out.toPath(),bytes);return out;}catch(Exception e){out.delete();throw e;}
 }
 static File gif(Context context,File source,AtomicBoolean cancelled)throws Exception {
  if(source==null||!source.isFile()||source.length()>40L*1024*1024)throw new IOException("This downloaded GIF is unavailable or too large.");
  GifStructure.inspect(Files.readAllBytes(source.toPath()));
  if(cancelled.get())throw new CancellationException();
  if(context.getCacheDir().getUsableSpace()<128L*1024*1024)throw new IOException("Free storage before importing this GIF.");
  File out=File.createTempFile("studio-gif-",".mp4",context.getCacheDir());boolean complete=false;
  try{
   List<String> args=new ArrayList<>(Arrays.asList("-hide_banner","-y","-nostdin","-loglevel","error","-threads","2","-filter_threads","1"));
   if(android.os.Build.SUPPORTED_ABIS.length>0&&"arm64-v8a".equals(android.os.Build.SUPPORTED_ABIS[0]))args.addAll(Arrays.asList("-cpuflags","0"));
   args.addAll(Arrays.asList("-protocol_whitelist","file,pipe","-ignore_loop","1","-i",source.getAbsolutePath(),"-vf","scale=ceil(iw/2)*2:ceil(ih/2)*2:flags=lanczos,fps=30,format=yuv420p","-an","-c:v","libx264","-preset","veryfast","-crf","18","-threads","2","-t","600","-fs","1073741824","-movflags","+faststart",out.getAbsolutePath()));
   FFmpegSession session=FFmpegKit.executeWithArguments(args.toArray(new String[0]));
   if(cancelled.get())throw new CancellationException();
   if(!ReturnCode.isSuccess(session.getReturnCode())||out.length()==0)throw new IOException("Could not convert this GIF into an editable video.");
   complete=true;return out;
  }finally{if(!complete)out.delete();}
 }
 static File audio(Context context,File source,AtomicBoolean cancelled)throws Exception {
  VideoSource.requireLocal(android.net.Uri.fromFile(source));
  StudioMedia.audioDuration(context,android.net.Uri.fromFile(source));
  if(cancelled.get())throw new CancellationException();
  File out=File.createTempFile("studio-audio-",".m4a",context.getCacheDir());boolean complete=false;
  try{
   FFmpegSession session=FFmpegKit.executeWithArguments(new String[]{"-hide_banner","-y","-nostdin","-loglevel","error","-protocol_whitelist","file,pipe","-i",source.getAbsolutePath(),"-map","0:a:0","-vn","-c:a","aac","-b:a","192k","-ar","48000","-ac","2","-threads","2","-t","600","-fs","1073741824","-movflags","+faststart",out.getAbsolutePath()});
   if(cancelled.get())throw new CancellationException();
   if(!ReturnCode.isSuccess(session.getReturnCode())||out.length()==0)throw new IOException("Could not extract this audio.");
   complete=true;return out;
  }finally{if(!complete)out.delete();}
 }
 private StudioLinkMedia(){}
}
