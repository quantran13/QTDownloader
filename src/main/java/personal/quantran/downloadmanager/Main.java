/**
 * Class: Main.java
 *
 * @author quan
 *
 */
package personal.quantran.downloadmanager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

	public static void main(String[] args) {
		String url = "http://mirror.internode.on.net/pub/test/100meg.test";
		//String url = "http://chicago.gaminghost.co/7/isos/x86_64/CentOS-7-x86_64-DVD-1511.iso";
		//String url = "https://doc-0s-38-docs.googleusercontent.com/docs/securesc/tft3eicc932i15ar37so9rauqeqt0u48/lf4hfcrgkd2sg4hn5mj7rkrm42sanjqj/1460599200000/17114335767397253931/17114335767397253931/0B0QSBviBwANoekZYQTU3MlRmM1U?e=download&h=18058446550967744138&nonce=o7aucg23fjhdm&user=17114335767397253931&hash=l694jmlum9grulbghu1ci4mmah71vkah";
		
		int partsCount = 8;
		
		Download newDownload = new Download(url, partsCount);

		try {
			newDownload.start();
		} catch (IOException | InterruptedException ex) {
			Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
			
			if (ex instanceof MalformedURLException)
				System.out.println("Malformed URL!");
		}
	}

}
