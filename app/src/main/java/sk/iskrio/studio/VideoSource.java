package sk.iskrio.studio;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Local fallback for containers whose duration Android's metadata retriever cannot read. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class VideoSource implements AutoCloseable {
    final Uri uri;
    final long durationMs;
    final double frameRate;
    final boolean repaired;
    private final File temporary;
    private VideoSource(Uri uri,long durationMs,double frameRate,File temporary){this.uri=uri;this.durationMs=durationMs;this.frameRate=frameRate;this.temporary=temporary;repaired=temporary!=null;}
    @Override public void close(){if(temporary!=null)temporary.delete();}

    static VideoSource open(Context context,Uri uri,IntConsumer progress)throws Exception {
        requireLocal(uri);progress.accept(0);
        MediaMetadataRetriever metadata=new MediaMetadataRetriever();
        long duration=0;double rate=30;
        try{
            metadata.setDataSource(context,uri);
            duration=positiveLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            rate=positiveDouble(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE),30);
            if(Build.VERSION.SDK_INT>=28){long count=positiveLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT));if(duration>0&&count>0)rate=count*1000.0/duration;}
        }catch(RuntimeException ignored){/* MediaExtractor provides an independent fallback below. */}
        finally{try{metadata.release();}catch(Exception ignored){}}
        if(duration>0)return new VideoSource(uri,duration,rate,null);
        return repair(context,uri,progress);
    }

    private static VideoSource repair(Context context,Uri source,IntConsumer progress)throws Exception {
        androidx.media3.inspector.MediaExtractorCompat extractor=new androidx.media3.inspector.MediaExtractorCompat(context);MediaMuxer muxer=null;File file=null;boolean complete=false;
        final long deadline=System.nanoTime()+60000000000L;
        try{
            extractor.setDataSource(context,source,null);
            int track=-1;MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat candidate=extractor.getTrackFormat(i);String mime=candidate.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("video/")){track=i;format=candidate;break;}}
            if(track<0||format==null)throw new IllegalArgumentException("This file does not contain a playable video track.");
            extractor.selectTrack(track);
            long declared=longValue(format,MediaFormat.KEY_DURATION,0);double rate=numberValue(format,MediaFormat.KEY_FRAME_RATE,30);
            VideoTimeline timeline=new VideoTimeline();
            do{
                check(deadline,progress);
                long time=extractor.getSampleTime();if(time<0)break;
                if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw new IllegalArgumentException("Encrypted videos cannot be imported.");
                timeline.add(time);
            }while(extractor.advance());
            long durationUs=timeline.durationUs(declared,rate);
            File folder=new File(context.getCacheDir(),"video-repair");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Could not create video import storage.");
            String mime=format.getString(MediaFormat.KEY_MIME);
            boolean webm="video/x-vnd.on2.vp8".equals(mime)||"video/x-vnd.on2.vp9".equals(mime);
            file=new File(folder,UUID.randomUUID()+(webm?".webm":".mp4"));
            muxer=new MediaMuxer(file.getAbsolutePath(),webm?MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM:MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int rotation=(int)longValue(format,MediaFormat.KEY_ROTATION,0);if(!webm&&(rotation==90||rotation==180||rotation==270))muxer.setOrientationHint(rotation);
            int output=muxer.addTrack(format);muxer.start();
            extractor.seekTo(timeline.first,androidx.media3.inspector.MediaExtractorCompat.SEEK_TO_PREVIOUS_SYNC);
            ByteBuffer buffer=ByteBuffer.allocateDirect(8*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long total=0;int samples=0;
            do{
                check(deadline,progress);
                long time=extractor.getSampleTime();if(time<0)break;
                if(++samples>100000)throw new IllegalArgumentException("This video has too many samples to import safely.");
                if(Build.VERSION.SDK_INT>=28&&extractor.getSampleSize()>buffer.capacity())throw new IllegalArgumentException("A video frame exceeds the safe import size.");
                buffer.clear();int size=extractor.readSampleData(buffer,0);if(size<0)break;
                total+=size;if(size>buffer.capacity()||total>40L*1024*1024)throw new IllegalArgumentException("This video exceeds the 40 MB import limit.");
                int flags=(extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0;
                info.set(0,size,Math.max(0,time-timeline.first),flags);muxer.writeSampleData(output,buffer,info);
            }while(extractor.advance());
            if(samples==0)throw new IOException("No video samples could be recovered.");
            if(!webm){buffer.clear();info.set(0,0,durationUs,MediaCodec.BUFFER_FLAG_END_OF_STREAM);muxer.writeSampleData(output,buffer,info);}
            muxer.stop();muxer.release();muxer=null;
            check(deadline,progress);complete=true;
            return new VideoSource(Uri.fromFile(file),Math.max(1,(durationUs+999)/1000),timeline.samples*1000000.0/durationUs,file);
        }finally{
            extractor.release();if(muxer!=null)try{muxer.release();}catch(Exception ignored){}
            if(!complete&&file!=null)file.delete();
        }
    }
    private static void check(long deadline,IntConsumer progress){progress.accept(0);if(System.nanoTime()>deadline)throw new IllegalArgumentException("Video import timed out. Try a smaller file.");}
    static void requireLocal(Uri uri){String scheme=uri.getScheme();if(!"file".equals(scheme)&&!"content".equals(scheme))throw new IllegalArgumentException("Download the link first or choose a local media file.");}
    static long positiveLong(String value){try{return Math.max(0,Long.parseLong(value));}catch(Exception ignored){return 0;}}
    private static double positiveDouble(String value,double fallback){try{double number=Double.parseDouble(value);return Double.isFinite(number)&&number>0?number:fallback;}catch(Exception ignored){return fallback;}}
    private static long longValue(MediaFormat format,String key,long fallback){try{return format.getLong(key);}catch(Exception ignored){try{return format.getInteger(key);}catch(Exception failure){return fallback;}}}
    private static double numberValue(MediaFormat format,String key,double fallback){try{return format.getInteger(key);}catch(Exception ignored){try{return format.getFloat(key);}catch(Exception failure){return fallback;}}}
    static void cleanup(Context context){File[] files=new File(context.getCacheDir(),"video-repair").listFiles();if(files!=null)for(File file:files)if(file.isFile()&&System.currentTimeMillis()-file.lastModified()>86400000L&&file.getName().matches("[0-9a-f-]{36}\\.(mp4|webm)"))file.delete();}
}
