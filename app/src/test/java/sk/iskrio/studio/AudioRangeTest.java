package sk.iskrio.studio;
import org.junit.Test;
import static org.junit.Assert.*;
public class AudioRangeTest {
 @Test public void acceptsExactRangesAndFinalPartialBlock(){assertEquals(100000,LinkImporter.rangeSize("bytes 16384-32767/100000",16384,16384));assertEquals(20000,LinkImporter.rangeSize("bytes 16384-19999/20000",16384,16384));}
 @Test public void rejectsUnsupportedAndMisreportedRanges(){for(String value:new String[]{null,"","bytes */100000","bytes 0-9999/100000","bytes 1-16384/100000","bytes 0-16383/0","bytes 0-16383/999999999999999999999999"})assertThrows(IllegalArgumentException.class,()->LinkImporter.rangeSize(value,0,16384));}
 @Test public void rejectsWholeVideoResponsesAndOversizedSources(){assertThrows(IllegalArgumentException.class,()->LinkImporter.rangeSize("bytes 0-99999/100000",0,16384));assertThrows(IllegalArgumentException.class,()->LinkImporter.rangeSize("bytes 0-16383/2147483648",0,16384));}
}
