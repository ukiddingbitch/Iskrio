package sk.iskrio.studio;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import org.json.*;
import java.io.*;
import java.util.*;

/** Disposable local preview files. Original project references are never rewritten. */
final class StudioPreviewCache {
 static final long LIMIT=256L*1024*1024;
 private static File root(Context c){return new File(c.getCacheDir(),"studio-proxies");}
 private static String id(Context c,String source)throws IOException{File f=VideoStore.resolve(c,source);if(f.getName().endsWith("png")||f.getName().endsWith("jpg"))throw new IOException("Photos do not need optimized previews.");return f.getParentFile().getName();}
 static String find(Context c,String source){try{String id=id(c,source);File f=new File(root(c),id+".mp4");if(f.isFile()&&f.length()>0)return VideoStore.ORIGIN+"/proxy/"+id+".mp4";}catch(IOException ignored){}return "";}
 static long bytes(Context c){long n=0;File[] files=root(c).listFiles();if(files!=null)for(File f:files)if(f.isFile())n+=f.length();return n;}
 static void clear(Context c){File[] files=root(c).listFiles();if(files!=null)for(File f:files)if(f.isFile()&&f.getName().matches("[0-9a-f-]{36}\\.mp4"))f.delete();}
 static void cleanup(Context c){File[] files=root(c).listFiles();if(files!=null)for(File f:files)if(f.isFile()&&System.currentTimeMillis()-f.lastModified()>7L*24*3600*1000)f.delete();}
 static JSONObject store(Context c,String source,File output)throws Exception{String id=id(c,source);File dir=root(c);if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create preview cache.");File target=new File(dir,id+".mp4");if(bytes(c)+output.length()-(target.exists()?target.length():0)>LIMIT)throw new IOException("Preview cache is full. Clear it in Settings > Preview storage.");if(!output.renameTo(target))throw new IOException("Could not store optimized preview.");return new JSONObject().put("original",source).put("src",VideoStore.ORIGIN+"/proxy/"+id+".mp4").put("bytes",target.length());}
 static JSONObject request(Context c,String source)throws Exception{File file=VideoStore.resolve(c,source);VideoInfo info=VideoInfo.inspect(c,Uri.fromFile(file),ignored->{});JSONObject clip=new JSONObject().put("id","proxy").put("src",source).put("startMs",0).put("endMs",info.durationMs).put("durationMs",info.durationMs).put("width",info.width).put("height",info.height).put("hasAudio",info.audio);JSONObject p=new JSONObject().put("w",Math.max(64,info.width)).put("h",Math.max(64,info.height)).put("bg","#000000").put("video",new JSONObject().put("clips",new JSONArray().put(clip)));return new JSONObject().put("project",p).put("purpose","optimize").put("size",720).put("fps",30).put("quality","compact");}
 static WebResourceResponse response(Context c,WebResourceRequest request)throws IOException{Uri uri=request.getUrl();String p=uri.getPath();if(!"https".equals(uri.getScheme())||!"app.iskrio.local".equals(uri.getHost())||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null||p==null||!p.matches("/proxy/[0-9a-f-]{36}\\.mp4"))throw new IOException("Invalid preview reference.");File parent=root(c).getCanonicalFile(),file=new File(parent,p.substring("/proxy/".length())).getCanonicalFile();if(!file.getParentFile().equals(parent)||!file.isFile())throw new IOException("Optimized preview is missing.");return VideoStore.responseFile(file,request);}
 static JSONObject freezeFrame(Context c,String source,long timeMs)throws Exception {
  File original=VideoStore.resolve(c,source);if(timeMs<0||timeMs>600000)throw new IOException("Invalid frame time.");android.graphics.Bitmap bitmap;
  if(original.getName().endsWith("png")||original.getName().endsWith("jpg"))bitmap=android.graphics.BitmapFactory.decodeFile(original.getAbsolutePath());
  else {VideoInfo info=VideoInfo.inspect(c,Uri.fromFile(original),ignored->{});android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();try{reader.setDataSource(original.getAbsolutePath());double scale=Math.min(1,2048.0/Math.max(info.width,info.height));long us=Math.min(timeMs,Math.max(0,info.durationMs-1))*1000;bitmap=android.os.Build.VERSION.SDK_INT>=27?reader.getScaledFrameAtTime(us,android.media.MediaMetadataRetriever.OPTION_CLOSEST,Math.max(1,(int)(info.width*scale)),Math.max(1,(int)(info.height*scale))):reader.getFrameAtTime(us,android.media.MediaMetadataRetriever.OPTION_CLOSEST);}finally{reader.release();}}
  if(bitmap==null)throw new IOException("Could not decode the freeze frame.");if(Math.max(bitmap.getWidth(),bitmap.getHeight())>2048){double scale=2048.0/Math.max(bitmap.getWidth(),bitmap.getHeight());android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true);if(small!=bitmap)bitmap.recycle();bitmap=small;}
  File dir=new File(new File(c.getFilesDir(),"videos"),UUID.randomUUID().toString());if(!dir.mkdirs()){bitmap.recycle();throw new IOException("Could not create frame storage.");}File file=new File(dir,"source.png");boolean done=false;try(OutputStream out=new FileOutputStream(file)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not save freeze frame.");out.flush();JSONObject result=new JSONObject().put("src",VideoStore.ORIGIN+"/videos/"+dir.getName()+"/source.png").put("width",bitmap.getWidth()).put("height",bitmap.getHeight()).put("durationMs",2000).put("isImage",true).put("hasAudio",false);done=true;return result;}finally{bitmap.recycle();if(!done){file.delete();dir.delete();}}
 }
}
