package sk.iskrio.studio;
import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.zip.*;

/** Portable video project/library packages. Media are streamed, never Base64 video strings. */
final class VideoArchive {
 static final int MAX_ENTRIES=1+60*(20+20+3);
 static void checkEntryCount(int count){if(count<1||count>MAX_ENTRIES)throw new IllegalArgumentException("This backup contains too many media files. Export individual projects.");}
 static final long MAX_TOTAL=2048L*1024*1024;
 static File create(Context context,String json,IntConsumer progress)throws Exception {
  if(json.length()>32*1024*1024)throw new IllegalArgumentException("Project metadata exceeds 32 MB.");JSONObject root=new JSONObject(json);validateRoot(root);
  Map<String,File> files=new LinkedHashMap<>();collectExport(context,root,files);checkEntryCount(files.size()+1);long total=0;for(File f:files.values())total+=f.length();if(total>MAX_TOTAL)throw new IllegalArgumentException("This library exceeds the 2 GB backup limit. Export individual video projects.");
  if(context.getCacheDir().getUsableSpace()<total+32L*1024*1024)throw new IllegalArgumentException("Free more storage before creating this backup.");
  File folder=new File(context.getCacheDir(),"video-exports");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException();File output=new File(folder,UUID.randomUUID()+".iskrio");boolean complete=false;
  try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(output))){zip.putNextEntry(new ZipEntry("project.json"));zip.write(json.getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.setLevel(Deflater.NO_COMPRESSION);byte[] buffer=new byte[65536];long copied=0;
   for(Map.Entry<String,File> entry:files.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));try(InputStream in=new FileInputStream(entry.getValue())){int n;while((n=in.read(buffer))!=-1){progress.accept(total==0?100:(int)(copied*100/total));zip.write(buffer,0,n);copied+=n;}}zip.closeEntry();}progress.accept(100);complete=true;
  }finally{if(!complete)output.delete();}return output;
 }
 static JSONObject restore(Context context,InputStream input,IntConsumer progress)throws Exception {
  Map<String,String> media=new HashMap<>();List<File> created=new ArrayList<>();String metadata=null;long total=0;int entries=0;boolean complete=false;
  try(ZipInputStream zip=new ZipInputStream(input)){
   ZipEntry entry;byte[] buffer=new byte[65536];while((entry=zip.getNextEntry())!=null){progress.accept(0);checkEntryCount(++entries);if(entry.isDirectory())throw new IllegalArgumentException("Invalid video project package.");String name=entry.getName();
    if(name.equals("project.json")){if(metadata!=null)throw new IllegalArgumentException("Duplicate project metadata.");ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1){progress.accept(0);if(out.size()+n>32*1024*1024)throw new IllegalArgumentException("Project metadata exceeds 32 MB.");out.write(buffer,0,n);}metadata=out.toString(StandardCharsets.UTF_8.name());}
    else{if(!validMediaEntry(name)||media.containsKey(name))throw new IllegalArgumentException("Invalid video package path.");File directory=new File(new File(context.getFilesDir(),name.startsWith("audio/")?"audio":"videos"),UUID.randomUUID().toString());if(!directory.mkdirs())throw new IOException();File file=new File(directory,name.substring(name.lastIndexOf('/')+1));created.add(file);long size=0;
     try(OutputStream out=new FileOutputStream(file)){int n;while((n=zip.read(buffer))!=-1){progress.accept(0);size+=n;total+=n;if(size>MediaLimits.VIDEO_BYTES||total>MAX_TOTAL)throw new IllegalArgumentException("This video package exceeds the import limits.");out.write(buffer,0,n);}}
     StudioMedia.inspectRestored(context,file,name.startsWith("audio/"),progress);media.put(name,VideoStore.ORIGIN+"/"+(name.startsWith("audio/")?"audio":"videos")+"/"+directory.getName()+"/"+file.getName());}
    zip.closeEntry();}
   if(metadata==null)throw new IllegalArgumentException("Project metadata is missing.");JSONObject root=new JSONObject(metadata);validateRoot(root);rewrite(root,media);complete=true;progress.accept(100);return root;
  }finally{if(!complete)for(File file:created){file.delete();file.getParentFile().delete();}}
 }
 static boolean validMediaEntry(String name){return name!=null&&name.matches("(videos/[0-9a-f-]{36}/source\\.(mp4|webm|png|jpg)|audio/[0-9a-f-]{36}/source\\.(mp4|m4a|wav|mp3|ogg|flac|aac))");}
 private static void validateRoot(JSONObject root){String format=root.optString("format");if(!format.equals("iskrio-project")&&!format.equals("iskrio-backup"))throw new IllegalArgumentException("This is not an Iskrio project package.");}
 private static void collectExport(Context context,Object node,Map<String,File> files)throws Exception {
  if(node instanceof JSONObject){JSONObject o=(JSONObject)node;Iterator<String> keys=o.keys();while(keys.hasNext())collectExport(context,o.get(keys.next()),files);}
  else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectExport(context,a.get(i),files);}
  else if(node instanceof String){String source=(String)node;Uri uri=Uri.parse(source);String path=uri.getPath();if(path!=null&&path.length()>1&&validMediaEntry(path.substring(1))&&source.startsWith(VideoStore.ORIGIN+"/")){files.put(path.substring(1),StudioMedia.resolve(context,source));}}
 }
 private static String replaceSource(String source,Map<String,String> media){
  Uri uri=Uri.parse(source);String path=uri.getPath();if(path==null||path.length()<2||!validMediaEntry(path.substring(1))||!source.startsWith(VideoStore.ORIGIN+"/"))return source;
  if(uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()!=-1||!media.containsKey(path.substring(1)))throw new IllegalArgumentException("An original media file is missing from this package.");return media.get(path.substring(1));
 }
 private static void rewrite(Object node,Map<String,String> media)throws JSONException {
  if(node instanceof JSONObject){JSONObject object=(JSONObject)node;Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();Object value=object.get(key);if(value instanceof String)object.put(key,replaceSource((String)value,media));else rewrite(value,media);}}
  else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){Object value=a.get(i);if(value instanceof String)a.put(i,replaceSource((String)value,media));else rewrite(value,media);}}
 }
}
