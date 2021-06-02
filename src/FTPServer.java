import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Pattern;

public class FTPServer {

	public static final String EOL = "\r\n";

	public static BufferedReader commandInputReader;
	public static OutputStream commandOutputStream;

	public static OutputStream dataOutputStream;

	public static ServerSocket commandServerSocket;
	public static ServerSocket dataServerSocket;

	public static void main(String[] args) {

		while (true) {
			Socket s = null;
			try {
				commandServerSocket = new ServerSocket(21);
				System.out.println("Waiting for connection");
				s = commandServerSocket.accept();
				System.out.println("Accepted connection");
				commandServerSocket.close();

				InputStream is = s.getInputStream();
				commandInputReader = new BufferedReader(new InputStreamReader(is));

				commandOutputStream = s.getOutputStream();

				sendResponseCode(200, "Microsoft FTP Service", false);

				while (true) {
					String line = readLine();

					if (line == null) {
						System.out.println("***Null read, closing connection***");
						break;
					}

					handleCommand(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				s.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void handleCommand(String message) {
		String command = null;
		String details = null;
		if (message.indexOf(" ") == -1) {
			command = message;
		} else {
			command = message.substring(0, message.indexOf(" "));
			details = message.substring(message.indexOf(" ") + 1);
		}
		command = command.toUpperCase();

		switch (command) {
		case "HOST":
			sendResponseCode(200, "Welcome.", false);
			break;
		case "USER":
			sendResponseCode(331, "All users are allowed, send master password.", false);
			break;
		case "PASS":
			handlePassword(details);
			break;
		case "OPTS":
			handleOptionsRequest(details);
			break;
		case "SYST":
			sendResponseCode(200, "Windows_NT", false);
			break;
		case "SITE":
		case "FEAT":
			sendResponseCode(214, "The following SITE commands are recognized (* ==>'s unimplemented).", true);
			sendResponse("    THMB");
			sendResponse("    HELP");
			sendResponseCode(214, "SITE command successful.", false);
			break;
		case "PWD":
			sendResponseCode(257, "\"" + EncodedFileSystem.getLocation() + "\" is current directory.", false);
			break;
		case "CWD":
			if (EncodedFileSystem.setLocation(details)) {
				sendResponseCode(250, "Current directory changed.", false);
			} else {
				sendResponseCode(550, "The filename, directory name, or volume label syntax is incorrect.", false);
			}
			break;
		case "TYPE":
			handleTransferMode(details);
			break;
		case "PASV":
			enablePassiveMode(false);
			break;
		case "EPSV":
			enablePassiveMode(true);
			break;
		case "LIST":
			handleListCommand(details);
			break;
		case "NOOP":
			sendResponseCode(200, "NOOP command successful.", false);
			break;
		case "SIZE":
			sendResponseCode(213, EncodedFileSystem.size(details) + "", false);
			break;
		case "RETR":
			handleReturnFile(details);
			break;
		case "REST":
			sendResponseCode(502, "REST not implemented.", false);
			break;
		case "QUIT":
			sendResponseCode(221, "Goodbye!", false);
			try {
				commandInputReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;

		default:
			System.err.println("unknown command");
			break;
		}
	}

	private static void handlePassword(String password) {
		EncodedFileSystem.setPassword(password);
		if (EncodedFileSystem.isPasswordValid()) {
			sendResponseCode(230, "User logged in.", false);
		} else {
			sendResponseCode(430, "Incorrect password.", false);
		}
	}

	private static void handleOptionsRequest(String details) {
		details = details.toUpperCase();

		switch (details) {
		case "UTF8 ON":
			sendResponseCode(200, "OPTS UTF8 command successful - UTF8 encoding now ON.", false);
			break;

		default:
			System.err.println("unknown option request");
			break;
		}
	}

	private static void handleTransferMode(String mode) {
		mode = mode.toUpperCase();
		switch (mode) {
		case "A":
			sendResponseCode(200, "Type set to A.", false);
			break;
		case "I":
			sendResponseCode(200, "Type set to I.", false);
			break;

		default:
			System.err.println("unknown transfer mode type");
			break;
		}
	}

	private static void enablePassiveMode(boolean extended) {
		try {
			String loopbackIP = InetAddress.getLoopbackAddress().getHostAddress();
			dataServerSocket = new ServerSocket(0);
			int portNum = dataServerSocket.getLocalPort();

			String passiveModeString = "(";
			if (extended) {
				passiveModeString += "|||" + portNum + "|)";
			} else {
				for (String curIPFragment : loopbackIP.split(Pattern.quote("."))) {
					passiveModeString += curIPFragment + ",";
				}
				passiveModeString += (portNum / 256) + "," + (portNum % 256) + ")";
			}

			new Thread(() -> {
				try {
					Socket dataSocket = dataServerSocket.accept();
					dataServerSocket.close();
					dataOutputStream = dataSocket.getOutputStream();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}, "Data-Socket-Connection-Thread").start();

			sendResponseCode(extended ? 229 : 227, "Entering Passive Mode " + passiveModeString + ".", false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void handleListCommand(String file) {

		if (file != null) {
			if (file.startsWith("-l")) {
				file = file.substring("-l".length()).trim();
			}
		} else {
			file = "";
		}

		if (!EncodedFileSystem.fileExists(file)) {
			sendResponseCode(550, "The filename, directory name, or volume label syntax is incorrect.", false);
			return;
		}

		sendResponseCode(125, "Data connection already open; Transfer starting.", false);

		String[] list = EncodedFileSystem.list(file);
		for (int i = 0; i < list.length; i++) {
			sendDataLine(list[i], i == list.length - 1);
		}

		sendResponseCode(226, "Transfer complete.", false);
	}

	private static void handleReturnFile(String file) {

		if (!EncodedFileSystem.fileExists(file)) {
			sendResponseCode(550, "The filename, directory name, or volume label syntax is incorrect.", false);
			return;
		}

		sendResponseCode(125, "Data connection already open; Transfer starting.", false);

		InputStream is = EncodedFileSystem.openFile(file);
		while (true) {
			byte[] curData = EncodedFileSystem.readFile(is);

			try {
				if (curData == null) {
					dataOutputStream.close();
					break;
				} else {
					dataOutputStream.write(curData);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		sendResponseCode(226, "Transfer complete.", false);
	}

	private static void sendResponseCode(int code, String message, boolean multiline) {
		try {
			String toSend = code + (multiline ? "-" : " ") + message;
			System.out.println("Sending: " + toSend);
			commandOutputStream.write((toSend + EOL).getBytes());
			commandOutputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void sendResponse(String message) {
		try {
			System.out.println("Sending: " + message);
			commandOutputStream.write((message + EOL).getBytes());
			commandOutputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void sendDataLine(String data, boolean closeAfter) {
		try {
			System.out.println("Sending data: " + data);
			dataOutputStream.write((data + EOL).getBytes());
			if (closeAfter) {
				dataOutputStream.close();
			} else {
				dataOutputStream.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String readLine() {
		try {
			System.out.println("Waiting on line read");
			String s = commandInputReader.readLine();
			System.out.println("Read line: " + s);
			return s;
		} catch (IOException e) {
			return null;
		}
	}

	private static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
