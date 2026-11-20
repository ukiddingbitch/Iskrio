package sk.iskrio.studio;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Base64;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;

/** Local editor; network access is confined to explicit, short-lived import sessions. */
public final class MainActivity extends Activity {
    private WebView web;
    private final java.util.concurrent.atomic.AtomicBoolean cancelExtraction=new java.util.concurrent.atomic.AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String importKind="image", pendingName, pendingMime;
    private volatile byte[] pendingExport;
    private volatile boolean importBusy,extractionRunning;
    private volatile File pendingLinkFile;
    private volatile File pendingExportFile,readyVideoFile;
    private String readyVideoToken,readyExportName="Iskrio-video.mp4",readyExportMime="video/mp4";
    private final java.util.concurrent.atomic.AtomicBoolean cancelArchive=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean videoExportBusy;
    private VideoExporter videoExporter;
    private final java.util.concurrent.atomic.AtomicInteger trackFrameGeneration=new java.util.concurrent.atomic.AtomicInteger();
    private volatile StudioEngine studioEngine;private final java.util.concurrent.atomic.AtomicBoolean studioCancelled=new java.util.concurrent.atomic.AtomicBoolean();private String studioPreviewSource;
    private android.media.MediaRecorder recorder;private File recordingFile;private long recordingStarted;private final android.os.Handler recordingHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private VideoExporter.Listener videoExportListener;
    private final java.util.concurrent.atomic.AtomicBoolean internetAccess=new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicReference<LinkImporter> linkImport=new java.util.concurrent.atomic.AtomicReference<>();
    private static final int PICK=41, SAVE=42, MAX_INPUT=40*1024*1024;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        worker.execute(()->{FrameStore.cleanupUnused(this);LinkImporter.cleanup(this);VideoSource.cleanup(this);VideoStore.cleanup(this);VideoExporter.cleanup(this);StudioMedia.cleanup(this);StudioPreviewCache.cleanup(this);StudioTrackFrames.cleanup(this,false);});
        web=new WebView(this); web.setBackgroundColor(Color.rgb(20,27,35));
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setBlockNetworkLoads(true);
        s.setMediaPlaybackRequiresUserGesture(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r){
                Uri u=r.getUrl();
                if("https".equals(u.getScheme()) && "app.iskrio.local".equals(u.getHost())) {
                    String p=u.getPath();
                    if(p==null || p.contains("..")) return empty();
                    try { if(p.startsWith("/videos/"))return VideoStore.response(MainActivity.this,r);
                        if(p.startsWith("/track-frames/"))return StudioTrackFrames.response(MainActivity.this,r);
                        if(p.startsWith("/proxy/"))return StudioPreviewCache.response(MainActivity.this,r);
                        if(p.startsWith("/audio/"))return StudioMedia.response(MainActivity.this,r);
                        if(p.startsWith("/media/"))return new WebResourceResponse(p.endsWith(".png")?"image/png":"image/jpeg",null,new FileInputStream(FrameStore.resolve(MainActivity.this,u)));
                        String f=p.equals("/")?"index.html":p.substring(1);
                        String mime=f.endsWith(".html")?"text/html":f.endsWith(".css")?"text/css":f.endsWith(".js")?"application/javascript":f.endsWith(".ttf")?"font/ttf":"application/octet-stream";
                        return new WebResourceResponse(mime,"UTF-8",getAssets().open(f));
                    } catch(IOException e){return empty();}
                }
                return empty();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
        });
        web.addJavascriptInterface(new Bridge(),"Android");
        setContentView(web); web.loadUrl("https://app.iskrio.local/index.html");
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->web.evaluateJavascript("window.handleBack()",null));
    }
    private WebResourceResponse empty(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private void js(String name,Object value){String json=value instanceof JSONObject || value instanceof JSONArray?value.toString():JSONObject.quote(String.valueOf(value));runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window."+name+"("+json+")",null);});}
    private void error(String msg){js("nativeError",msg);}
    private void publishInternet(String reason){try{js("nativeInternetAccess",new JSONObject().put("enabled",internetAccess.get()).put("reason",reason));}catch(JSONException ignored){}}
    private synchronized void setInternetAccess(boolean enabled){
        internetAccess.set(enabled);
        if(!enabled){LinkImporter task=linkImport.get();if(task!=null){task.cancel();task.close();}}
        publishInternet(enabled?"enabled":"disabled");
    }
    private synchronized void finishDownloadNetwork(LinkImporter task){
        task.close();if(linkImport.get()==task){internetAccess.set(false);publishInternet("complete");}
    }
    private synchronized LinkImporter beginLink(){
        if(!internetAccess.get()){error("Internet access is off. Turn it on to import a link.");return null;}
        if(importBusy||pendingExport!=null||pendingExportFile!=null||extractionRunning||videoExportBusy){error("Finish the current import or export first.");return null;}
        LinkImporter task=new LinkImporter(internetAccess::get),previous=linkImport.getAndSet(task);if(previous!=null){previous.cancel();previous.close();}
        releaseLinkFile();return task;
    }
    private void releaseLinkFile(){File file=pendingLinkFile;pendingLinkFile=null;if(file!=null)file.delete();}
    private void releaseLinkSource(String uri){File file=pendingLinkFile;if(file!=null&&Uri.fromFile(file).toString().equals(uri))releaseLinkFile();}
    private synchronized void linkFailure(LinkImporter task,Throwable failure){
        task.close();if(!linkImport.compareAndSet(task,null))return;internetAccess.set(false);publishInternet("failed");
        if(task.isCancelled()||failure instanceof CancellationException){js("nativeCancelled","link");return;}
        if(failure instanceof IllegalArgumentException){error(failure.getMessage());return;}
        if(failure instanceof javax.net.ssl.SSLException){error("A secure connection could not be verified.");return;}
        android.util.Log.w("Iskrio","Link import failed: "+failure.getClass().getSimpleName());
        error("Could not download this link. Check your connection or try a direct media link.");
    }
    final class Bridge {
        @JavascriptInterface public boolean getInternetAccess(){return internetAccess.get();}
        @JavascriptInterface public void setInternetAccess(boolean enabled){MainActivity.this.setInternetAccess(enabled);}
        @JavascriptInterface public boolean isLinkImportActive(){return linkImport.get()!=null;}
        @JavascriptInterface public void pasteLink(){runOnUiThread(()->{
            try{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=clipboard.getPrimaryClip();
                CharSequence text=clip!=null&&clip.getItemCount()>0?clip.getItemAt(0).getText():null;
                js("nativeClipboard",text==null?"":text.toString().substring(0,Math.min(8192,text.length())));
            }catch(Exception e){error("The clipboard is unavailable.");}
        });}
        @JavascriptInterface public void cancelLinkImport(){MainActivity.this.setInternetAccess(false);if(linkImport.get()==null)js("nativeCancelled","link");}
        @JavascriptInterface public void resolveLink(String source,String token){
            LinkImporter task=beginLink();if(task==null)return;
            worker.execute(()->{try{
                JSONObject result=task.resolve(source,token);task.close();if(task.isCancelled())throw new CancellationException();
                if(linkImport.compareAndSet(task,null))js(result.has("postId")?"nativeLinkPostId":"nativeLinkOptions",result);
            }catch(Throwable e){linkFailure(task,e);}finally{task.close();}});
        }
        @JavascriptInterface public void downloadLinkAudio(String source){
            LinkImporter task=beginLink();if(task==null)return;importBusy=true;
            worker.execute(()->{File file=null;try{file=task.downloadAudio(MainActivity.this,source,percent->{if(linkImport.get()==task)js("nativeLinkProgress",percent);});finishDownloadNetwork(task);if(task.isCancelled())throw new CancellationException();JSONObject info=StudioMedia.importMedia(MainActivity.this,Uri.fromFile(file),true,percent->{if(task.isCancelled())throw new CancellationException();});if(linkImport.compareAndSet(task,null))js("nativeStudioMedia",new JSONObject().put("kind","audio").put("items",new JSONArray().put(info)));}catch(Throwable e){linkFailure(task,e);}finally{task.close();if(file!=null)file.delete();importBusy=false;}});
        }
        @JavascriptInterface public void downloadLinkMedia(String source){
            LinkImporter task=beginLink();if(task==null)return;
            worker.execute(()->{File file=null;boolean keep=false;try{
                file=task.download(MainActivity.this,source,progress->{if(linkImport.get()==task)js("nativeLinkProgress",progress);});
                finishDownloadNetwork(task); // Gate off and connections closed before local decoding.
                if(task.isCancelled())throw new CancellationException();
                String mime=LinkImporter.mediaType(file);JSONObject media=new JSONObject().put("mime",mime);String kind;
                if(mime.startsWith("video/")||mime.equals("image/gif")){media.put("uri",Uri.fromFile(file).toString());kind=mime.startsWith("video/")?"video":"gif";}
                else{media.put("data",DownloadedImage.dataUrl(file));kind="main";}
                if(task.isCancelled())throw new CancellationException();
                if(linkImport.compareAndSet(task,null)){
                    if(media.has("uri")){pendingLinkFile=file;keep=true;}
                    js("nativeLinkFile",new JSONObject().put("kind",kind).put("files",new JSONArray().put(media)));
                }
            }catch(Throwable e){linkFailure(task,e);}finally{task.close();if(!keep&&file!=null)file.delete();}});
        }

        @JavascriptInterface public void discardLinkMedia(){if(!importBusy&&!extractionRunning)releaseLinkFile();}
        @JavascriptInterface public void studioLinkAudio(String uri){
            if(videoExportBusy||importBusy||extractionRunning||pendingExport!=null||pendingExportFile!=null){error("Finish the current operation first.");return;}
            File downloaded=pendingLinkFile;if(downloaded==null||!Uri.fromFile(downloaded).toString().equals(uri)){error("This download is no longer available.");return;}
            importBusy=true;cancelArchive.set(false);worker.execute(()->{File temp=null;try{
                temp=StudioLinkMedia.audio(MainActivity.this,downloaded,cancelArchive);
                JSONObject info=StudioMedia.importMedia(MainActivity.this,Uri.fromFile(temp),true,percent->{if(cancelArchive.get())throw new CancellationException();});
                js("nativeStudioMedia",new JSONObject().put("kind","audio").put("items",new JSONArray().put(info)));
            }catch(CancellationException e){js("nativeCancelled","pick");}catch(Exception e){error(e.getMessage()==null?"Could not import this audio.":e.getMessage());}
            finally{if(temp!=null)temp.delete();releaseLinkSource(uri);importBusy=false;}});
        }
        @JavascriptInterface public void studioLinkPhoto(String data){importStudioLink(data,false);}
        @JavascriptInterface public void studioLinkGif(String uri){importStudioLink(uri,true);}
        @JavascriptInterface public void exportVideo(String source,long start,long end,int width,int height,double volume,boolean mute,String overlayBase64){
            if(videoExportBusy||extractionRunning||importBusy||pendingExport!=null||pendingExportFile!=null){error("Finish the current operation first.");return;}
            if(overlayBase64.length()>24*1024*1024){error("The video overlay is too large.");return;}
            videoExportBusy=true;
            worker.execute(()->{try{File file=VideoStore.resolve(MainActivity.this,source);byte[] bytes=Base64.decode(overlayBase64,Base64.DEFAULT);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null||bitmap.getWidth()!=width||bitmap.getHeight()!=height)throw new IOException("Invalid overlay.");
                runOnUiThread(()->{try{if(readyVideoFile!=null)readyVideoFile.delete();readyVideoFile=null;readyVideoToken=null;videoExporter=new VideoExporter();
                    videoExportListener=new VideoExporter.Listener(){public void ready(File output){videoExportBusy=false;readyExportName="Iskrio-video.mp4";readyExportMime="video/mp4";readyVideoFile=output;readyVideoToken=java.util.UUID.randomUUID().toString();try{js("nativeVideoExportReady",new JSONObject().put("token",readyVideoToken).put("bytes",output.length()));}catch(JSONException ignored){}}
                        public void failed(String message){videoExportBusy=false;error(message);}public void cancelled(){videoExportBusy=false;js("nativeCancelled","export");}};
                    videoExporter.start(MainActivity.this,file,start,end,width,height,(float)volume,mute,bitmap,progress->js("nativeVideoExportProgress",progress),videoExportListener);
                }catch(Exception failure){bitmap.recycle();videoExportBusy=false;error(failure.getMessage()==null?"Could not start video export.":failure.getMessage());}});
            }catch(Exception failure){videoExportBusy=false;error("Could not prepare this video for export.");}});
        }
        @JavascriptInterface public void backupWithVideos(String json){
            if(videoExportBusy||importBusy||extractionRunning||pendingExport!=null||pendingExportFile!=null){error("Finish the current operation first.");return;}cancelArchive.set(false);videoExportBusy=true;
            worker.execute(()->{try{File file=VideoArchive.create(MainActivity.this,json,progress->{if(cancelArchive.get())throw new CancellationException();js("nativeArchiveProgress",progress);});if(readyVideoFile!=null)readyVideoFile.delete();readyVideoFile=file;readyVideoToken=java.util.UUID.randomUUID().toString();readyExportName="Iskrio-video-backup.iskrio";readyExportMime="application/octet-stream";videoExportBusy=false;js("nativeArchiveReady",new JSONObject().put("token",readyVideoToken));}catch(CancellationException e){videoExportBusy=false;js("nativeCancelled","export");}catch(Exception e){videoExportBusy=false;error(e.getMessage()==null?"Could not create video backup.":e.getMessage());}});
        }
        @JavascriptInterface public void cancelArchive(){cancelArchive.set(true);if(studioEngine==null)com.arthenica.ffmpegkit.FFmpegKit.cancel();}
        @JavascriptInterface public void cancelVideoExport(){runOnUiThread(()->{if(videoExporter!=null&&videoExportListener!=null)videoExporter.cancel(videoExportListener);});}
        @JavascriptInterface public void saveVideo(String token){runOnUiThread(()->{
            if(readyVideoFile==null||readyVideoToken==null||!readyVideoToken.equals(token)){error("The video export is no longer available.");return;}
            if(importBusy||pendingExport!=null||pendingExportFile!=null){error("Finish the current file selection first.");return;}
            pendingExportFile=readyVideoFile;readyVideoFile=null;readyVideoToken=null;pendingName=readyExportName;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(readyExportMime);intent.putExtra(Intent.EXTRA_TITLE,pendingName);
            try{startActivityForResult(intent,SAVE);}catch(Exception e){pendingExportFile.delete();pendingExportFile=null;error("The system save dialog is unavailable.");}
        });}

        @JavascriptInterface public String licenseText(String name){try{if(!name.matches("(APP-LICENSE|THIRD-PARTY-NOTICES|Apache-2.0)\\.txt|ffmpeg/license[a-z0-9_]*\\.txt"))return "License unavailable.";try(InputStream in=getAssets().open("licenses/"+name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>512*1024)throw new IOException();out.write(buffer,0,n);}return out.toString("UTF-8");}}catch(Exception e){return "License unavailable.";}}
        @JavascriptInterface public void studioDetachAudio(String json){
            if(json.length()>65536||videoExportBusy||extractionRunning||importBusy||pendingExportFile!=null||pendingExport!=null){error("Finish the current operation first.");return;}
            studioCancelled.set(false);videoExportBusy=true;worker.execute(()->{StudioEngine engine=null;try{
                JSONObject clip=new JSONObject(json).put("muted",false).put("volume",1).put("transition","none").put("atMs",0);
                JSONObject project=new JSONObject().put("w",160).put("h",90).put("bg","#000000").put("video",new JSONObject().put("mode","sequence").put("clips",new JSONArray().put(clip))).put("layers",new JSONArray()).put("pips",new JSONArray()).put("audioTracks",new JSONArray());
                engine=new StudioEngine(MainActivity.this,n->js("nativeStudioDetachProgress",n));studioEngine=engine;if(studioCancelled.get())throw new CancellationException();
                File output=engine.render(new JSONObject().put("project",project).put("audioOnly",true).put("audioFormat","m4a").put("size",160).put("fps",24));
                JSONObject info=StudioMedia.importMedia(MainActivity.this,Uri.fromFile(output),true,n->{if(studioCancelled.get())throw new CancellationException();});
                if(studioCancelled.get())throw new CancellationException();videoExportBusy=false;js("nativeStudioDetached",info);
            }catch(CancellationException e){videoExportBusy=false;js("nativeCancelled","process");}catch(Exception e){videoExportBusy=false;error(e.getMessage()==null?"Could not detach audio.":e.getMessage());}finally{studioEngine=null;if(engine!=null)engine.cleanup();}});
        }
        @JavascriptInterface public void studioRender(String json){
            if(json.length()>48*1024*1024){error("This composition is too large.");return;}
            if(videoExportBusy||extractionRunning||importBusy||pendingExportFile!=null||pendingExport!=null){error("Finish the current operation first.");return;}
            trackFrameGeneration.incrementAndGet();studioCancelled.set(false);videoExportBusy=true;worker.execute(()->{StudioEngine engine=null;try{
                JSONObject request=new JSONObject(json);engine=new StudioEngine(MainActivity.this,percent->js("nativeStudioProgress",percent));studioEngine=engine;if(studioCancelled.get())throw new CancellationException();File output=engine.render(request);
                String purpose=request.optString("purpose","export");if(purpose.equals("preview")){
                    JSONObject info=VideoStore.importVideo(MainActivity.this,Uri.fromFile(output),percent->{});info.put("purpose","preview");if(studioPreviewSource!=null)VideoStore.discard(MainActivity.this,studioPreviewSource);studioPreviewSource=info.getString("src");videoExportBusy=false;js("nativeStudioReady",info);
                }else{
                    File folder=new File(getCacheDir(),"video-exports");folder.mkdirs();String extension=request.optBoolean("audioOnly")?request.optString("audioFormat","m4a"):request.optString("format","mp4");if(!extension.matches("mp4|m4a|mp3|wav|gif|png"))throw new IOException("Invalid export format.");File ready=new File(folder,java.util.UUID.randomUUID()+"."+extension);if(!output.renameTo(ready))throw new IOException("Could not prepare the output file.");
                    if(readyVideoFile!=null)readyVideoFile.delete();readyVideoFile=ready;readyVideoToken=java.util.UUID.randomUUID().toString();readyExportName="Iskrio-export."+extension;readyExportMime=extension.equals("mp3")?"audio/mpeg":extension.equals("wav")?"audio/wav":extension.equals("m4a")?"audio/mp4":extension.equals("gif")?"image/gif":extension.equals("png")?"image/png":"video/mp4";videoExportBusy=false;
                    js("nativeStudioReady",new JSONObject().put("purpose","export").put("token",readyVideoToken).put("bytes",ready.length()));
                }
            }catch(CancellationException e){videoExportBusy=false;js("nativeCancelled","export");}catch(Exception e){videoExportBusy=false;error(e.getMessage()==null?"Could not process the composition.":e.getMessage());}finally{studioEngine=null;if(engine!=null)engine.cleanup();}});
        }
        @JavascriptInterface public void studioTrackFrames(String json){if(json.length()>16000||videoExportBusy||importBusy)return;int generation=trackFrameGeneration.incrementAndGet();worker.execute(()->{if(generation!=trackFrameGeneration.get()||videoExportBusy)return;try{JSONObject request=new JSONObject(json);JSONArray frames=StudioTrackFrames.read(MainActivity.this,request.getJSONArray("items"),()->generation!=trackFrameGeneration.get()||videoExportBusy);if(generation==trackFrameGeneration.get())js("nativeStudioTrackFrames",new JSONObject().put("requestId",request.getInt("requestId")).put("frames",frames));}catch(Exception ignored){}});}
        @JavascriptInterface public String studioProxyFor(String source){return StudioPreviewCache.find(MainActivity.this,source);}
        @JavascriptInterface public long studioProxyBytes(){return StudioPreviewCache.bytes(MainActivity.this);}
        @JavascriptInterface public void studioClearProxies(){if(videoExportBusy||extractionRunning||importBusy){error("Finish processing before clearing previews.");return;}StudioPreviewCache.clear(MainActivity.this);}
        @JavascriptInterface public void studioFreezeFrame(String source,long timeMs){if(videoExportBusy||extractionRunning||importBusy){error("Finish the current operation first.");return;}videoExportBusy=true;studioCancelled.set(false);worker.execute(()->{try{if(studioCancelled.get())throw new CancellationException();JSONObject info=StudioPreviewCache.freezeFrame(MainActivity.this,source,timeMs);if(studioCancelled.get()){VideoStore.discard(MainActivity.this,info.getString("src"));throw new CancellationException();}js("nativeStudioFrozen",info);}catch(CancellationException e){js("nativeCancelled","process");}catch(Exception e){error(e.getMessage());}finally{videoExportBusy=false;}});}
        @JavascriptInterface public void studioOptimize(String source){
            if(videoExportBusy||extractionRunning||importBusy||pendingExportFile!=null||pendingExport!=null){error("Finish the current operation first.");return;}
            videoExportBusy=true;studioCancelled.set(false);worker.execute(()->{StudioEngine engine=null;try{String cached=StudioPreviewCache.find(MainActivity.this,source);if(!cached.isEmpty()){videoExportBusy=false;js("nativeStudioProxyReady",new JSONObject().put("original",source).put("src",cached));return;}engine=new StudioEngine(MainActivity.this,percent->js("nativeStudioProgress",percent));studioEngine=engine;if(studioCancelled.get())throw new CancellationException();File output=engine.render(StudioPreviewCache.request(MainActivity.this,source));JSONObject info=StudioPreviewCache.store(MainActivity.this,source,output);videoExportBusy=false;js("nativeStudioProxyReady",info);}catch(CancellationException e){videoExportBusy=false;js("nativeCancelled","export");}catch(Exception e){videoExportBusy=false;error(e.getMessage());}finally{studioEngine=null;if(engine!=null)engine.cleanup();}});
        }
        @JavascriptInterface public void studioCancel(){studioCancelled.set(true);StudioEngine engine=studioEngine;if(engine!=null)engine.cancel();}
        @JavascriptInterface public void studioRecordStart(){runOnUiThread(()->{
            if(recorder!=null)return;
            if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},63);return;}
            startStudioRecording();
        });}
        @JavascriptInterface public void studioRecordStop(){runOnUiThread(()->stopStudioRecording(true));}

        @JavascriptInterface public void finishApp(){runOnUiThread(()->finish());}
        @JavascriptInterface public void pick(String kind){runOnUiThread(()->{
            if(importBusy || pendingExport!=null || pendingExportFile!=null || videoExportBusy || extractionRunning){error("Finish the previous file selection first.");return;}
            importKind=kind; importBusy=true;
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(kind.equals("audio")?"*/*":kind.equals("studio")||kind.equals("pip")?"*/*":kind.equals("animation")?"*/*":kind.equals("video")?"video/*":kind.equals("backup")||kind.equals("subtitles")?"*/*":"image/*");
            if(kind.equals("animation"))i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/gif","video/*"});
            if(kind.equals("studio")||kind.equals("pip"))i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","image/*"});if(kind.equals("audio"))i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,kind.equals("frames")||kind.equals("collage")||kind.equals("studio")||kind.equals("audio"));
            try{startActivityForResult(i,PICK);}catch(Exception e){importBusy=false;error("The system file picker is unavailable.");}
        });}
        @JavascriptInterface public void saveOriginalGif(String name,String frame){
            worker.execute(()->{try{
                File source=FrameStore.originalGif(MainActivity.this,frame);
                File copy=File.createTempFile("original-export-",".gif",getCacheDir());
                Files.copy(source.toPath(),copy.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                runOnUiThread(()->{
                    if(pendingExport!=null||pendingExportFile!=null||importBusy||videoExportBusy||extractionRunning){copy.delete();error("Finish the previous file selection first.");return;}
                    pendingExportFile=copy;pendingName=name.replaceAll("[^a-zA-Z0-9._-]","_");pendingMime="image/gif";
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(pendingMime);intent.putExtra(Intent.EXTRA_TITLE,pendingName);
                    try{startActivityForResult(intent,SAVE);}catch(Exception e){copy.delete();pendingExportFile=null;error("Saving is unavailable.");}
                });
            }catch(Exception e){error(e.getMessage());}});
        }
        @JavascriptInterface public void save(String name,String mime,String b64){
            if(b64.length()>100*1024*1024){error("The export is too large. Reduce the resolution or frame count.");return;}
            try {byte[] data=Base64.decode(b64,Base64.DEFAULT);runOnUiThread(()->{
                if(pendingExport!=null || pendingExportFile!=null || importBusy || videoExportBusy || extractionRunning){error("Finish the previous file selection first.");return;}
                pendingExport=data;pendingName=name.replaceAll("[^a-zA-Z0-9._-]","_");pendingMime=mime;
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,pendingName);
                try{startActivityForResult(i,SAVE);}catch(Exception e){pendingExport=null;error("Saving is unavailable.");}
            });}catch(Exception e){error("Invalid export data.");}
        }
        @JavascriptInterface public void putLibrary(String json){
            if(json.length()>32*1024*1024){error("The library is too large. Export a backup and remove older projects.");return;}
            worker.execute(()->{try{File tmp=new File(getFilesDir(),"library.tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(json.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!tmp.renameTo(new File(getFilesDir(),"library.json")))throw new IOException();js("librarySaved","ok");}catch(Exception e){error("Could not save the library. Export a project backup.");}});
        }
        @JavascriptInterface public void getLibrary(){worker.execute(()->{try{File f=new File(getFilesDir(),"library.json");String data=f.exists()?new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):"{\"projects\":[],\"favorites\":[]}";js("nativeLibrary",new JSONObject(data));}catch(Exception e){error("Could not load the library. Restore it from a backup.");}});}
        @JavascriptInterface public String readMedia(String source){try{return FrameStore.readDataUrl(MainActivity.this,source);}catch(Exception e){return "";}}
        @JavascriptInterface public void cancelExtraction(){cancelExtraction.set(true);}
        @JavascriptInterface public void importAnimation(String kind,String uri){extractAnimation(kind,uri,0,0,30,true);}
        @JavascriptInterface public void extract(String kind,String uri,long start,long end,int fps){extractAnimation(kind,uri,start,end,fps,false);}
        private void extractAnimation(String kind,String uri,long start,long end,int fps,boolean automatic){
            if(extractionRunning){error("An animation is already being imported.");return;}
            cancelExtraction.set(false);extractionRunning=true;
            worker.execute(()->{JSONObject importedVideo=null;boolean delivered=false;try{
                int[] last={-1};
                java.util.function.IntConsumer progress=percent->{
                    if(cancelExtraction.get())throw new java.util.concurrent.CancellationException();
                    if(percent!=last[0]){last[0]=percent;js("nativeProgress",percent);}
                };
                if(automatic&&!MediaFrames.isGif(MainActivity.this,Uri.parse(uri))){
                    importedVideo=VideoStore.importVideo(MainActivity.this,Uri.parse(uri),progress);StudioMedia.addPreview(MainActivity.this,importedVideo);if(cancelExtraction.get())throw new CancellationException();js("nativeVideo",importedVideo);delivered=true;return;
                }
                JSONObject result=automatic?MediaFrames.extractAuto(MainActivity.this,kind,Uri.parse(uri),progress):MediaFrames.extract(MainActivity.this,kind,Uri.parse(uri),start,end,fps,progress);
                if(cancelExtraction.get())throw new java.util.concurrent.CancellationException();
                js("nativeFrames",result);
            }catch(java.util.concurrent.CancellationException e){js("nativeCancelled","extract");}
             catch(IllegalArgumentException e){error(e.getMessage());}
             catch(Throwable e){android.util.Log.e("Iskrio","Frame extraction failed",e);error("Could not load frames. Try a shorter segment or a different file.");}finally{if(importedVideo!=null&&!delivered)VideoStore.discard(MainActivity.this,importedVideo.optString("src"));extractionRunning=false;releaseLinkSource(uri);}});
        }

    }
    private byte[] readBounded(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>MAX_INPUT)throw new IOException("The file exceeds 40 MB.");out.write(buf,0,n);}return out.toByteArray();}
    private Bitmap scale(Bitmap b,int max){float s=Math.min(1f,max/(float)Math.max(b.getWidth(),b.getHeight()));return s==1?b:Bitmap.createScaledBitmap(b,Math.max(1,(int)(b.getWidth()*s)),Math.max(1,(int)(b.getHeight()*s)),true);}
    private String dataUrl(Bitmap b,String mime){ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(mime.equals("image/png")?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,90,o);return "data:"+mime+";base64,"+Base64.encodeToString(o.toByteArray(),Base64.NO_WRAP);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==SAVE){byte[] bytes=pendingExport;File file=pendingExportFile;String exportName=pendingName;pendingExport=null;pendingExportFile=null;if(result!=RESULT_OK||data==null){if(file!=null)file.delete();js("nativeCancelled","save");return;}Uri u=data.getData();worker.execute(()->{try{try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){if(out==null)throw new IOException();if(file!=null){try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}}else out.write(bytes);out.flush();}
            String actualName=exportName;try(android.database.Cursor cursor=getContentResolver().query(u,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))actualName=cursor.getString(0);}catch(Exception ignored){}js("nativeSaved",actualName);
        }catch(Exception e){error("Saving failed. Try a different location.");}finally{if(file!=null)file.delete();}});}
        if(request==PICK){importBusy=false;if(result!=RESULT_OK||data==null){js("nativeCancelled","pick");return;}String kind=importKind;if(kind.equals("studio")||kind.equals("pip")||kind.equals("audio")){cancelArchive.set(false);js("nativeStudioImporting",kind);}worker.execute(()->{
            try{JSONArray files=new JSONArray();int count=data.getClipData()!=null?Math.min(60,data.getClipData().getItemCount()):1;
                for(int x=0;x<count;x++){Uri u=data.getClipData()!=null?data.getClipData().getItemAt(x).getUri():data.getData();String mime=getContentResolver().getType(u);if(mime==null)mime="application/octet-stream";
                    try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
                    JSONObject f=new JSONObject().put("mime",mime).put("uri",u.toString());
                    if(kind.equals("studio")||kind.equals("pip")||kind.equals("audio")){files.put(StudioMedia.importMedia(MainActivity.this,u,kind.equals("audio"),percent->{if(cancelArchive.get())throw new CancellationException();}));continue;}
                    if(kind.equals("animation")||kind.equals("video")||kind.equals("gif")||mime.equals("image/gif")){files.put(f);continue;}
                    try(InputStream in=new BufferedInputStream(getContentResolver().openInputStream(u))){
                        if(kind.equals("backup")){in.mark(4);byte[] signature=new byte[4];int n=in.read(signature);in.reset();if(n==4&&signature[0]=='P'&&signature[1]=='K'){cancelArchive.set(false);js("nativeArchiveImporting","ok");JSONObject backup=VideoArchive.restore(MainActivity.this,in,progress->{if(cancelArchive.get())throw new CancellationException();});f.put("text",backup.toString());files.put(f);continue;}}
                        byte[] bytes=readBounded(in);
                        if(!kind.equals("backup")&&MediaFrames.isGifHeader(bytes)){f.put("mime","image/gif");files.put(f);continue;}
                        if(kind.equals("backup")||kind.equals("subtitles")){f.put("text",new String(bytes,StandardCharsets.UTF_8));}
                        else{BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(opts.outWidth<1)throw new IOException();int sample=1;while(Math.max(opts.outWidth,opts.outHeight)/sample>2048)sample*=2;opts.inJustDecodeBounds=false;opts.inSampleSize=sample;Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(b==null)throw new IOException();
                            // ImageDecoder applies EXIF orientation for JPEG/HEIC on API 28+.
                            if(Build.VERSION.SDK_INT>=28){try{Bitmap oriented=ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)),(decoder,info,source)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int w=info.getSize().getWidth(),h=info.getSize().getHeight();float s=Math.min(1f,2048f/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*s)),Math.max(1,(int)(h*s)));});b.recycle();b=oriented;}catch(Exception ignored){}}
                            f.put("data",dataUrl(b,"image/png"));b.recycle();}
                    }files.put(f);
                }if(kind.equals("studio")||kind.equals("pip")||kind.equals("audio"))js("nativeStudioMedia",new JSONObject().put("kind",kind).put("items",files));else js("nativePicked",new JSONObject().put("kind",kind).put("files",files));
            }catch(CancellationException e){js("nativeCancelled","pick");}catch(IllegalArgumentException e){error(e.getMessage());}catch(Throwable e){error("Could not open the file. Try a supported media file or Iskrio backup.");}
        });}
    }
    private void startStudioRecording(){try{
        recordingFile=File.createTempFile("voice-",".m4a",getCacheDir());recorder=new android.media.MediaRecorder();recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(44100);recorder.setAudioEncodingBitRate(128000);recorder.setOutputFile(recordingFile.getAbsolutePath());recorder.prepare();recorder.start();recordingStarted=android.os.SystemClock.elapsedRealtime();
        recordingHandler.post(new Runnable(){public void run(){if(recorder==null)return;long elapsed=android.os.SystemClock.elapsedRealtime()-recordingStarted;try{js("nativeStudioRecording",new JSONObject().put("recording",true).put("elapsedMs",elapsed));}catch(JSONException ignored){}if(elapsed>=600000)stopStudioRecording(true);else recordingHandler.postDelayed(this,500);}});
    }catch(Exception e){stopStudioRecording(false);error("Could not start microphone recording.");}}
    private synchronized void importStudioLink(String value,boolean gif){
        if(videoExportBusy||importBusy||extractionRunning||pendingExport!=null||pendingExportFile!=null){error("Finish the current operation first.");return;}
        if(value==null||value.length()>24*1024*1024){error("This imported image is too large.");return;}
        if(gif&&(pendingLinkFile==null||!Uri.fromFile(pendingLinkFile).toString().equals(value))){error("This download is no longer available.");return;}
        importBusy=true;cancelArchive.set(false);
        worker.execute(()->{File temp=null;try{
            temp=gif?StudioLinkMedia.gif(MainActivity.this,pendingLinkFile,cancelArchive):StudioLinkMedia.photo(MainActivity.this,value);
            if(cancelArchive.get())throw new CancellationException();
            JSONObject info=StudioMedia.importMedia(MainActivity.this,Uri.fromFile(temp),false,percent->{if(cancelArchive.get())throw new CancellationException();});
            js("nativeStudioMedia",new JSONObject().put("kind","studio").put("items",new JSONArray().put(info)));
        }catch(CancellationException e){js("nativeCancelled","pick");}catch(Exception e){error(e.getMessage()==null?"Could not import this media.":e.getMessage());}
        finally{if(temp!=null)temp.delete();if(gif)releaseLinkSource(value);importBusy=false;}});
    }
    private void stopStudioRecording(boolean keep){if(recorder==null)return;recordingHandler.removeCallbacksAndMessages(null);try{recorder.stop();}catch(Exception e){keep=false;}recorder.release();recorder=null;File file=recordingFile;recordingFile=null;try{js("nativeStudioRecording",new JSONObject().put("recording",false).put("elapsedMs",0));}catch(JSONException ignored){}if(keep&&file!=null)worker.execute(()->{try{JSONObject info=StudioMedia.importMedia(this,Uri.fromFile(file),true,percent->{});js("nativeStudioMedia",new JSONObject().put("kind","audio").put("items",new JSONArray().put(info)));}catch(Exception e){error("Could not keep this recording. Record at least one second.");}finally{file.delete();}});else if(file!=null)file.delete();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==63){if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)startStudioRecording();else error("Microphone access was not granted. You can still import audio files.");}}
    @Override public void onBackPressed(){web.evaluateJavascript("window.handleBack()",null);}
    @Override protected void onStop(){setInternetAccess(false);if(recorder!=null)stopStudioRecording(true);super.onStop();}
    @Override protected void onDestroy(){internetAccess.set(false);if(studioEngine!=null)studioEngine.cancel();if(recorder!=null)stopStudioRecording(false);if(videoExporter!=null)videoExporter.destroy();if(readyVideoFile!=null)readyVideoFile.delete();LinkImporter task=linkImport.get();if(task!=null)task.cancel();cancelExtraction.set(true);if(!extractionRunning)releaseLinkFile();worker.shutdown();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();web=null;}super.onDestroy();}
}
