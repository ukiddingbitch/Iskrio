package sk.iskrio.studio;
import org.junit.Test;
public class ArchiveCapacityTest {
 @Test public void libraryBackupCapacityIncludesMetadata(){VideoArchive.checkEntryCount(1);VideoArchive.checkEntryCount(66);VideoArchive.checkEntryCount(2581);}
 @Test(expected=IllegalArgumentException.class) public void rejectsExcessEntries(){VideoArchive.checkEntryCount(2582);}
 @Test(expected=IllegalArgumentException.class) public void rejectsEmptyArchive(){VideoArchive.checkEntryCount(0);}
}
