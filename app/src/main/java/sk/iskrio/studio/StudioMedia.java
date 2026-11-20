package sk.iskrio.studio;

import android.content.Context;
import android.graphics.*;
import android.media.MediaFormat;
import android.net.Uri;
import android.webkit.*;
import androidx.media3.inspector.MediaExtractorCompat;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Private original audio and photo clips. No network data source is accepted. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class StudioMedia {
 static final String AUDIO_PATTERN="/audio/[0-9a-f-]{36}/source\\.(mp4|wav|mp3|ogg|flac|aac|m4a)";
 static File resolve(Context context,String source)throws IOException {
  if(source.startsWith(VideoStore.ORIGIN+"/videos/"))return VideoStore.resolve(context,source);
  Uri u=Uri.parse(source);String p=u.getPath();if(!"https".equals(u.getScheme())||!"app.iskrio.local".equals(u.getHost())||u.getPort()!=-1||u.getQuery()!=null||u.getFragment()!=null||p==null||!p.matches(AUDIO_PATTERN))throw new IOException("Invalid local media reference.");
  File root=new File(context.getFilesDir(),"audio").getCanonicalFile(),f=new File(context.getFilesDir(),p.substring(1)).getCanonicalFile();if(!f.getPath().startsWith(root.getPath()+File.separator)||!f.isFile())throw new IOException("The original audio is missing. Restore a project backup.");return f;
 }
 static JSONObject importMedia(Context context,Uri uri,boolean audio,IntConsumer progress)throws Exception {
  VideoSource.requireLocal(uri);
  if(!audio){try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,o);if(o.outWidth>0&&o.outHeight>0)return importPhoto(context,uri,o,progress);}
   JSONObject info=VideoStore.importVideo(context,uri,progress);addPreview(context,info);return info;
  }
  long duration=audioDuration(context,uri);String extension="mp4";
  try(InputStream in=context.getContentResolver().openInputStream(uri)){byte[] h=new byte[12];int n=in.read(h);if(n>4){String a=new String(h,0,4,java.nio.charset.StandardCharsets.ISO_8859_1);if(a.equals("RIFF"))extension="wav";else if(a.equals("OggS"))extension="ogg";else if(a.equals("fLaC"))extension="flac";else if(a.startsWith("ID3")||((h[0]&255)==255&&(h[1]&224)==224))extension="mp3";}}
  File dir=new File(new File(context.getFilesDir(),"audio"),UUID.randomUUID().toString());if(!dir.mkdirs())throw new IOException();File file=new File(dir,"source."+extension);boolean complete=false;
  try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){byte[] buffer=new byte[65536];long bytes=0;int n;while((n=in.read(buffer))!=-1){progress.accept(0);bytes+=n;if(bytes>MediaLimits.VIDEO_BYTES)throw new IllegalArgumentException("Audio imports must be 1 GB or smaller.");out.write(buffer,0,n);}MediaLimits.video(bytes,duration);complete=true;JSONObject info=new JSONObject().put("src",VideoStore.ORIGIN+"/audio/"+dir.getName()+"/"+file.getName()).put("durationMs",duration).put("bytes",bytes).put("hasAudio",true);out.flush();StudioWaveform.add(context,file,info);return info;
  }finally{if(!complete){file.delete();dir.delete();}}
 }
 static void addPreview(Context context,JSONObject info){android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever();Bitmap bitmap=null;try{File file=VideoStore.resolve(context,info.getString("src"));retriever.setDataSource(file.getAbsolutePath());bitmap=android.os.Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,160,160):retriever.getFrameAtTime(0);if(bitmap!=null){int w=bitmap.getWidth(),h=bitmap.getHeight();float scale=Math.min(1,160f/Math.max(w,h));Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,70,bytes);info.put("thumb","data:image/jpeg;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));if(small!=bitmap)small.recycle();}}catch(Exception ignored){}finally{if(bitmap!=null)bitmap.recycle();try{retriever.release();}catch(Exception ignored){}}}
 static long audioDuration(Context context,Uri uri)throws Exception {
  VideoSource.requireLocal(uri);MediaExtractorCompat e=new MediaExtractorCompat(context);try{e.setDataSource(context,uri,null);long duration=0;boolean found=false;for(int i=0;i<e.getTrackCount();i++){MediaFormat f=e.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){found=true;if(f.containsKey(MediaFormat.KEY_DURATION))duration=Math.max(duration,f.getLong(MediaFormat.KEY_DURATION));}}
   if(!found)throw new IllegalArgumentException("This file has no audio track.");long ms=(duration+999)/1000;MediaLimits.video(1,ms);return ms;
  }finally{e.release();}
 }
 private static JSONObject importPhoto(Context context,Uri uri,BitmapFactory.Options bounds,IntConsumer progress)throws Exception {
  if((long)bounds.outWidth*bounds.outHeight>100000000L)throw new IllegalArgumentException("This photo is too large.");int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2;BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;Bitmap b;
  try(InputStream in=context.getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,options);}if(android.os.Build.VERSION.SDK_INT>=28){try{Bitmap oriented=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),uri),(decoder,info,src)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);android.util.Size size=info.getSize();float factor=Math.min(1,2048f/Math.max(size.getWidth(),size.getHeight()));decoder.setTargetSize(Math.max(1,Math.round(size.getWidth()*factor)),Math.max(1,Math.round(size.getHeight()*factor)));});if(b!=null)b.recycle();b=oriented;}catch(Exception ignored){}}if(b==null)throw new IOException("Could not decode the photo.");
  File dir=new File(new File(context.getFilesDir(),"videos"),UUID.randomUUID().toString());if(!dir.mkdirs()){b.recycle();throw new IOException();}File file=new File(dir,"source.png");boolean complete=false;
  try(OutputStream out=new FileOutputStream(file)){progress.accept(0);b.compress(Bitmap.CompressFormat.PNG,100,out);JSONObject info=new JSONObject().put("src",VideoStore.ORIGIN+"/videos/"+dir.getName()+"/source.png").put("durationMs",3000).put("width",b.getWidth()).put("height",b.getHeight()).put("hasAudio",false).put("isImage",true);complete=true;return info;}finally{b.recycle();if(!complete){file.delete();dir.delete();}}
 }
 static void inspectRestored(Context context,File file,boolean audio,IntConsumer progress)throws Exception {
  if(audio){audioDuration(context,Uri.fromFile(file));return;}if(file.getName().endsWith(".png")){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),o);if(o.outWidth<1||o.outHeight<1||o.outWidth>2048||o.outHeight>2048)throw new IllegalArgumentException("Invalid photo video.");return;}VideoInfo.inspect(context,Uri.fromFile(file),progress);
 }
 static void cleanup(Context context){try{File library=new File(context.getFilesDir(),"library.json");String json=library.exists()?new String(java.nio.file.Files.readAllBytes(library.toPath()),java.nio.charset.StandardCharsets.UTF_8):"";File[] dirs=new File(context.getFilesDir(),"audio").listFiles();if(dirs!=null)for(File d:dirs)if(d.isDirectory()&&d.getName().matches("[0-9a-f-]{36}")&&!json.contains("/audio/"+d.getName()+"/")){File[] files=d.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();d.delete();}}catch(Exception ignored){}}
 static WebResourceResponse response(Context context,WebResourceRequest request)throws IOException {
  File f=resolve(context,request.getUrl().toString());Map<String,String> h=new HashMap<>();h.put("Accept-Ranges","bytes");h.put("Cache-Control","no-store");String range=request.getRequestHeaders().get("Range");long size=f.length(),start=0,end=size-1;boolean partial=false;if(range!=null){try{String[] p=range.substring(6).split("-",-1);start=p[0].isEmpty()?Math.max(0,size-Long.parseLong(p[1])):Long.parseLong(p[0]);if(!p[0].isEmpty()&&!p[1].isEmpty())end=Math.min(end,Long.parseLong(p[1]));if(start<0||start>end||start>=size)throw new IOException();partial=true;}catch(Exception e){return new WebResourceResponse("text/plain",null,416,"Range Not Satisfiable",Collections.singletonMap("Content-Range","bytes */"+size),new ByteArrayInputStream(new byte[0]));}}
  h.put("Content-Length",Long.toString(end-start+1));if(partial)h.put("Content-Range","bytes "+start+"-"+end+"/"+size);String name=f.getName(),mime=name.endsWith("wav")?"audio/wav":name.endsWith("mp3")?"audio/mpeg":name.endsWith("ogg")?"audio/ogg":name.endsWith("flac")?"audio/flac":"audio/mp4";return new WebResourceResponse(mime,null,partial?206:200,partial?"Partial Content":"OK",h,new FileInputStream(f));
 }
}
