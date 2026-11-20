package sk.iskrio.studio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;

public class LinkImportSecurityTest {
    @Test public void standardHttpsOnly(){
        assertEquals("https://example.com/a.png",SafeUrlPolicy.parse("https://example.com/a.png#fragment").toString());
        for(String link:new String[]{"http://example.com/a","file:///sdcard/a.png","content://files/a","javascript:alert(1)","https://example.com:8443/a","https://user:secret@example.com/a","https://example.com/\nHeader"})assertThrows(IllegalArgumentException.class,()->SafeUrlPolicy.parse(link));
    }
    @Test public void privateIpLiteralsCannotBypassDns(){
        for(String host:new String[]{"127.0.0.1","0.0.0.0","10.0.2.2","192.168.1.1","172.16.1.1","169.254.169.254","100.100.100.200","2130706433","[::1]","[::ffff:127.0.0.1]","[fe80::1]","[fd00::1]"})assertThrows(host,IllegalArgumentException.class,()->SafeUrlPolicy.parse("https://"+host+"/image.png"));
        for(String host:new String[]{"localhost","router.local","metadata.google.internal","server"})assertThrows(IllegalArgumentException.class,()->SafeUrlPolicy.parse("https://"+host+"/"));
    }
    @Test public void publicAndReservedNetworks()throws Exception {
        assertTrue(SafeUrlPolicy.isPublic(InetAddress.getByName("8.8.8.8")));
        assertTrue(SafeUrlPolicy.isPublic(InetAddress.getByName("2001:4860:4860::8888")));
        for(String ip:new String[]{"192.0.2.1","198.51.100.1","203.0.113.1","198.18.0.1","224.0.0.1","255.255.255.255","2001:db8::1","2002:7f00:1::","2001::1"})assertFalse(ip,SafeUrlPolicy.isPublic(InetAddress.getByName(ip)));
    }
    @Test public void redirectTargetsUseTheSamePolicy(){
        okhttp3.HttpUrl original=SafeUrlPolicy.parse("https://example.com/start");
        assertEquals("example.com",SafeUrlPolicy.parse(original.resolve("/photo.jpg").toString()).host());
        for(String target:new String[]{"http://example.com/image","https://127.0.0.1/private","https://user:pass@public.example/a"})assertThrows(IllegalArgumentException.class,()->SafeUrlPolicy.parse(original.resolve(target).toString()));
    }
    @Test public void closedSessionCannotMakeMoreRequests(){
        LinkImporter importer=new LinkImporter();importer.close();
        assertThrows(CancellationException.class,()->importer.resolve("https://example.com/a.png",""));
        assertEquals(0,importer.connectionCount());
    }
    @Test public void internetGateRejectsBeforeProcessingAnyLink(){
        LinkImporter importer=new LinkImporter();
        try{assertThrows(IllegalArgumentException.class,()->importer.resolve("https://example.com/a.png",""));assertEquals(0,importer.connectionCount());}finally{importer.close();}
    }
    @Test public void disabledGateCannotBeBypassedByDirectNativeCalls(){
        java.util.concurrent.atomic.AtomicBoolean allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
        LinkImporter importer=new LinkImporter(allowed::get);allowed.set(false);
        try{assertThrows(IllegalArgumentException.class,()->importer.resolve("https://x.com/i/status/1969515038512926823","abc"));assertEquals(0,importer.connectionCount());}finally{importer.close();}
    }
    @Test public void htmlSvgAndExecutablesAreRejected()throws Exception {
        for(String content:new String[]{"<html><script>alert(1)</script></html>","<svg xmlns='http://www.w3.org/2000/svg'/>","PK\u0003\u0004not-an-image"}){
            File file=File.createTempFile("not-media",".tmp");try(FileOutputStream out=new FileOutputStream(file)){out.write(content.getBytes(StandardCharsets.UTF_8));}
            try{assertThrows(IllegalArgumentException.class,()->LinkImporter.mediaType(file));}finally{file.delete();}
        }
    }
}
