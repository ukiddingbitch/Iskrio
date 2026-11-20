package sk.iskrio.studio;

import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.json.*;
import java.util.*;

/** Reads standard public-page metadata only. Never loads resources or executes page scripts. */
final class PageMediaResolver {
    static final class Media {
        final String url,kind;
        final int score;
        Media(String url,String kind,int score){this.url=url;this.kind=kind;this.score=score;}
    }
    private final Map<String,Media> candidates=new LinkedHashMap<>();
    private HttpUrl base;
    private boolean declaresVideo;
    private int jsonNodes;
    private PageMediaResolver(HttpUrl base){this.base=base;}
    static List<Media> resolve(String html,String pageUrl){
        if(html.length()>2*1024*1024)throw new IllegalArgumentException("This page is too large to inspect safely. Try a direct media link.");
        int tags=0;for(int i=0;i<html.length();i++)if(html.charAt(i)=='<'&&++tags>30000)throw new IllegalArgumentException("This page is too complex. Try a direct media link.");
        PageMediaResolver resolver=new PageMediaResolver(SafeUrlPolicy.parse(pageUrl));
        Document doc=Jsoup.parse(html,pageUrl);
        Element base=doc.selectFirst("base[href]");if(base!=null)try{resolver.base=SafeUrlPolicy.parse(resolver.base.resolve(base.attr("href")).toString());}catch(Exception ignored){}
        resolver.meta(doc);
        int totalJson=0;
        for(Element script:doc.select("script[type=application/ld+json]")){
            String data=script.data();totalJson+=data.length();if(data.length()>512*1024||totalJson>1024*1024||!boundedJson(data))continue;
            try{resolver.json(new JSONTokener(data).nextValue(),0);}catch(JSONException ignored){}
        }
        for(Element video:doc.select("video")){
            resolver.declaresVideo=true;resolver.add(video.attr("src"),"video",80);
            for(Element source:video.select("source"))if(!source.attr("type").contains("html"))resolver.add(source.attr("src"),"video",80);
        }
        for(Element link:doc.select("link[rel=image_src]"))resolver.add(link.attr("href"),"image",20);
        for(Element link:doc.select("a[download][href]")){String href=link.attr("href");String kind=kind(href,"");if(!kind.isEmpty())resolver.add(href,kind,60);}
        // Inline images are a fallback; explicit metadata/player files rank above them.
        org.jsoup.select.Elements images=doc.select("main img[src], article img[src]");
        if(images.isEmpty()&&doc.select("img[src]").size()==1)images=doc.select("img[src]");
        int seen=0;for(Element image:images){if(++seen>100)break;if(small(image.attr("width"))||small(image.attr("height")))continue;resolver.add(image.attr("src"),"image",30);}
        return resolver.result();
    }
    private void meta(Document doc){
        for(Element element:doc.select("meta[property],meta[name]")){
            String property=element.hasAttr("property")?element.attr("property"):element.attr("name");property=property.toLowerCase(Locale.ROOT);
            if(property.equals("og:type")&&element.attr("content").toLowerCase(Locale.ROOT).contains("video"))declaresVideo=true;
            if(property.equals("og:video")||property.equals("og:video:url")||property.equals("og:video:secure_url")||property.equals("twitter:player:stream")){
                declaresVideo=true;Element next=element.nextElementSibling();String type="";
                for(int i=0;i<6&&next!=null;i++,next=next.nextElementSibling()){
                    String key=next.hasAttr("property")?next.attr("property"):next.attr("name");
                    if(key.equals("og:video")||key.equals("og:video:url")||key.equals("twitter:player:stream"))break;
                    if(key.equals("og:video:type")||key.equals("twitter:player:stream:content_type")){type=next.attr("content");break;}
                }
                if(!type.toLowerCase(Locale.ROOT).contains("html"))add(element.attr("content"),"video",100);
            }else if(property.equals("og:image")||property.equals("og:image:url")||property.equals("og:image:secure_url")||property.equals("twitter:image")||property.equals("twitter:image:src")){
                String url=element.attr("content");boolean card=property.startsWith("twitter:");add(url,"image",kind(url,"image").equals("gif")?(card?60:100):(card?15:70));
            }
        }
    }
    private void json(Object node,int depth)throws JSONException {
        if(node==null||depth>32||++jsonNodes>10000)return;
        if(node instanceof JSONArray){JSONArray array=(JSONArray)node;for(int i=0;i<Math.min(100,array.length());i++)json(array.opt(i),depth+1);return;}
        if(!(node instanceof JSONObject))return;
        JSONObject object=(JSONObject)node;String type=String.valueOf(object.opt("@type")).toLowerCase(Locale.ROOT);
        if(type.contains("videoobject")){declaresVideo=true;add(value(object.opt("contentUrl")),"video",105);}
        else if(type.contains("imageobject")){
            String content=value(object.opt("contentUrl"));boolean explicit=!content.isEmpty();if(!explicit)content=value(object.opt("url"));add(content,"image",explicit?105:50);
        }
        // Traverse content-bearing schema properties, excluding publisher logos, avatars and previews.
        for(String key:new String[]{"@graph","mainEntity","video","image","associatedMedia","encoding","primaryImageOfPage"}){
            Object child=object.opt(key);if(child instanceof String&&key.equals("image"))add((String)child,"image",80);else json(child,depth+1);
        }
    }
    private void add(String source,String hint,int priority){
        if(source==null||source.isEmpty()||source.length()>8192||candidates.size()>=100)return;
        try{
            HttpUrl url=base.resolve(source);if(url==null)return;url=SafeUrlPolicy.parse(url.toString());if(url.equals(base))return;
            String kind=kind(url.encodedPath(),hint);if(kind.isEmpty())return;
            String path=url.encodedPath().toLowerCase(Locale.ROOT);
            if(path.endsWith(".m3u8")||path.endsWith(".mpd")||path.endsWith(".svg")||path.endsWith(".swf"))return;
            int score=priority+(kind.equals("gif")?50:kind.equals("video")?25:0);
            Media old=candidates.get(url.toString());if(old==null||score>old.score)candidates.put(url.toString(),new Media(url.toString(),kind,score));
        }catch(IllegalArgumentException ignored){}
    }
    private List<Media> result(){
        List<Media> all=new ArrayList<>(candidates.values());all.sort((a,b)->Integer.compare(b.score,a.score));if(all.isEmpty())return all;
        Media best=all.get(0);
        if(declaresVideo&&!best.kind.equals("gif")&&!best.kind.equals("video"))return Collections.emptyList();
        List<Media> result=new ArrayList<>();for(Media media:all)if(media.kind.equals(best.kind)&&media.score>=best.score-5&&result.size()<16)result.add(media);
        return result;
    }
    private static String value(Object value){if(value instanceof String)return (String)value;if(value instanceof JSONObject)return ((JSONObject)value).optString("@id","");return "";}
    private static boolean small(String value){try{int number=Integer.parseInt(value);return number>0&&number<64;}catch(Exception ignored){return false;}}
    private static String kind(String value,String hint){
        String path=value.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        if(path.endsWith(".gif"))return "gif";
        if(path.endsWith(".mp4")||path.endsWith(".webm")||path.endsWith(".mov")||path.endsWith(".m4v"))return "video";
        if(path.endsWith(".png")||path.endsWith(".jpg")||path.endsWith(".jpeg")||path.endsWith(".webp"))return "image";
        return hint;
    }
    private static boolean boundedJson(String data){
        int depth=0;boolean string=false,escaped=false;
        for(int i=0;i<data.length();i++){char c=data.charAt(i);if(string){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')string=false;continue;}if(c=='"')string=true;else if(c=='{'||c=='['){if(++depth>32)return false;}else if(c=='}'||c==']'){if(--depth<0)return false;}}
        return depth==0&&!string;
    }
}
