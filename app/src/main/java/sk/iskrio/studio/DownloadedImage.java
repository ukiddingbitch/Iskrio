package sk.iskrio.studio;

import android.graphics.*;
import android.os.Build;
import android.util.Base64;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;

final class DownloadedImage {
    static String dataUrl(File file)throws IOException {
        if(file.length()>MediaLimits.IMAGE_BYTES)throw new IOException("The image exceeds 40 MB.");
        byte[] bytes=Files.readAllBytes(file.toPath());
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1||options.outHeight<1||options.outWidth>32768||options.outHeight>32768||(long)options.outWidth*options.outHeight>160000000)throw new IllegalArgumentException("This image is invalid or its dimensions are too large.");
        Bitmap bitmap;
        if(Build.VERSION.SDK_INT>=28){
            bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),(decoder,info,source)->{
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,2048f/Math.max(w,h));
                decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
            });
        }else{
            int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>2048)sample*=2;
            options.inJustDecodeBounds=false;options.inSampleSize=sample;
            bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        }
        if(bitmap==null)throw new IllegalArgumentException("This image cannot be decoded.");
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not prepare the image.");
            return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        }finally{bitmap.recycle();}
    }
}
