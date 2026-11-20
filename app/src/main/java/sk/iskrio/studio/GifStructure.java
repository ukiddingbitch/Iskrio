package sk.iskrio.studio;

/** Bounded structural preflight before native GIF decoding. No pixel decompression. */
final class GifStructure {
    final int width,height,frames,loopCount;
    private final byte[] bytes;
    private int offset;
    private GifStructure(byte[] bytes){
        this.bytes=bytes;
        if(bytes.length<13||bytes.length>40*1024*1024)throw invalid();
        String signature=new String(bytes,0,6,java.nio.charset.StandardCharsets.US_ASCII);
        if(!signature.equals("GIF87a")&&!signature.equals("GIF89a"))throw invalid();
        offset=6;width=word();height=word();dimensions(width,height);
        int packed=next();skip(2);if((packed&128)!=0)skip(3*(2<<(packed&7)));
        int count=0,repeats=-1;
        while(true){
            int block=next();
            if(block==0x3b)break;
            if(block==0x21){
                int label=next();
                if(label==0xf9){if(next()!=4)throw invalid();skip(4);if(next()!=0)throw invalid();}
                else if(label==0xff){
                    int size=next();int start=offset;skip(size);
                    String app=new String(bytes,start,size,java.nio.charset.StandardCharsets.US_ASCII);
                    if((app.equals("NETSCAPE2.0")||app.equals("ANIMEXTS1.0"))&&peek()==3){next();if(next()!=1)throw invalid();repeats=word();}
                    subBlocks();
                }else subBlocks();
            }else if(block==0x2c){
                skip(4);int w=word(),h=word();dimensions(w,h);
                int flags=next();if((flags&128)!=0)skip(3*(2<<(flags&7)));
                int codeSize=next();if(codeSize<1||codeSize>8)throw invalid();subBlocks();
                if(++count>900||(long)width*height*count>512000000L)throw new IllegalArgumentException("This GIF exceeds the 900-frame or decoded-pixel limit. Choose a smaller GIF.");
            }else throw invalid();
        }
        if(count<1)throw invalid();frames=count;loopCount=repeats<0?1:repeats==0?0:repeats+1;
    }
    static GifStructure inspect(byte[] bytes){return new GifStructure(bytes);}
    private static void dimensions(int width,int height){if(width<1||height<1||width>2048||height>2048||(long)width*height>4194304)throw new IllegalArgumentException("This GIF is larger than 2048 pixels per side. Choose a smaller GIF.");}
    private int peek(){if(offset>=bytes.length)throw invalid();return bytes[offset]&255;}
    private int next(){int value=peek();offset++;return value;}
    private int word(){return next()|(next()<<8);}
    private void skip(int count){if(count<0||count>bytes.length-offset)throw invalid();offset+=count;}
    private void subBlocks(){int size;while((size=next())!=0)skip(size);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("This GIF is incomplete or unsupported. Try the original GIF file.");}
}
