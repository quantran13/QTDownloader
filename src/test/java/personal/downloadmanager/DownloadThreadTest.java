package personal.downloadmanager;

import personal.downloadmanager.DownloadThread;
import java.net.HttpURLConnection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author quan
 */
public class DownloadThreadTest {
	
	public DownloadThreadTest() {
	}
	
	@BeforeClass
	public static void setUpClass() {
	}
	
	@AfterClass
	public static void tearDownClass() {
	}
	
	@Before
	public void setUp() {
	}
	
	@After
	public void tearDown() {
	}

	/**
	 * Test of startDownload method, of class DownloadThread.
	 */
	@Test
	public void testStartDownload() {
		System.out.println("startDownload");
		DownloadThread instance = null;
		instance.startDownload();
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of joinThread method, of class DownloadThread.
	 */
	@Test
	public void testJoinThread() throws Exception {
		System.out.println("joinThread");
		DownloadThread instance = null;
		instance.joinThread();
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of getHttpConnection method, of class DownloadThread.
	 */
	@Test
	public void testGetHttpConnection() throws Exception {
		System.out.println("getHttpConnection");
		DownloadThread instance = null;
		HttpURLConnection expResult = null;
		HttpURLConnection result = instance.getHttpConnection();
		assertEquals(expResult, result);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of downloadToFile method, of class DownloadThread.
	 */
	@Test
	public void testDownloadToFile() throws Exception {
		System.out.println("downloadToFile");
		HttpURLConnection conn = null;
		DownloadThread instance = null;
		instance.downloadToFile(conn);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of writeToFile method, of class DownloadThread.
	 */
	@Test
	public void testWriteToFile() throws Exception {
		System.out.println("writeToFile");
		byte[] bytes = null;
		int bytesToWrite = 0;
		boolean overwrite = false;
		DownloadThread instance = null;
		instance.writeToFile(bytes, bytesToWrite, overwrite);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of run method, of class DownloadThread.
	 */
	@Test
	public void testRun() {
		System.out.println("run");
		DownloadThread instance = null;
		instance.run();
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}
	
}
