package sk.iskrio.studio;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.function.IntConsumer;

/** Samples the complete requested interval, with bounded frame RAM. */
final class MediaFrames {
    static JSONObject extractAuto(Context context,String kind,Uri uri,IntConsumer progress)throws Exception {
        VideoSource.requireLocal(uri);progress.accept(0);
        if(isGif(context,uri))return OriginalGif.extract(context,uri,progress);
        try(VideoSource source=VideoSource.open(context,uri,progress)){
            AnimationPlan plan=new AnimationPlan(source.durationMs,source.frameRate);progress.accept(0);
            JSONObject result=extract(context,"video",source.uri,0,plan.durationMs,plan.fps,progress);
            JSONArray delays=new JSONArray();double elapsed=0;
            for(int i=0;i<result.getJSONArray("frames").length();i++){double end=Math.min(plan.durationMs,(i+1)*1000.0/plan.fps);delays.put(end-elapsed);elapsed=end;}
            return result.put("frameDurations",delays).put("loopCount",0).put("automatic",true).put("durationRecovered",source.repaired);
        }
    }

    static boolean isGifHeader(byte[] bytes){return bytes.length>=6&&bytes[0]=='G'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='8'&&(bytes[4]=='7'||bytes[4]=='9')&&bytes[5]=='a';}
    static boolean isGif(Context context,Uri uri)throws IOException {
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Could not open this media file.");
            byte[] header=new byte[6];int offset=0,n;while(offset<header.length&&(n=in.read(header,offset,header.length-offset))>0)offset+=n;
            return offset==6&&isGifHeader(header);
        }
    }
    static JSONObject extract(Context context,String kind,Uri uri,long start,long end,int fps,IntConsumer progress) throws Exception {
        VideoSource.requireLocal(uri);
        if(fps<1||fps>50||start<0||end<=start||end-start>30000)throw new IllegalArgumentException("Choose up to 30 seconds and 1–50 frames/s.");
        FrameStore store = new FrameStore(context);
        JSONArray frames = new JSONArray();
        try {
            if("video".equals(kind)) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(context,uri);
                    String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    long knownDuration=VideoSource.positiveLong(duration);if(knownDuration>0)end=Math.min(end,knownDuration);
                    int count=count(start,end,fps);
                    int width=number(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),640);
                    int height=number(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),640);
                    float scale=Math.min(1f,640f/Math.max(width,height));
                    int targetW=Math.max(1,Math.round(width*scale)), targetH=Math.max(1,Math.round(height*scale));
                    for(int i=0;i<count;i++) {
                        long timeUs=start*1000+Math.round(i*1000000.0/fps);
                        Bitmap bitmap=Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST,targetW,targetH):retriever.getFrameAtTime(timeUs,MediaMetadataRetriever.OPTION_CLOSEST);
                        if(bitmap==null)throw new IOException("Could not decode a video frame.");
                        try{float s=Math.min(1f,640f/Math.max(bitmap.getWidth(),bitmap.getHeight()));Bitmap fitted=s<1?Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*s)),Math.max(1,Math.round(bitmap.getHeight()*s)),true):bitmap;
                            try{frames.put(store.add(fitted,i));}finally{if(fitted!=bitmap)fitted.recycle();}
                        }finally{bitmap.recycle();}
                        progress.accept((i+1)*100/count);
                    }
                }finally{retriever.release();}
            }else{
                byte[] data;
                try(InputStream in=context.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    if(in==null)throw new IOException("Could not open GIF.");
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>40*1024*1024)throw new IllegalArgumentException("The GIF exceeds 40 MB.");out.write(buffer,0,n);}data=out.toByteArray();
                }
                Movie movie=Movie.decodeByteArray(data,0,data.length);
                if(movie==null)throw new IllegalArgumentException("This GIF is not supported.");
                int duration=movie.duration();if(duration<=0)duration=1000;
                end=Math.min(end,duration);int count=count(start,end,fps);
                float scale=Math.min(1f,640f/Math.max(movie.width(),movie.height()));
                Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(movie.width()*scale)),Math.max(1,Math.round(movie.height()*scale)),Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(bitmap);
                try{for(int i=0;i<count;i++){
                    bitmap.eraseColor(Color.WHITE);canvas.save();canvas.scale(scale,scale);
                    movie.setTime((int)(start+Math.floor(i*1000.0/fps)));movie.draw(canvas,0,0);canvas.restore();
                    frames.put(store.add(bitmap,i));progress.accept((i+1)*100/count);
                }}finally{bitmap.recycle();}
            }
            return new JSONObject().put("frames",frames).put("fps",fps).put("durationMs",end-start);
        }catch(Throwable error){store.discard();throw error;}
    }
    private static int count(long start,long end,int fps){
        if(end<=start)throw new IllegalArgumentException("The segment starts after the end of this file.");
        int count=(int)Math.ceil((end-start)*fps/1000.0);
        if(count>900)throw new IllegalArgumentException("This segment needs more than 900 frames. Choose a shorter segment or a lower frame rate.");
        return Math.max(1,count);
    }
    private static int number(String value,int fallback){try{return Math.max(1,Integer.parseInt(value));}catch(Exception e){return fallback;}}
}
