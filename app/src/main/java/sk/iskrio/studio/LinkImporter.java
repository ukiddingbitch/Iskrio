package sk.iskrio.studio;

import android.content.Context;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;
import java.util.regex.*;

/** Explicit, cancellable online import. No HTML, scripts, cookies or login. */
final class LinkImporter implements AutoCloseable {
    static final long MAX_MEDIA=MediaLimits.VIDEO_BYTES;
    private static final Pattern POST=Pattern.compile("^/(?:[A-Za-z0-9_]+/status|i/web/status)/([0-9]{5,25})(?:/.*)?$");
    private final OkHttpClient client;
    private final BooleanSupplier internetAccess;
    private volatile Call currentCall;
    private volatile boolean cancelled,closed;private volatile long audioReceived,audioSourceSize;
    private long deadline;
    LinkImporter(){this(()->false);}
    LinkImporter(BooleanSupplier internetAccess){
        this.internetAccess=internetAccess;
        client=new OkHttpClient.Builder().dns(hostname->{check();java.util.List<java.net.InetAddress> addresses=SafeUrlPolicy.resolve(hostname);check();return addresses;}).proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(600,TimeUnit.SECONDS).build();
    }
    void cancel(){cancelled=true;Call call=currentCall;if(call!=null)call.cancel();}
    boolean isCancelled(){return cancelled;}
    long audioBytesReceived(){return audioReceived;}
    long audioSourceSize(){return audioSourceSize;}
    int connectionCount(){return client.connectionPool().connectionCount();}
    @Override public synchronized void close(){if(closed)return;closed=true;Call call=currentCall;if(call!=null)call.cancel();client.dispatcher().cancelAll();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();currentCall=null;}
    private void begin(){begin(90);}
    private void begin(int seconds){if(closed||cancelled)throw new CancellationException();if(!internetAccess.getAsBoolean())throw new IllegalArgumentException("Internet access is off.");deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);}
    private void check(){if(cancelled||closed||!internetAccess.getAsBoolean())throw new CancellationException();if(System.nanoTime()>deadline)throw new IllegalArgumentException("The download timed out. Try again.");}
    private Response get(HttpUrl initial,String accept)throws IOException {return get(initial,accept,false);}
    private Response get(HttpUrl initial,String accept,boolean stopBeforeX)throws IOException {return get(initial,accept,stopBeforeX,null);}
    private Response get(HttpUrl initial,String accept,boolean stopBeforeX,String range)throws IOException {
        HttpUrl url=SafeUrlPolicy.parse(initial.toString());Set<String> visited=new HashSet<>();
        for(int redirects=0;redirects<=5;redirects++){
            check();if(!visited.add(url.toString()))throw new IOException("Redirect loop.");
            if(stopBeforeX&&SafeUrlPolicy.isX(url))return new Response.Builder().request(new Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1).code(200).message("Resolved").body(ResponseBody.create(new byte[0],null)).build();
            Request.Builder requestBuilder=new Request.Builder().url(url).get();if(range!=null)requestBuilder.header("Range",range).header("Accept-Encoding","identity");Request request=requestBuilder.header("Accept",accept).header("User-Agent","Iskrio/1.2 (Android)").header("Accept-Language","en").build();
            Call call=client.newCall(request);currentCall=call;
            call.timeout().timeout(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            check();Response response=call.execute();try{check();}catch(RuntimeException e){response.close();throw e;}
            int status=response.code();
            if(status==301||status==302||status==303||status==307||status==308){
                String location=response.header("Location");HttpUrl next=location==null?null:url.resolve(location);response.close();
                if(next==null)throw new IOException("Invalid redirect.");url=SafeUrlPolicy.parse(next.toString());continue;
            }
            if(!response.isSuccessful()){
                response.close();
                if(status==401||status==403)throw new IllegalArgumentException("This link is private, blocked or requires a login.");
                if(status==404||status==410)throw new IllegalArgumentException("This media is no longer available.");
                if(status==429)throw new IllegalArgumentException("This service is rate limiting imports. Try again later.");
                throw new IOException("HTTP "+status);
            }
            return response;
        }
        throw new IllegalArgumentException("This link redirects too many times.");
    }
    private byte[] smallBody(Response response,int limit)throws IOException {
        ResponseBody body=response.body();if(body==null)throw new IOException("Empty response.");
        if(body.contentLength()>limit)throw new IllegalArgumentException("The response is too large.");
        try(InputStream in=body.byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){check();if(out.size()+n>limit)throw new IllegalArgumentException("The response is too large.");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    JSONObject resolve(String source,String token)throws Exception {
        begin();
        HttpUrl url=SafeUrlPolicy.parse(source);
        if(url.host().equals("t.co"))try(Response response=get(url,"*/*",true)){url=SafeUrlPolicy.parse(response.request().url().toString());}
        if(!SafeUrlPolicy.isX(url)){
            try(Response response=get(url,"text/html, application/xhtml+xml, image/*, video/*, application/octet-stream;q=0.8",true)){
                url=SafeUrlPolicy.parse(response.request().url().toString());
                if(!SafeUrlPolicy.isX(url)){
                    String type=response.header("Content-Type","").toLowerCase(Locale.ROOT);
                    String prefix=new String(response.peekBody(512).bytes(),StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                    boolean html=type.contains("text/html")||type.contains("application/xhtml+xml")||prefix.startsWith("<!doctype html")||prefix.startsWith("<html");
                    if(!html){check();return new JSONObject().put("items",new JSONArray().put(candidate(url.toString(),"Media","unknown")));}
                    java.nio.charset.Charset charset=response.body()!=null&&response.body().contentType()!=null?response.body().contentType().charset(StandardCharsets.UTF_8):StandardCharsets.UTF_8;
                    java.util.List<PageMediaResolver.Media> media=PageMediaResolver.resolve(new String(smallBody(response,2*1024*1024),charset),url.toString());check();
                    if(media.isEmpty())throw new IllegalArgumentException("This page does not expose a downloadable GIF, video or image. Try a direct media link.");
                    JSONArray items=new JSONArray();int index=0;for(PageMediaResolver.Media item:media){String label=item.kind.equals("gif")?"GIF":item.kind.equals("video")?"Video":"Image";if(media.size()>1)label+=" "+(++index);items.put(candidate(item.url,label,item.kind));}
                    return new JSONObject().put("items",items);
                }
            }
        }
        Matcher post=POST.matcher(url.encodedPath());
        if(!post.matches())throw new IllegalArgumentException("Paste a link to a public X post, not a profile or search page.");
        String id=post.group(1);
        if(token==null||token.isEmpty())return new JSONObject().put("postId",id);
        if(!token.matches("[a-z0-9]{1,64}"))throw new IllegalArgumentException("Invalid post identifier.");
        HttpUrl metadata=SafeUrlPolicy.parse("https://cdn.syndication.twimg.com/tweet-result").newBuilder().addQueryParameter("id",id).addQueryParameter("lang","en").addQueryParameter("token",token).build();
        JSONObject tweet;
        try(Response response=get(metadata,"application/json")){
            String type=response.header("Content-Type","");if(!type.toLowerCase(Locale.ROOT).contains("application/json"))throw new IllegalArgumentException("X could not provide this public post. Try a direct media link.");
            tweet=new JSONObject(new String(smallBody(response,2*1024*1024),StandardCharsets.UTF_8));
        }
        if(tweet.length()==0||tweet.optString("__typename").equals("TweetTombstone"))throw new IllegalArgumentException("This X post is unavailable or requires a login.");
        JSONArray items=mediaFromTweet(tweet);
        if(items.length()==0&&tweet.optJSONObject("quoted_tweet")!=null)items=mediaFromTweet(tweet.getJSONObject("quoted_tweet"));
        if(items.length()==0)throw new IllegalArgumentException("No downloadable media was found in this public X post.");
        return new JSONObject().put("items",items);
    }
    static JSONObject candidate(String url,String label,String kind)throws JSONException{return new JSONObject().put("url",url).put("label",label).put("kind",kind);}
    private static void add(JSONArray list,Set<String> seen,String source,String label,String kind)throws JSONException {
        try{HttpUrl url=SafeUrlPolicy.parse(source);if(!url.host().equals("twimg.com")&&!url.host().endsWith(".twimg.com"))return;
            if(seen.add(url.toString())&&list.length()<16)list.put(candidate(url.toString(),label,kind));
        }catch(IllegalArgumentException ignored){}
    }
    static JSONArray mediaFromTweet(JSONObject tweet)throws JSONException {
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();JSONArray media=tweet.optJSONArray("mediaDetails");
        if(media!=null)for(int i=0;i<media.length()&&result.length()<16;i++){
            JSONObject item=media.optJSONObject(i);if(item==null)continue;String type=item.optString("type");
            if(type.equals("photo"))add(result,seen,item.optString("media_url_https"),"Photo "+(i+1),"image");
            else if(type.equals("video")||type.equals("animated_gif")){
                JSONObject info=item.optJSONObject("video_info");JSONArray variants=info==null?null:info.optJSONArray("variants");
                if(variants!=null){List<JSONObject> mp4=new ArrayList<>();for(int v=0;v<variants.length();v++){JSONObject variant=variants.optJSONObject(v);if(variant!=null&&variant.optString("content_type").equals("video/mp4"))mp4.add(variant);}
                    mp4.sort((a,b)->Long.compare(b.optLong("bitrate"),a.optLong("bitrate")));
                    for(int v=0;v<Math.min(4,mp4.size());v++){String src=mp4.get(v).optString("url"),label=type.equals("animated_gif")?"GIF":"Video";Matcher size=Pattern.compile("/([0-9]{2,5}x[0-9]{2,5})/").matcher(src);if(size.find())label+=" · "+size.group(1);else if(mp4.size()>1)label+=" · quality "+(v+1);add(result,seen,src,label,"video");if(result.length()>0)result.getJSONObject(result.length()-1).put("group","x-"+i);}
                }
            }
        }
        if(result.length()==0){JSONArray photos=tweet.optJSONArray("photos");if(photos!=null)for(int i=0;i<photos.length();i++){JSONObject photo=photos.optJSONObject(i);if(photo!=null)add(result,seen,photo.optString("url"),"Photo "+(i+1),"image");}
            JSONObject video=tweet.optJSONObject("video");JSONArray variants=video==null?null:video.optJSONArray("variants");if(variants!=null)for(int i=0;i<variants.length();i++){JSONObject v=variants.optJSONObject(i);if(v!=null&&v.optString("type").equals("video/mp4"))add(result,seen,v.optString("src"),"Video","video");}
        }
        return result;
    }
    static long rangeSize(String header,long offset,int requested){
        if(header==null)throw new IllegalArgumentException("This server does not support audio-only range downloads.");
        Matcher match=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(header);if(!match.matches())throw new IllegalArgumentException("Invalid audio download range.");
        try{long first=Long.parseLong(match.group(1)),last=Long.parseLong(match.group(2)),total=Long.parseLong(match.group(3));if(total<1||total>MediaLimits.VIDEO_BYTES||first!=offset||last!=Math.min(total-1,offset+requested-1))throw new IllegalArgumentException("Invalid audio download range.");return total;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid audio download range.");}
    }
    File downloadAudio(Context context,String source,IntConsumer progress)throws Exception {
        begin(900);HttpUrl url=SafeUrlPolicy.parse(source);final long[] size={-1},received={0};final String[] tag={null};
        RemoteAudioSource.Reader reader=(offset,length)->{check();try(Response response=get(url,"audio/*, video/mp4, application/octet-stream",false,"bytes="+offset+"-"+(offset+length-1))){
            if(response.code()!=206)throw new IllegalArgumentException("This server cannot download audio separately. No full video was downloaded.");long total=rangeSize(response.header("Content-Range"),offset,length);synchronized(size){if(size[0]<0)size[0]=total;else if(size[0]!=total)throw new IllegalArgumentException("This media changed during download.");String current=response.header("ETag");if(tag[0]==null)tag[0]=current;else if(current!=null&&!tag[0].equals(current))throw new IllegalArgumentException("This media changed during download.");}
            byte[] data=smallBody(response,length);if(data.length!=(int)Math.min(length,total-offset))throw new IOException("Incomplete audio range.");synchronized(size){received[0]+=data.length;audioReceived=received[0];audioSourceSize=total;if(received[0]>128L*1024*1024)throw new IllegalArgumentException("This source requires too much data for audio-only import. No full video was saved.");}return data;
        }};
        byte[] initial=reader.read(0,16);File folder=new File(context.getCacheDir(),"link-imports");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Could not create temporary storage.");File output=new File(folder,UUID.randomUUID()+".audio");boolean complete=false;
        try{if(AudioOnlyDownload.elementaryAudio(initial))AudioOnlyDownload.copyElementary(size[0],reader,output,progress,this::check);else AudioOnlyDownload.copyRanges(size[0],reader,output,progress,this::check);check();complete=true;return output;}finally{currentCall=null;if(!complete)output.delete();}
    }
    File download(Context context,String source,IntConsumer progress)throws Exception {
        begin(900);
        File folder=new File(context.getCacheDir(),"link-imports");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Could not create temporary storage.");
        File file=new File(folder,UUID.randomUUID()+".media");boolean complete=false;
        try(Response response=get(SafeUrlPolicy.parse(source),"image/*, video/mp4, video/webm, application/octet-stream;q=0.8")){
            ResponseBody body=response.body();if(body==null)throw new IOException("The response is empty.");
            byte[] head=response.peekBody(32).bytes();String detected=mediaType(head,head.length);long limit=detected.startsWith("video/")?MediaLimits.VIDEO_BYTES:MediaLimits.IMAGE_BYTES;
            long length=body.contentLength();if(length>limit)throw new IllegalArgumentException(detected.startsWith("video/")?"This video exceeds the 1 GB import limit.":"This image/GIF exceeds the 40 MB import limit.");
            if(context.getCacheDir().getUsableSpace()<(length>0?length:64L*1024*1024)+32L*1024*1024)throw new IllegalArgumentException("Free more storage before downloading this media.");
            String content=response.header("Content-Type","").toLowerCase(Locale.ROOT);
            if(content.contains("text/html")||content.contains("image/svg")||content.contains("javascript"))throw new IllegalArgumentException("This is a webpage or unsupported format. Paste a share-page link or a direct media link.");
            try(InputStream in=body.byteStream();OutputStream out=new FileOutputStream(file)){
                byte[] buffer=new byte[16384];long total=0;int n,last=-2;
                while((n=in.read(buffer))!=-1){check();total+=n;if(total>limit)throw new IllegalArgumentException(detected.startsWith("video/")?"This video exceeds the 1 GB import limit.":"This image/GIF exceeds the 40 MB import limit.");out.write(buffer,0,n);int percent=length>0?(int)Math.min(100,total*100/length):-1;if(percent!=last){last=percent;progress.accept(percent);}}
                if(total==0)throw new IOException("The file is empty.");
                if(length>=0&&total!=length)throw new IOException("The download is incomplete.");
            }
            mediaType(file);check();complete=true;return file;
        }finally{currentCall=null;if(!complete)file.delete();}
    }
    static String mediaType(File file)throws IOException {
        byte[] h=new byte[32];int n;try(InputStream in=new FileInputStream(file)){n=in.read(h);}return mediaType(h,n);
    }
    static String mediaType(byte[] h,int n)throws IOException {
        if(n>=8&&(h[0]&255)==137&&h[1]==80&&h[2]==78&&h[3]==71&&h[4]==13&&h[5]==10&&h[6]==26&&h[7]==10)return "image/png";
        if(n>=3&&(h[0]&255)==255&&(h[1]&255)==216&&(h[2]&255)==255)return "image/jpeg";
        String ascii=new String(h,0,Math.max(0,n),StandardCharsets.US_ASCII);
        if(n>=10&&(ascii.startsWith("GIF87a")||ascii.startsWith("GIF89a"))){int w=(h[6]&255)|((h[7]&255)<<8),height=(h[8]&255)|((h[9]&255)<<8);if(w<1||height<1||(long)w*height>20000000)throw new IllegalArgumentException("The GIF dimensions are too large.");return "image/gif";}
        if(n>=12&&ascii.startsWith("RIFF")&&ascii.substring(8,12).equals("WEBP"))return "image/webp";
        if(n>=12&&ascii.substring(4,8).equals("ftyp"))return "video/mp4";
        if(n>=4&&(h[0]&255)==26&&(h[1]&255)==69&&(h[2]&255)==223&&(h[3]&255)==163)return "video/webm";
        throw new IllegalArgumentException("The downloaded file is not a supported image, GIF or video.");
    }
    static void cleanup(Context context){File folder=new File(context.getCacheDir(),"link-imports");File[] files=folder.listFiles();if(files!=null)for(File file:files)if(file.isFile()&&System.currentTimeMillis()-file.lastModified()>86400000L&&file.getName().matches("[0-9a-f-]{36}\\.media"))file.delete();}
}
