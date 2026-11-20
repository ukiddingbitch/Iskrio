package sk.iskrio.studio;
import android.media.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

/** Copies indexed AAC packets only. MP4 metadata is read, video payload is skipped. */
final class AudioOnlyDownload {
 private static final class Range {final long offset;final int first;int count,size;Range(long offset,int first,int size){this.offset=offset;this.first=first;this.size=size;count=1;}}
 static long copyRanges(long fileSize,RemoteAudioSource.Reader reader,File output,IntConsumer progress,Runnable check)throws Exception {
  Mp4AudioMap map=Mp4AudioMap.read(fileSize,reader);List<Range> ranges=new ArrayList<>();long bytes=0;
  for(int i=0;i<map.sizes.length;i++){if(map.times[i]<0)continue;if(map.times[i]>=600000000L)break;int size=map.sizes[i];if(size<1||size>512*1024)throw new IllegalArgumentException("Unsupported audio packet size.");Range last=ranges.isEmpty()?null:ranges.get(ranges.size()-1);if(last!=null&&last.offset+last.size==map.offsets[i]&&last.size+size<=512*1024){last.size+=size;last.count++;}else ranges.add(new Range(map.offsets[i],i,size));bytes+=size;}
  if(ranges.isEmpty())throw new IllegalArgumentException("This source has no readable audio.");
  ExecutorService pool=Executors.newFixedThreadPool(4);Map<Integer,Future<byte[]>> waiting=new HashMap<>();MediaMuxer muxer=null;boolean started=false,complete=false;
  try{
   MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",map.sampleRate,map.channels);format.setByteBuffer("csd-0",ByteBuffer.wrap(map.config));muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);int track=muxer.addTrack(format);muxer.start();started=true;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long firstTime=map.times[ranges.get(0).first];
   for(int i=0;i<ranges.size();i++){check.run();for(int k=i;k<Math.min(ranges.size(),i+4);k++)if(!waiting.containsKey(k)){final Range r=ranges.get(k);waiting.put(k,pool.submit(()->reader.read(r.offset,r.size)));}Range r=ranges.get(i);byte[] data;try{data=waiting.remove(i).get();}catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw e;}if(data.length!=r.size)throw new IOException("Incomplete audio range.");ByteBuffer packet=ByteBuffer.wrap(data);int offset=0;for(int n=0;n<r.count;n++){check.run();int sample=r.first+n;info.set(offset,map.sizes[sample],map.times[sample]-firstTime,MediaCodec.BUFFER_FLAG_KEY_FRAME);muxer.writeSampleData(track,packet,info);offset+=map.sizes[sample];}progress.accept((i+1)*100/ranges.size());}
   muxer.stop();started=false;complete=true;return bytes;
  }finally{pool.shutdownNow();if(muxer!=null){if(started)try{muxer.stop();}catch(Exception ignored){}muxer.release();}if(!complete)output.delete();}
 }
 static boolean elementaryAudio(byte[] h){if(h.length<12)return false;String first=new String(h,0,4,java.nio.charset.StandardCharsets.US_ASCII);return first.startsWith("ID3")||first.equals("fLaC")||first.equals("RIFF")&&new String(h,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE")||(h[0]&255)==255&&(h[1]&224)==224;}
 static void copyElementary(long size,RemoteAudioSource.Reader reader,File output,IntConsumer progress,Runnable check)throws Exception {boolean complete=false;try(OutputStream out=new FileOutputStream(output)){for(long at=0;at<size;){check.run();int length=(int)Math.min(512*1024,size-at);byte[] bytes=reader.read(at,length);if(bytes.length!=length)throw new IOException("Incomplete audio data.");out.write(bytes);at+=length;progress.accept((int)(at*100/size));}complete=true;}finally{if(!complete)output.delete();}}
 private AudioOnlyDownload(){}
}
