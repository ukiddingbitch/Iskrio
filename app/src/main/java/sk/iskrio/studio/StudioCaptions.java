package sk.iskrio.studio;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Plain manual subtitles, rendered locally with the bundled font. No recognition or cloud API. */
final class StudioCaptions {
 private static String stamp(double ms){long cs=Math.round(ms/10);return String.format(Locale.US,"%d:%02d:%02d.%02d",cs/360000,(cs/6000)%60,(cs/100)%60,cs%100);}
 static File create(Context context,File directory,JSONObject project,double duration)throws Exception {
  JSONArray rows=project.optJSONArray("captions");if(rows==null||rows.length()==0)return null;if(rows.length()>200)throw new IllegalArgumentException("Use up to 200 subtitles.");
  int width=project.getInt("w"),height=project.getInt("h");File fonts=new File(directory,"fonts");if(!fonts.mkdirs()&&!fonts.isDirectory())throw new IOException();File font=new File(fonts,"Manrope.ttf");try(InputStream in=context.getAssets().open("fonts/Manrope.ttf");OutputStream out=new FileOutputStream(font)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
  File file=new File(directory,"captions.ass");try(Writer out=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){
   out.write("[Script Info]\nScriptType: v4.00+\nPlayResX: "+width+"\nPlayResY: "+height+"\nWrapStyle: 0\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
   out.write("Style: Default,Manrope,"+Math.max(14,width*.045)+",&H00FFFFFF,&H00FFFFFF,&H00000000,&H60000000,-1,0,0,0,100,100,0,0,1,"+Math.max(1,width*.003)+",0,2,"+Math.round(width*.05)+","+Math.round(width*.05)+","+Math.round(height*.08)+",1\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
   for(int i=0;i<rows.length();i++){JSONObject c=rows.getJSONObject(i);double start=c.getDouble("startMs"),end=c.getDouble("endMs");String text=c.getString("text");if(!Double.isFinite(start)||!Double.isFinite(end)||start<0||end<=start||end>600000||text.length()>2000)throw new IllegalArgumentException("Invalid subtitle timing or text.");if(start>=duration*1000)continue;text=text.replace("\\","\\\\").replace("{","\\{").replace("}","\\}").replace("\r","").replace("\n","\\N");out.write("Dialogue: 0,"+stamp(start)+","+stamp(Math.min(end,duration*1000))+",Default,,0,0,0,,"+text+"\n");}
  }return file;
 }
}
