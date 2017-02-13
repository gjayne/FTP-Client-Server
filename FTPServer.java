import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringTokenizer;

/*
 * FTP Server
 */
public class FTPServer {

	static Boolean userCommand = false;
	static Boolean passCommand = false;
	static Boolean portCommand = false;
	static Boolean retrCommand = false;
	static Boolean quitCommand = false;
	static int retrCounter = 0;
	static DataOutputStream outToClient;
	static String ip = "";
	static int port = 0;
	static Socket connectionSocket = null;
	static DataOutputStream fileTransfer;
	static Socket clientSocket;
	static BufferedReader inFromClient = null;

	public static void main (String[] args) throws Exception {

		String clientSentence = null;
		String portInput = args[0];
		int portInputNumber = Integer.parseInt(portInput);

		ServerSocket welcomeSocket = new ServerSocket(portInputNumber);

		try {
			connectionSocket = welcomeSocket.accept();
		}
		catch (IOException e) {
			System.out.println("Couldn't establish a connection socket.");
		}
		
		int loops = 0;

		while(true){
			if(connectionSocket.isClosed()){
				connectionSocket = welcomeSocket.accept();
				userCommand = false;
				passCommand = false;
				portCommand = false;
				retrCommand = false;
				quitCommand = false;
				retrCounter = 0;
				try {
					inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
				} catch (IOException e) {
					connectionSocket.close();
				}
				try {
					outToClient = new DataOutputStream(connectionSocket.getOutputStream());
				} catch (IOException e) {
					e.printStackTrace();
				}
				System.out.println("220 COMP 431 FTP server ready.");
				write("220 COMP 431 FTP server ready.");
			}
		
			try {
				inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			} catch (IOException e) {
				connectionSocket.close();
			}
			try {
				outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(loops == 0){
				System.out.println("220 COMP 431 FTP server ready.");
				write("220 COMP 431 FTP server ready.");
			}	

			
			loops++;
			if(!quitCommand){
				clientSentence = inFromClient.readLine();//+\r\n
				if(clientSentence != null && !clientSentence.equals("")){
					System.out.print(clientSentence+"\r\n");
				}
				else{
					connectionSocket.close();
				}
			}

			if(clientSentence != null){
				int count = 0;
				int index = 0;
				Boolean crlfError = false;
				String[] stringArr = clientSentence.split("\r|\n");
				for(int i = 0; i < stringArr.length-1; i++){
					count++;
					index += stringArr[i].length();
					crlfError = true;
					index++;

					try {
						parse(stringArr[i], !(i == stringArr.length-1));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}	
				if(count == stringArr.length-1){
					String nocrlferror = stringArr[stringArr.length-1]+"\r\n";

					try {
						parse(nocrlferror, false);
					} catch (IOException e) {
						e.printStackTrace();
					}	
				}
			}
		}
	
	}
	//tests the input and prints out the appropriate error or Command ok
	static void parse(String s, Boolean crlfError) throws IOException{
		Boolean usernameError = false;
		Boolean passwordError = false;
		Boolean commandError = false;
		Boolean parameterError = false;
		Boolean pathnameError = false;
		Boolean portError = false;
		Boolean retrValid = true;
		String token = ""; //current token
		String pathName = "";




		StringTokenizer tokenizedLine = new StringTokenizer(s);
		int numTokens = tokenizedLine.countTokens();

		if(tokenizedLine.hasMoreTokens()){
			token += tokenizedLine.nextToken();
		}

		switch(token.toUpperCase()){

		case "PORT":
			if(userCommand && passCommand && !retrCommand){
				String p = tokenizedLine.nextToken();
				String[] pArr = p.split(",");

				int port1;
				int port2;

				int portPlaceHolder;

				for(int i = 0; i < pArr.length; i++){
					for(int j = 0; j < pArr[i].length(); j++){
						int ascii = pArr[i].charAt(j);
						if(ascii < 48 || ascii > 57){
							portError = true;
						}
					}
				}
				if(pArr.length != 6){
					portError = true;
				}
				if((numTokens == 1)
						&& (s.charAt(4) == ' ')){
					portError = true;
				}
				if(!s.substring(0,5).equalsIgnoreCase("PORT ")){
					commandError = true;
				}
				if(!portError && pArr.length == 6){
					port1 = Integer.parseInt(pArr[4]); 
					port2 = Integer.parseInt(pArr[5]);
					port = (port1 * 256) + port2;
				}
				else{
					portError = true;
				}

				if(!portError && !commandError){
					for(int i = 0; i < pArr.length; i++){
						portPlaceHolder = Integer.parseInt(pArr[i]);
						if(portPlaceHolder > 256 || portPlaceHolder < 0){
							portError = true;
						}
					}
				}
				if(!commandError && !portError && !crlfError){
					for(int i = 0; i < 4; i++){
						if(i < 3){
							ip += pArr[i]+".";
						}
						else{
							ip += pArr[i];
						}
					}
				}

				if(commandError){
					write("500 Syntax error, command unrecognized.");
				}
				else if(portError || crlfError){
					write("501 Syntax error in parameter.");
				}
				else if(!commandError && !portError){
					System.out.print("200 Port command successful ("+ip+","+port+").\r\n");
					write("200 Port command successful ("+ip+","+port+").");
					portCommand = true;
					retrCommand = false;
				}
			}

			else if(userCommand && !passCommand){
				write("503 Bad sequence of commands.");
			}else if(!userCommand && !passCommand){
				write("530 Not logged in.");
			}
			break;

		case "RETR":

			if(userCommand && passCommand && portCommand && !retrCommand){

				if(!s.substring(0,5).equalsIgnoreCase("RETR ")){ 
					commandError = true;
					retrValid = false;
				}
				if((numTokens == 1)
						&& (s.charAt(4) == ' ')){
					pathnameError = true;
					retrValid = false;
				}
				if(tokenizedLine.hasMoreTokens()){
					String inputPath = tokenizedLine.nextToken();
					pathName = inputPath;

					for(int i = 0; i < inputPath.length();i++){
						if(inputPath.charAt(i) == '/'
								|| inputPath.charAt(i) == '\\'){
							pathName = pathName.substring(1);
						}
						else{
							i = inputPath.length();

						}
					}
					for(int j = 4; j < pathName.length(); j++){
						int ascii = pathName.charAt(j);
						if(ascii > 127){
							pathnameError = true;
							retrValid = false;

						}
					}
				}

				if(!pathnameError && !commandError && !crlfError){
					retrCounter++;
					FileInputStream input = null;
					FileOutputStream output = null;
					String fileName = "retr_files/file"+retrCounter;
					int data;
					Path filePath  = FileSystems.getDefault().getPath(pathName);
					File file = new File(pathName);


					if(file.getAbsolutePath() == null || !file.exists()){
						retrValid = false;
						write("550 File not found or access denied.");
						break;	
					}
					if(file.getAbsolutePath() != null && file.exists()){ 
						System.out.print("150 File status okay.");
						write("150 File status okay.");
						
						Socket fileSocket = null;
						try{
							fileSocket = new Socket(ip, port);
						}
						catch (Exception e){
							write("425 Can not open data connection.");
						}
						
					try{	
						input = new FileInputStream(file);
						byte[] fileByteArray = new byte[(int)file.length()];
						BufferedInputStream bis = new BufferedInputStream(input);
						bis.read(fileByteArray,0,fileByteArray.length);
						OutputStream os = fileSocket.getOutputStream();
						os.write(fileByteArray,0,fileByteArray.length);
						os.flush();
						System.out.print("250 Requested file action completed.\r\n");
						write("250 Requested file action completed.");
					}	
						
					catch (IOException e) {
						
								retrValid = false;
								write("550 File not found or access denied.");
						}
						
					}
					else if(!pathnameError && !commandError && !crlfError){
						retrValid = false;
						write("550 File not found or access denied.");
					}
				}
				if(commandError){
					write("500 Syntax error, command unrecognized.");
				}
				else if(pathnameError || crlfError){
					write("501 Syntax error in parameter.");
				}
				else if(retrValid){
					portCommand = false;
				}
			}

			else if(!userCommand && !passCommand){
				write("530 Not logged in.");
			}else if(userCommand && !passCommand){
				write("503 Bad sequence of commands.");
			}else if(!portCommand){
				write("503 Bad sequence of commands.");
			}
			break;

		case "USER": 

			if(!userCommand){
				if(!s.substring(0,5).equalsIgnoreCase("USER ")){ 
					commandError = true;
				}

				else if((numTokens == 1)
						&& (s.charAt(4) == ' ')){
					usernameError = true;
				}
				else{
					for(int i = 4; i < s.substring(4).length()+2; i++){
						int ascii = s.charAt(i);
						if(ascii > 127){
							usernameError = true;				
						}
					}
				}

				if(commandError){
					System.out.print("500 Syntax error, command unrecognized.\r\n");
					write("500 Syntax error, command unrecognized.\r\n");

				}
				else if(usernameError || crlfError){
					System.out.print("501 Syntax error in parameter.\r\n");
					write("501 Syntax error in parameter.\r\n");

				}
				else if(!commandError && !usernameError && !crlfError){
					System.out.print("331 Guest access OK, send password.\r\n");
					write("331 Guest access OK, send password.\r\n");

					userCommand = true;
				}
			}
			else{
				if(commandError){
					System.out.print("500 Syntax error, command unrecognized.\r\n");
					write("500 Syntax error, command unrecognized.\r\n");
				}
				else if(usernameError || crlfError){
					System.out.print("501 Syntax error in parameter.\r\n");
					write("501 Syntax error in parameter.");

				}
				else if(!commandError && !usernameError && !crlfError){
					System.out.print("503 Bad sequence of commands.\r\n");
					write("503 Bad sequence of commands.");

				}
			}
			break;

		case "PASS":

			if(userCommand && !passCommand){
				if(!s.substring(0,5).equalsIgnoreCase("PASS ")){
					commandError = true;
				}
				else if(numTokens == 1
						&& (s.charAt(4) == ' ')){
					passwordError = true;
				} 
				else {
					for(int i = 4; i < s.substring(4).length()+2; i++){
						int ascii = s.charAt(i);
						if(ascii > 127){
							passwordError = true;	

						}
					}

				}
				if(commandError){
					System.out.print("500 Syntax error, command unrecongnized.\r\n");
					write("500 Syntax error, command unrecongnized.");

				}else if(passwordError || crlfError){
					System.out.print("501 Syntax error in parameter.\r\n");
					write("501 Syntax error in parameter.");

				}else if(!commandError && !passwordError && !crlfError && userCommand){
					System.out.print("230 Guest login OK.\r\n");
					write("230 Guest login OK.");

					passCommand = true;
				}
			}
			else {
				if(commandError){
					System.out.print("500 Syntax error, command unrecongnized.\r\n");
					write("500 Syntax error, command unrecongnized.");

				}else if(passwordError || crlfError){
					System.out.print("501 Syntax error in parameter.\r\n");
					write("501 Syntax error in parameter.");

				}else if(!commandError && !passwordError && !crlfError){
					System.out.print("503 Bad sequence of commands.\r\n");
					write("503 Bad sequence of commands."); 

				}

			}
			break;

		case "TYPE": 
			if(userCommand && passCommand){
				Boolean typecodeError = false;
				if(tokenizedLine.hasMoreTokens()){
					String type = tokenizedLine.nextToken();
					if(!type.equals(null) && !(type.equals(""))){
						if((!type.equals("I") &&   
								!type.equals("A"))){
							typecodeError = true;
						}
					}
					else{
						typecodeError = true;
					}
					if(typecodeError){
						System.out.print("501 Syntax error in parameter.\r\n");
						write("501 Syntax error in parameter.");

					}
					else if(crlfError){
						System.out.print("501 Syntax error in parameter.\r\n");
						write("501 Syntax error in parameter.");

					}
					else if(!typecodeError && type.equals("I")){
						System.out.print("200 Type set to I.\r\n");
						write("200 Type set to I.");

					}
					else if(!typecodeError && type.equals("A")){
						System.out.print("200 Type set to A.\r\n");
						write("200 Type set to A.");

					}		
				}
				else if(!s.substring(0, 5).equalsIgnoreCase("TYPE ")){
					System.out.print("500 Syntax error, command unrecognized.\r\n");
					write("500 Syntax error, command unrecognized.");

				}
				else {
					System.out.print("501 Syntax error in parameter.\r\n");
					write("501 Syntax error in parameter.");

				}
			}
			else if(userCommand && !passCommand){
				System.out.print("503 Bad sequence of commands.\r\n");
				write("503 Bad sequence of commands.");

			}else if(!userCommand && !passCommand){
				System.out.print("530 Not logged in.\r\n");
				write("530 Not logged in.");
			}
			break;

		case "SYST": 
			if(userCommand && passCommand){
				if(tokenizedLine.countTokens() > 1){
					crlfError = true;
					System.out.println("ERROR -- CRLF");
					break;
				}
				else if(!s.substring(4).equals(null) && !s.substring(4).equals("\r\n") ){
					crlfError = true;
					System.out.println("ERROR -- CRLF");
					break;
				}	
				else if(!crlfError){
					System.out.print("215 UNIX Type: L8.\r\n");
					write("215 UNIX Type: L8.");

				}
			}
			else if(userCommand && !passCommand){
				System.out.print("503 Bad sequence of commands.\r\n");
				write("503 Bad sequence of commands.");

			}else if(!userCommand && !passCommand){
				System.out.print("530 Not logged in.\r\n");
				write("530 Not logged in.");

			}
			break;

		case "NOOP": 
			if(userCommand && passCommand){
				if(tokenizedLine.countTokens() > 1){
					System.out.print("501 Syntax error in parameter.\r\n");
					write("501 Syntax error in parameter.");

					break;
				}
				else if(!s.substring(4).equals(null) && !s.substring(4).equals("\r\n") ){
					System.out.print("501 Syntax error in parameter.\r\n");
					write("501 Syntax error in parameter.");

					break;
				}	
				System.out.print("200 Command OK.\r\n");
				write("200 Command OK.");

			}
			else if(userCommand && !passCommand){
				System.out.print("503 Bad sequence of commands.\r\n");
				write("503 Bad sequence of commands.");

			}else if(!userCommand && !passCommand){
				System.out.print("530 Not logged in.\r\n");
				write("530 Not logged in.");

			}

			break;

		case "QUIT":

			if(tokenizedLine.countTokens() > 1){
				System.out.print("501 Syntax error in parameter.\r\n");
				write("501 Syntax error in parameter.");
				break;
			}
			else if(!s.substring(4).equals(null) && !s.substring(4).equals("\r\n") ){
				System.out.print("501 Syntax error in parameter.\r\n");
				write("501 Syntax error in parameter.");
				break;
			}
			else{
				if(!quitCommand){
					System.out.print("200 Command OK.\r\n");
					write("221 Goodbye.");
					quitCommand = true; 
				}
			}
			break;

		default: 
			System.out.print("500 Syntax error, command unrecognized.\r\n");
			write("500 Syntax error, command unrecognized.");
			outToClient.flush();
		}
	}
	static public void write(String s){
		try {
			outToClient.writeBytes(s+"\r\n");
			outToClient.flush();
		} catch (IOException e) {
			System.out.println("Server write failed.");
		}
	}
}