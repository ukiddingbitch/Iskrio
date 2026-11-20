package sk.iskrio.studio;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.function.IntConsumer;
import pl.droidsonroids.gif.GifDrawable;

/** Imports complete GIF frames, original dimensions, alpha, disposal and per-frame timing. */
final class OriginalGif {
    static JSONObject extract(Context context,Uri uri,IntConsumer progress)throws Exception {
        byte[] data;
        try(InputStream in=context.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IOException("Could not open GIF.");
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))!=-1){progress.accept(0);if(out.size()+n>40*1024*1024)throw new IllegalArgumentException("The GIF exceeds 40 MB.");out.write(buffer,0,n);}data=out.toByteArray();
        }
        GifStructure structure=GifStructure.inspect(data);
        progress.accept(0);
        FrameStore store=new FrameStore(context);
        GifDrawable decoder=null;
        try{
            decoder=new GifDrawable(data);decoder.stop();
            int count=decoder.getNumberOfFrames();
            if(count!=structure.frames)throw new IllegalArgumentException("This GIF could not be decoded completely.");
            store.keepOriginalGif(data);
            JSONArray frames=new JSONArray(),delays=new JSONArray();long duration=0;
            for(int i=0;i<count;i++){
                progress.accept(i*100/count);
                int delay=decoder.getFrameDuration(i);if(delay<=0)delay=100;
                if(delay>655350||duration+delay>3600000)throw new IllegalArgumentException("This GIF exceeds the supported playback duration.");
                Bitmap frame=decoder.seekToFrameAndGet(i);
                try{frames.put(store.add(frame,i,true));}finally{frame.recycle();}
                delays.put(delay);duration+=delay;
            }
            progress.accept(100);
            return new JSONObject().put("frames",frames).put("frameDurations",delays).put("durationMs",duration)
                .put("fps",Math.max(1,Math.min(50,Math.round(count*1000f/Math.max(1,duration)))))
                .put("loopCount",structure.loopCount).put("preserveSize",true).put("transparent",true).put("originalTiming",true).put("originalGifFrame",frames.getString(0));
        }catch(Throwable error){store.discard();throw error;}finally{if(decoder!=null)decoder.recycle();}
    }
}
