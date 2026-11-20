package sk.iskrio.studio;

import okhttp3.HttpUrl;
import java.net.*;
import java.util.*;

/** URL and DNS checks are applied to the exact addresses used by OkHttp. */
final class SafeUrlPolicy {
    static HttpUrl parse(String source) {
        if(source==null||source.length()>8192)throw new IllegalArgumentException("Paste a valid HTTPS link.");
        String text=source.trim();
        if(text.matches("(?s).*[\\x00-\\x20\\x7f].*"))throw new IllegalArgumentException("The link contains invalid characters.");
        HttpUrl url;
        try{url=HttpUrl.Companion.get(text);}catch(Exception e){throw new IllegalArgumentException("Paste a valid HTTPS link.");}
        if(!url.isHttps()||url.port()!=443)throw new IllegalArgumentException("Only standard HTTPS links are supported.");
        if(!url.username().isEmpty()||!url.password().isEmpty())throw new IllegalArgumentException("Links containing a username or password are not allowed.");
        String host=url.host().toLowerCase(Locale.ROOT);
        if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.endsWith(".internal")||(!host.contains(".")&&!host.contains(":")))throw new IllegalArgumentException("Local-network links are not allowed.");
        // OkHttp may route IP literals without consulting custom DNS.
        if(host.contains(":")||host.matches("[0-9.]+"))try{if(!isPublic(InetAddress.getByName(host)))throw new IllegalArgumentException("Local or reserved network addresses are not allowed.");}catch(UnknownHostException e){throw new IllegalArgumentException("Invalid network address.");}
        return url.newBuilder().fragment(null).build();
    }
    static List<InetAddress> resolve(String hostname)throws UnknownHostException {
        List<InetAddress> addresses=Arrays.asList(InetAddress.getAllByName(hostname));
        if(addresses.isEmpty())throw new UnknownHostException("No public address found.");
        for(InetAddress address:addresses)if(!isPublic(address))throw new UnknownHostException("Local or reserved network addresses are blocked.");
        return addresses;
    }
    static boolean isPublic(InetAddress address){
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        byte[] bytes=address.getAddress();
        if(bytes.length==4){int a=bytes[0]&255,b=bytes[1]&255,c=bytes[2]&255,d=bytes[3]&255;
            return !(a==0||a==10||a==127||a>=224||a==100&&b>=64&&b<=127||a==169&&b==254||a==172&&b>=16&&b<=31||a==192&&(b==168||b==0||b==88&&c==99)||a==198&&(b==18||b==19||b==51&&c==100)||a==203&&b==0&&c==113||a==168&&b==63&&c==129&&d==16);
        }
        if(bytes.length!=16||(bytes[0]&0xe0)!=0x20)return false; // Global unicast only.
        if((bytes[0]&255)==0x20&&(bytes[1]&255)==0x02)return false; // 6to4 tunnels.
        if((bytes[0]&255)==0x20&&(bytes[1]&255)==0x01){
            int c=bytes[2]&255,d=bytes[3]&255;
            if(c==0&&d==0||c==0x0d&&d==0xb8)return false; // Teredo / documentation.
        }
        return true;
    }
    static boolean isX(HttpUrl url){return Arrays.asList("x.com","www.x.com","mobile.x.com","twitter.com","www.twitter.com","mobile.twitter.com").contains(url.host());}
}
