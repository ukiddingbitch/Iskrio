package sk.iskrio.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class GifStructureTest {
 private byte[] gif(int width,int height,int count,int repeats)throws Exception {
  ByteArrayOutputStream out=new ByteArrayOutputStream();out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
  out.write(width&255);out.write(width>>8);out.write(height&255);out.write(height>>8);out.write(new byte[]{(byte)128,0,0,0,0,0,(byte)255,(byte)255,(byte)255});
  if(repeats>=0){out.write(new byte[]{33,(byte)255,11});out.write("NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII));out.write(new byte[]{3,1,(byte)repeats,(byte)(repeats>>8),0});}
  for(int i=0;i<count;i++)out.write(new byte[]{33,(byte)249,4,0,4,0,0,0,44,0,0,0,0,1,0,1,0,0,2,2,68,1,0});out.write(59);return out.toByteArray();
 }
 @Test public void countsOriginalFramesAndDimensions()throws Exception{GifStructure s=GifStructure.inspect(gif(96,64,3,0));assertEquals(96,s.width);assertEquals(64,s.height);assertEquals(3,s.frames);assertEquals(0,s.loopCount);}
 @Test public void preservesSingleAndFinitePlayback()throws Exception{assertEquals(1,GifStructure.inspect(gif(1,1,1,-1)).loopCount);assertEquals(3,GifStructure.inspect(gif(1,1,1,2)).loopCount);}
 @Test public void rejectsTruncatedInputBeforeNativeDecode()throws Exception{byte[] full=gif(10,10,2,0);for(int cut:new int[]{0,8,full.length-1})assertThrows(IllegalArgumentException.class,()->GifStructure.inspect(java.util.Arrays.copyOf(full,cut)));}
 @Test public void enforcesFrameAndPixelBudgets()throws Exception{assertEquals(900,GifStructure.inspect(gif(64,64,900,0)).frames);assertThrows(IllegalArgumentException.class,()->GifStructure.inspect(gif(64,64,901,0)));assertThrows(IllegalArgumentException.class,()->GifStructure.inspect(gif(2048,2048,123,0)));}
 @Test public void rejectsOversizedLogicalScreen()throws Exception{assertThrows(IllegalArgumentException.class,()->GifStructure.inspect(gif(4096,4096,1,0)));}
}
