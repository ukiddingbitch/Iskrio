package sk.iskrio.studio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class PageMediaResolverTest {
 private List<PageMediaResolver.Media> resolve(String html){return PageMediaResolver.resolve(html,"https://example.com/posts/one");}
 @Test public void tenorStylePageChoosesOriginalGifOverVideoAndPoster(){
  String html="<meta property='og:image' content='https://media1.tenor.com/m/id/joke.gif'><meta property='og:video' content='https://media.tenor.com/id/joke.mp4'><script type='application/ld+json'>{\"@type\":\"Article\",\"image\":{\"@type\":\"ImageObject\",\"contentUrl\":\"https:\\u002F\\u002Fmedia1.tenor.com\\u002Fm\\u002Fid\\u002Fjoke.gif\"},\"video\":{\"@type\":\"VideoObject\",\"contentUrl\":\"https://media.tenor.com/id/joke.mp4\",\"thumbnailUrl\":\"https://media.tenor.com/id/joke.png\"}}</script>";
  List<PageMediaResolver.Media> media=resolve(html);assertEquals(1,media.size());assertEquals("https://media1.tenor.com/m/id/joke.gif",media.get(0).url);assertEquals("gif",media.get(0).kind);
 }
 @Test public void unfamiliarHostsUseStandardOpenGraph(){List<PageMediaResolver.Media> media=resolve("<meta property='og:video' content='/files/movie.mp4?x=1&amp;y=2'><meta property='og:video:type' content='video/mp4'>");assertEquals("https://example.com/files/movie.mp4?x=1&y=2",media.get(0).url);}
 @Test public void prefersGifOverWebpPreview(){List<PageMediaResolver.Media> media=resolve("<meta property='og:image' content='https://cdn.example.com/preview.webp'><meta property='og:image' content='https://cdn.example.com/original.gif'>");assertEquals(1,media.size());assertTrue(media.get(0).url.endsWith("original.gif"));}
 @Test public void htmlVideoSourceWinsOverPoster(){List<PageMediaResolver.Media> media=resolve("<meta property='og:image' content='/poster.jpg'><video><source type='video/webm' src='../clip.webm'></video>");assertEquals("https://example.com/clip.webm",media.get(0).url);}
 @Test public void schemaGraphsAndOpaqueMediaUrlsWork(){List<PageMediaResolver.Media> media=resolve("<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"VideoObject\",\"contentUrl\":\"https://cdn.example.com/download?id=123\"}]}</script>");assertEquals("video",media.get(0).kind);assertTrue(media.get(0).url.endsWith("?id=123"));}
 @Test public void playerPageDoesNotBecomeStaticThumbnail(){assertTrue(resolve("<meta property='og:type' content='video.other'><meta property='og:video' content='https://player.example.com/embed/one'><meta property='og:video:type' content='text/html'><meta property='og:image' content='/poster.jpg'>").isEmpty());}
 @Test public void unsafeDestinationsAndScriptsAreIgnored(){assertTrue(resolve("<meta property='og:video' content='https://127.0.0.1/private.mp4'><meta property='og:video' content='http://example.com/a.mp4'><video src='file:///sdcard/a.mp4'></video><script>fetch('https://example.com/track');</script>").isEmpty());}
 @Test public void imagesAndDownloadLinksHaveGenericFallbacks(){assertEquals("image",resolve("<main><img src='/photo.png' width='800' height='600'></main>").get(0).kind);assertEquals("gif",resolve("<a download href='/animation.gif'>Download</a>").get(0).kind);}
 @Test public void unsafeBaseDoesNotRouteToPrivateHosts(){List<PageMediaResolver.Media> media=resolve("<base href='https://192.168.1.1/'><video src='https://192.168.1.1/a.mp4'></video>");assertTrue(media.isEmpty());}
 @Test public void deeplyNestedJsonIsSkippedSafely(){String json="[".repeat(40)+"0"+"]".repeat(40);assertEquals(1,resolve("<script type='application/ld+json'>"+json+"</script><meta property='og:image' content='/real.gif'>").size());}
 @Test public void socialCardThumbnailDoesNotCompeteWithOriginalGif(){List<PageMediaResolver.Media> media=resolve("<meta property='og:image' content='/original.gif'><meta name='twitter:image' content='/static-card.gif'>");assertEquals(1,media.size());assertTrue(media.get(0).url.endsWith("original.gif"));}
 @Test public void primaryPagePhotoWinsOverIncidentalImages(){List<PageMediaResolver.Media> media=resolve("<meta property='og:image' content='/primary.jpg'><main><img src='/other.jpg' width='800' height='600'></main>");assertEquals(1,media.size());assertTrue(media.get(0).url.endsWith("primary.jpg"));}
 @Test public void excessiveHtmlIsRejectedBeforeParsing(){assertThrows(IllegalArgumentException.class,()->resolve("<i>".repeat(30001)));}
}
