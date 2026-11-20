package sk.iskrio.studio;
import android.content.Context;
import com.arthenica.ffmpegkit.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Small local amplitude overview. Peak markers are editable hints, not speech/AI analysis. */
final class StudioWaveform {
 static void add(Context context,File source,JSONObject info){File pcm=null;try{
  pcm=File.createTempFile("wave-",".pcm",context.getCacheDir());FFmpegSession session=FFmpegKit.executeWithArguments(new String[]{"-hide_banner","-y","-nostdin","-loglevel","error","-protocol_whitelist","file,pipe","-i",source.getAbsolutePath(),"-vn","-t","600","-ac","1","-ar","8000","-f","s16le",pcm.getAbsolutePath()});if(!ReturnCode.isSuccess(session.getReturnCode()))return;
  List<Double> energy=new ArrayList<>();try(InputStream in=new BufferedInputStream(new FileInputStream(pcm))){int low,high,count=0;double sum=0;while((low=in.read())!=-1&&(high=in.read())!=-1){short sample=(short)(low|(high<<8));sum+=(double)sample*sample;count++;if(count==400){energy.add(Math.sqrt(sum/count)/32768);sum=0;count=0;}}if(count>0)energy.add(Math.sqrt(sum/count)/32768);}
  double maximum=energy.stream().mapToDouble(Double::doubleValue).max().orElse(1);JSONArray waveform=new JSONArray(),peaks=new JSONArray();for(int i=0;i<120;i++){int from=i*energy.size()/120,to=Math.max(from+1,(i+1)*energy.size()/120);double value=0;for(int j=from;j<Math.min(to,energy.size());j++)value=Math.max(value,energy.get(j));waveform.put(maximum>0?Math.round(value/maximum*1000)/1000.0:0);}
  int previous=-10;for(int i=1;i<energy.size()-1;i++){double average=0;int count=0;for(int j=Math.max(0,i-8);j<i;j++){average+=energy.get(j);count++;}if(i-previous>=4&&energy.get(i)>average/Math.max(1,count)*1.6&&energy.get(i)>.08&&energy.get(i)>=energy.get(i-1)&&energy.get(i)>=energy.get(i+1)){peaks.put(i*50);previous=i;}}
  info.put("waveform",waveform).put("peaksMs",peaks);
 }catch(Exception ignored){}finally{if(pcm!=null)pcm.delete();}}
}
