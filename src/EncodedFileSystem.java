import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Date;

public class EncodedFileSystem {
	
	private static final Encoder ENCODER = Base64.getUrlEncoder();
	private static final Decoder DECODER = Base64.getUrlDecoder();
	
	private static final File DATA_LOCATION = new File("C:/Users/Nicholas/Desktop/encodedFileSystem");
	private static final File PASSWORD_FILE = new File(DATA_LOCATION, new String(ENCODER.encode("///Password///".getBytes())) + ".txt");
	
	private static String password = "";
	private static String location = "/";
	
	public static void setPassword(String p) {
		password = p;
	}
	
	public static boolean isPasswordValid() {
		try {
			InputStream is = DECODER.wrap(new FileInputStream(PASSWORD_FILE));
			
			String fileContents = "";
			while (true) {
				byte curByte = (byte) is.read();		
				if (curByte == -1) {
					break;
				}
				fileContents += (char) curByte;
			}
			
			is.close();
			
			return password.equals(fileContents);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public static boolean fileExists(String f) {
		File toCheck = new File(new File(DATA_LOCATION, location), f);
		return toCheck.exists();
	}
	
	public static String getLocation() {
		return location.replace('\\', '/');
	}
	
	public static boolean setLocation(String path) {
		if (new File(location, path).isDirectory()) {
			location = path;
			return true;
		} else {
			return false;
		}
	}
	
	public static String[] list(String f) {

		long sixMonths = 1000L * 60L * 60L * 24L * 30L * 6L;
		
		SimpleDateFormat monthFormat = new SimpleDateFormat("MMM");
		SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
		SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
		SimpleDateFormat yearFormat = new SimpleDateFormat("YYYY");
		
		File[] subFiles = new File(DATA_LOCATION, location).listFiles();
		if (subFiles == null) {
			subFiles = new File[] {new File(DATA_LOCATION, location)};
		}
		
		System.out.println(new File(DATA_LOCATION, location));
		String[] s = new String[subFiles.length];
		
		for (int i = 0; i < subFiles.length; i++) {
			File curSubFile = subFiles[i];
			Date lastModifiedDate = new Date(curSubFile.lastModified());
			s[i] = (curSubFile.isDirectory() ? "d" : "-") + "rwxrwxrwx   1 owner    group";
			s[i] += frontPad(curSubFile.length() + "", 16) + " "; 
			s[i] += monthFormat.format(lastModifiedDate) + " " + dayFormat.format(lastModifiedDate) + " ";
			if (new Date().getTime() - lastModifiedDate.getTime() < sixMonths) {
				s[i] += timeFormat.format(lastModifiedDate) + " ";
			} else {
				s[i] += " " + yearFormat.format(lastModifiedDate) + " ";
			}
			s[i] += curSubFile.getName() + "";
		}
		return s;
	}
	
	public static long size(String f) {
		return new File(DATA_LOCATION, location + (f != null ? f : "")).length();
	}
	
	public static InputStream openFile(String f) {
		try {
			File toRead = new File(DATA_LOCATION, location + (f != null ? f : ""));
			return DECODER.wrap(new FileInputStream(toRead));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static byte[] readFile(InputStream is) {
		try {
			byte[] byteBuffer = new byte[1024];
			int bytesRead = is.read(byteBuffer);
			
			if (bytesRead == -1) {
				is.close();
				return null;
			} else {
				return Arrays.copyOf(byteBuffer, bytesRead);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static String frontPad(String s, int targetLength) {
		while (s.length() < targetLength) {
			s = " " + s;
		}
		return s;
	}
}
