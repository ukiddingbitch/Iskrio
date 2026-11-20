package sk.iskrio.studio;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Private, disk-backed animation frames; never served from the network. */
final class FrameStore {
    static final String ORIGIN = "https://app.iskrio.local";
    private static final String PATH_PATTERN = "/media/[0-9a-f-]{36}/frame_[0-9]{5}(\\.thumb)?\\.(jpg|png)";
    private final File directory;
    private final String prefix;
    private long bytes;

    FrameStore(Context context) throws IOException {
        String id = UUID.randomUUID().toString();
        directory = new File(new File(context.getFilesDir(), "media"), id);
        if (!directory.mkdirs()) throw new IOException("Could not create frame storage.");
        prefix = ORIGIN + "/media/" + id + "/";
    }
    String add(Bitmap bitmap, int index) throws IOException {return add(bitmap,index,false);}
    String add(Bitmap bitmap,int index,boolean lossless)throws IOException {
        String name=String.format(Locale.ROOT,"frame_%05d.%s",index,lossless?"png":"jpg");
        File frame = new File(directory, name);
        try (OutputStream out = new FileOutputStream(frame)) {
            if (!bitmap.compress(lossless?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG, 88, out)) throw new IOException("Could not save frame.");
        }
        bytes += frame.length();
        if (bytes > 96L * 1024 * 1024) throw new IllegalArgumentException("This animation exceeds the 96 MB working-storage limit. Choose a smaller file.");
        float scale = Math.min(1f, 80f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        Bitmap thumb = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth()*scale)), Math.max(1, Math.round(bitmap.getHeight()*scale)), true);
        try (OutputStream out = new FileOutputStream(new File(directory, name.replaceAll("\\.(jpg|png)$", ".thumb.jpg")))) {
            thumb.compress(Bitmap.CompressFormat.JPEG, 65, out);
        } finally { if (thumb != bitmap) thumb.recycle(); }
        return prefix + name;
    }
    void keepOriginalGif(byte[] data)throws IOException {Files.write(new File(directory,"original.gif").toPath(),data);}
    static File originalGif(Context context,String frame)throws IOException {
        File source=new File(resolve(context,Uri.parse(frame)).getParentFile(),"original.gif");
        if(!source.isFile()||source.length()>40L*1024*1024)throw new IOException("The original GIF is unavailable. Import it again.");
        GifStructure.inspect(Files.readAllBytes(source.toPath()));return source;
    }
    static File resolve(Context context, Uri uri) throws IOException {
        if (!"https".equals(uri.getScheme()) || !"app.iskrio.local".equals(uri.getHost()) || uri.getPort()!=-1 || uri.getQuery()!=null || uri.getFragment()!=null) throw new IOException("Invalid media origin.");
        String path = uri.getPath();
        if (path == null || !path.matches(PATH_PATTERN)) throw new IOException("Invalid frame path.");
        File root = new File(context.getFilesDir(), "media").getCanonicalFile();
        File frame = new File(context.getFilesDir(), path.substring(1)).getCanonicalFile();
        if (!frame.getPath().startsWith(root.getPath()+File.separator) || !frame.isFile()) throw new IOException("Frame not found.");
        return frame;
    }
    static String readDataUrl(Context context, String source) throws IOException {
        File file = resolve(context, Uri.parse(source));
        if (file.length()>16*1024*1024) throw new IOException("Frame is too large.");
        return "data:image/"+(file.getName().endsWith(".png")?"png":"jpeg")+";base64," + Base64.encodeToString(Files.readAllBytes(file.toPath()), Base64.NO_WRAP);
    }
    void discard() { deleteDirectory(directory); }
    private static void deleteDirectory(File dir) {
        File[] files=dir.listFiles();
        if(files!=null)for(File file:files)if(file.isFile())file.delete();
        dir.delete();
    }
    // Only clean on launch: undo history may still reference an older clip
    // during a session. Corrupt library data must never trigger deletion.
    static void cleanupUnused(Context context) {
        try {
            File library = new File(context.getFilesDir(),"library.json");
            Set<String> used = new HashSet<>();
            if(library.exists())collect(new JSONObject(new String(Files.readAllBytes(library.toPath()), java.nio.charset.StandardCharsets.UTF_8)), used);
            File root=new File(context.getFilesDir(),"media");
            File[] dirs=root.listFiles();
            if(dirs!=null)for(File dir:dirs)if(dir.isDirectory()&&dir.getName().matches("[0-9a-f-]{36}")&&!used.contains(dir.getName()))deleteDirectory(dir);
        } catch(Exception ignored) { /* Retain media when library validation fails. */ }
    }
    private static void collect(Object value, Set<String> used) throws JSONException {
        if(value instanceof JSONObject){JSONObject obj=(JSONObject)value;Iterator<String> keys=obj.keys();while(keys.hasNext())collect(obj.get(keys.next()),used);}
        else if(value instanceof JSONArray){JSONArray arr=(JSONArray)value;for(int i=0;i<arr.length();i++)collect(arr.get(i),used);}
        else if(value instanceof String){Uri uri=Uri.parse((String)value);String path=uri.getPath();if(ORIGIN.equals(uri.getScheme()+"://"+uri.getHost())&&path!=null&&path.matches(PATH_PATTERN))used.add(path.split("/")[2]);}
    }
}
