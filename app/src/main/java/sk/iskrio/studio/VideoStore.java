package sk.iskrio.studio;
import android.content.Context;
import android.net.Uri;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Original video/audio stay in private files; playback uses bounded local byte ranges. */
final class VideoStore {
 static final String ORIGIN="https://app.iskrio.local";
 static final String PATTERN="/videos/[0-9a-f-]{36}/source\\.(mp4|webm|png|jpg)";
 static JSONObject importVideo(Context context,Uri uri,IntConsumer progress)throws Exception {
  VideoSource.requireLocal(uri);VideoInfo info=VideoInfo.inspect(context,uri,progress);
  File directory=new File(new File(context.getFilesDir(),"videos"),UUID.randomUUID().toString());if(!directory.mkdirs())throw new IOException("Could not create video storage.");
  boolean complete=false;File target=null;
  try(InputStream in=context.getContentResolver().openInputStream(uri)){
   if(in==null)throw new IOException("Could not open this video.");byte[] head=new byte[16];int read=0,n;while(read<head.length&&(n=in.read(head,read,head.length-read))>0)read+=n;
   boolean webm=read>=4&&(head[0]&255)==26&&(head[1]&255)==69&&(head[2]&255)==223&&(head[3]&255)==163;
   target=new File(directory,"source."+(webm?"webm":"mp4"));long total=read;
   if("file".equals(uri.getScheme())){File cached=new File(uri.getPath()).getCanonicalFile();File cache=new File(context.getCacheDir(),"link-imports").getCanonicalFile();if(cached.getPath().startsWith(cache.getPath()+File.separator)&&cached.length()<=MediaLimits.VIDEO_BYTES&&cached.renameTo(target)){MediaLimits.video(target.length(),info.durationMs);progress.accept(100);complete=true;return new JSONObject().put("src",ORIGIN+"/videos/"+directory.getName()+"/"+target.getName()).put("durationMs",info.durationMs).put("width",info.width).put("height",info.height).put("hasAudio",info.audio).put("hdr",info.hdr()).put("bytes",target.length());}}

   long sourceBytes=0;try(android.content.res.AssetFileDescriptor descriptor=context.getContentResolver().openAssetFileDescriptor(uri,"r")){if(descriptor!=null)sourceBytes=Math.max(0,descriptor.getLength());}if(sourceBytes>MediaLimits.VIDEO_BYTES)throw new IllegalArgumentException("This video exceeds the 1 GB import limit.");
   if(context.getFilesDir().getUsableSpace()<sourceBytes+64L*1024*1024)throw new IllegalArgumentException("Not enough free storage to import video.");
   try(OutputStream out=new FileOutputStream(target)){out.write(head,0,read);byte[] buffer=new byte[65536];while((n=in.read(buffer))!=-1){progress.accept(0);total+=n;if(total>MediaLimits.VIDEO_BYTES)throw new IllegalArgumentException("This video exceeds the 1 GB import limit.");out.write(buffer,0,n);}}
   MediaLimits.video(total,info.durationMs);progress.accept(100);complete=true;
   return new JSONObject().put("src",ORIGIN+"/videos/"+directory.getName()+"/"+target.getName()).put("durationMs",info.durationMs).put("width",info.width).put("height",info.height).put("hasAudio",info.audio).put("hdr",info.hdr()).put("bytes",total);
  }finally{if(!complete){if(target!=null)target.delete();directory.delete();}}
 }
 static File resolve(Context context,String source)throws IOException {
  Uri uri=Uri.parse(source);String path=uri.getPath();if(!"https".equals(uri.getScheme())||!"app.iskrio.local".equals(uri.getHost())||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null||path==null||!path.matches(PATTERN))throw new IOException("Invalid private video reference.");
  File root=new File(context.getFilesDir(),"videos").getCanonicalFile(),file=new File(context.getFilesDir(),path.substring(1)).getCanonicalFile();if(!file.getPath().startsWith(root.getPath()+File.separator)||!file.isFile())throw new IOException("The source video is missing. Restore a video project backup.");return file;
 }
 static WebResourceResponse response(Context context,WebResourceRequest request)throws IOException {
  return responseFile(resolve(context,request.getUrl().toString()),request);
 }
 static WebResourceResponse responseFile(File file,WebResourceRequest request)throws IOException {
  long length=file.length(),start=0,end=length-1;boolean partial=false;
  String range=null;for(Map.Entry<String,String> h:request.getRequestHeaders().entrySet())if(h.getKey().equalsIgnoreCase("Range"))range=h.getValue();
  if(range!=null){try{if(!range.matches("bytes=[0-9]*-[0-9]*"))throw new IOException();String[] parts=range.substring(6).split("-",-1);if(parts[0].isEmpty()){long suffix=Long.parseLong(parts[1]);start=Math.max(0,length-suffix);}else{start=Long.parseLong(parts[0]);if(!parts[1].isEmpty())end=Math.min(end,Long.parseLong(parts[1]));}if(start<0||start>=length||end<start)throw new IOException();partial=true;}catch(Exception e){Map<String,String> headers=new HashMap<>();headers.put("Content-Range","bytes */"+length);return new WebResourceResponse("text/plain",null,416,"Range Not Satisfiable",headers,new ByteArrayInputStream(new byte[0]));}}
  final long count=end-start+1;Map<String,String> headers=new HashMap<>();headers.put("Accept-Ranges","bytes");headers.put("Content-Length",Long.toString(count));headers.put("Cache-Control","no-store");if(partial)headers.put("Content-Range","bytes "+start+"-"+end+"/"+length);
  // Android WebView's stream loader applies the request Range itself via available()/skip().
  // Always supply the complete seekable file at offset zero to avoid skipping twice.
  InputStream body="HEAD".equalsIgnoreCase(request.getMethod())?new ByteArrayInputStream(new byte[0]):new FileInputStream(file);
  return new WebResourceResponse(file.getName().endsWith(".png")?"image/png":file.getName().endsWith(".jpg")?"image/jpeg":file.getName().endsWith("webm")?"video/webm":"video/mp4",null,partial?206:200,partial?"Partial Content":"OK",headers,body);
 }
 static void discard(Context context,String source){try{File file=resolve(context,source);file.delete();file.getParentFile().delete();}catch(IOException ignored){}}
 static void cleanup(Context context){try{File library=new File(context.getFilesDir(),"library.json");Set<String> used=new HashSet<>();if(library.exists())collect(new JSONObject(new String(java.nio.file.Files.readAllBytes(library.toPath()),java.nio.charset.StandardCharsets.UTF_8)),used);File[] dirs=new File(context.getFilesDir(),"videos").listFiles();if(dirs!=null)for(File dir:dirs)if(dir.isDirectory()&&dir.getName().matches("[0-9a-f-]{36}")&&!used.contains(dir.getName())){File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();dir.delete();}}catch(Exception ignored){}}
 private static void collect(Object value,Set<String> used)throws JSONException{if(value instanceof JSONObject){JSONObject o=(JSONObject)value;Iterator<String> keys=o.keys();while(keys.hasNext())collect(o.get(keys.next()),used);}else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collect(a.get(i),used);}else if(value instanceof String){Uri uri=Uri.parse((String)value);String path=uri.getPath();if(ORIGIN.equals(uri.getScheme()+"://"+uri.getHost())&&path!=null&&path.matches(PATTERN))used.add(path.split("/")[2]);}}
}
