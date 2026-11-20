package sk.iskrio.studio;

import org.json.JSONObject;
import java.util.Locale;

/** Generates bounded filter graphs from numeric editor controls, never user expressions. */
final class StudioLooks {
 private static String num(double v){return String.format(Locale.US,"%.6f",v);}
 private static double value(JSONObject o,String k,double fallback,double min,double max){double v=o.optDouble(k,fallback);if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("Invalid "+k+" setting.");return v;}
 static String apply(String graph,JSONObject c,int width,int height,int fps,double duration){
  double exposure=value(c,"exposure",0,-1,1),temperature=value(c,"temperature",0,-1,1);
  if(exposure!=0)graph+=",exposure=exposure="+num(exposure);
  if(temperature!=0)graph+=",colorbalance=rs="+num(temperature*.16)+":bs="+num(-temperature*.16);
  String creative=c.optString("creative","none");
  switch(creative){
   case "none":break;
   case "grain":graph+=",noise=alls=12:allf=t+u";break;
   case "vhs":graph+=",chromashift=cbh=3:crh=-3,noise=alls=7:allf=t,drawgrid=w=iw:h=4:t=1:c=black@0.12";break;
   case "glow":graph+=",split[glowbase][glowlight];[glowlight]gblur=sigma=8,eq=brightness=.08[glow];[glowbase][glow]blend=all_mode=screen:all_opacity=.3";break;
   case "wave":graph+=",geq=lum='lum(X+5*sin(Y/25+T*3),Y)':cb='cb(X,Y)':cr='cr(X,Y)'";break;
   default:throw new IllegalArgumentException("Invalid creative effect.");
  }
  String motion=c.optString("cameraMotion","none");
  if(!motion.equals("none")){String z;switch(motion){case "in":z="1+.2*on/"+num(Math.max(1,duration*fps));break;case "out":z="1.2-.2*on/"+num(Math.max(1,duration*fps));break;case "pan":z="1.15";break;default:throw new IllegalArgumentException("Invalid camera motion.");}String x=motion.equals("pan")?"(iw-iw/zoom)*on/"+num(Math.max(1,duration*fps)):"iw/2-iw/zoom/2";graph+=",zoompan=z='"+z+"':x='"+x+"':y='ih/2-ih/zoom/2':d=1:s="+width+"x"+height+":fps="+fps;}
  JSONObject r=c.optJSONObject("region");if(r==null)return graph;
  double x=value(r,"x",.25,0,.95),y=value(r,"y",.25,0,.95),w=value(r,"w",.5,.05,1),h=value(r,"h",.5,.05,1),strength=value(r,"strength",15,2,30);if(x+w>1.001||y+h>1.001)throw new IllegalArgumentException("Keep the effect region inside the frame.");
  int rw=Math.max(2,(int)(w*width/2)*2),rh=Math.max(2,(int)(h*height/2)*2),rx=Math.min(width-rw,(int)(x*width/2)*2),ry=Math.min(height-rh,(int)(y*height/2)*2);String effect;
  if(r.optString("mode").equals("blur"))effect="gblur=sigma="+num(strength*width/720.0);
  else if(r.optString("mode").equals("pixelate"))effect="scale="+Math.max(2,(int)(width/strength))+":"+Math.max(2,(int)(height/strength))+":flags=neighbor,scale="+width+":"+height+":flags=neighbor";
  else throw new IllegalArgumentException("Invalid area effect.");
  return graph+",split[regionbase][regionwork];[regionwork]"+effect+",crop="+rw+":"+rh+":"+rx+":"+ry+"[regioncrop];[regionbase][regioncrop]overlay="+rx+":"+ry+":shortest=1";
 }
}
