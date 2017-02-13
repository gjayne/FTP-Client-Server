import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.StringTokenizer;

/*
 * FTP Client
 */

public class FTPClient {
	static int portNumber = 0;
	static String domain = "";
	static String port = "";
	static Boolean connectCommand = false;
	static Boolean commandError = false;
	static Boolean domainError = false;
	static Boolean portError = false;
	static Boolean pathnameError = false;
	static Boolean quitCommand = false;
	static int portNum = 8000;
	static BufferedReader inFromUser = null;
	static String sentence = "";
	static String modifiedSentence = "";
	static String portInput;
	static int portInputNumber;
	static Socket clientSocket;
	static DataOutputStream outToServer;
	static BufferedReader inFromServer;
	static String ipAddress = "";	
	static int retrPort = 0;
	static ServerSocket welcomeSocket = null;
	static Socket connectionSocket = null;
	static 	int fileCounter = 0;
	


	public static void main (String[] args) throws IOException{
		portInput = args[0];
		portInputNumber = Integer.parseInt(portInput);
		inFromUser = new BufferedReader(new InputStreamReader(System.in));

		while(!quitCommand){
			String userInput = inFromUser.readLine();
			System.out.println(userInput);
			sentence = userInput;
			parse(userInput);
		}	
	}

	public static void parse(String s) throws IOException{
		String token = "";
		StringTokenizer tokenizedLine = null;
		if(s != null){
			tokenizedLine = new StringTokenizer(s);
		}

		if(tokenizedLine != null && tokenizedLine.hasMoreTokens()){
			token += tokenizedLine.nextToken();
		}
		String pathname = "";

		switch(token){
			case "CONNECT":
				commandError = false;
				if(tokenizedLine.hasMoreTokens()){
					domain = tokenizedLine.nextToken();
					domainError = false;
					String[] domainSplit = domain.split("\\.");
					for(int i = 0; i < domainSplit.length; i++){
						int ascii = domainSplit[i].charAt(0);
						if(ascii < 65){
							domainError = true;
						}
						if(ascii > 90 && ascii < 97){
							domainError = true;
						}
						if(ascii > 122){
							domainError = true;
						}
					}
					for(int i = 0; i < domain.length(); i++){
						int ascii = domain.charAt(i);
						if((ascii < 48 || ascii > 122) && ascii != 46){
							domainError = true;
						}
						else if(ascii > 57 && ascii < 65){
							domainError = true;
						}
						else if(ascii > 90 && ascii < 97){
							domainError = true;
						}
					}
				} else{
					domainError = true;
				}
				if(s.charAt(0) == ' '){
					commandError = true;
				}
				if(tokenizedLine.hasMoreTokens()){
					port = tokenizedLine.nextToken();
					for(int i = 0; i < port.length(); i++){
						int ascii = port.charAt(i);
						if(ascii < 48 || ascii > 57){
							portError = true;
						}
					}
					if(!portError){
						portNumber = Integer.parseInt(port);
						if(portNumber < 0 || portNumber > 65535){
							portError = true;
						}
					}

				}
				else if(domain != "") {

					int num = s.indexOf(domain);
					num += domain.length();
					if(s.length() <= num){
						domainError = true;
					}
					else if(s.charAt(num) == ' '){
						portError = true;
					}
					else{
						domainError = true;
					}
				}
				if(s.length() > 7 && !s.substring(0, 8).equalsIgnoreCase("CONNECT ")){
					commandError = true;
				}
				if(s.length() == 7){
					commandError = true;
				}
				if(commandError){
					printError("request");
				}
				else if(domainError){
					printError("server-host");
				}
				else if(portError){
					printError("server-port");
				}
				else {
					System.out.print("CONNECT accepted for FTP server at host " + domain + " and port " + port + "\n");
				}
				if(connectCommand && !commandError && !domainError && !portError){
					reset();
					if(!socketConnection()){
					connectCommand = true;
					}
				}
				else if(!connectCommand && !commandError && !domainError && !portError){
					if(!socketConnection()){
						connectCommand = true;
					}
				}
				break;

			case "GET":
				if(connectCommand && s.charAt(0) != ' '){
					if(tokenizedLine.hasMoreTokens()){
						pathname = tokenizedLine.nextToken();
						pathnameError = false;
						for(int i = 0; i < pathname.length(); i++){
							int ascii = pathname.charAt(i);
							if(pathname.charAt(0) == ' '){
								pathnameError = true;
							}
							if(ascii > 128 || ascii == 10 || ascii == 13){
								pathnameError = true;
							}
						}
					}
					else{
						pathnameError = true;
					}
					if(s.length() <= 3 || !s.substring(0, 4).equalsIgnoreCase("GET ")){
						printError("request");
					}
					else if(!pathnameError){
						int index = s.indexOf(pathname);
						String fileName = s.substring(index);
						System.out.print("GET accepted for "+fileName+"\r\n");

						try {
							port();
						} catch (Exception e) {
							e.printStackTrace();
						}
						retr(fileName);
					}
					else{
						printError("pathname");
					}
				}
				else if(s.charAt(0) == ' ' || s.length() < 4 || s.charAt(3) != ' '){
					printError("request");
				}
				else{
					printError("expecting CONNECT");
				}
				break;

			case "QUIT":
				if(connectCommand && s.charAt(0) != ' '){
					if(s.length() > 4){
						printError("request");
					}
					else{
						System.out.println("QUIT accepted, terminating FTP client");
						System.out.print("QUIT\r\n");
						outToServer.writeBytes("QUIT\r\n");
						replyParser(inFromServer.readLine());
						quitCommand = true;
						clientSocket.close();
					}
				}
				else if(s.charAt(0) == ' '){
					printError("request");
				}
				else{
					printError("expecting CONNECT");
				}
				break;

			default:
				printError("request");
			}
	}
	
	public static void printError(String s){
		System.out.println("ERROR -- "+s);
	}
	
	public static void reset(){
		connectCommand = false;
		commandError = false;
		domainError = false;
		portError = false;
		pathnameError = false;
		
		try {
			clientSocket.close();
		} catch (IOException e) {
			System.out.print("Couldn't close clientSocket");
		}
		
	}
	
	public static void port() throws Exception{
		String myIP;
		InetAddress myInet;
		myInet = InetAddress.getLocalHost();
		myIP = myInet.getHostAddress();

		String[] IP = myIP.split("\\.");
		int x = 0;
		int y = 0;

		for(int i = 0; i < 4; i++){
			if(i < 3){
				ipAddress += IP[i]+",";
			}
			else{
				ipAddress += IP[i];
			}
		}
		x = portInputNumber/256;
		y = portInputNumber -(x*256);
		System.out.print("PORT "+ipAddress+","+x+","+y+"\r\n");
	
		try{
			welcomeSocket = new ServerSocket(portInputNumber);
			System.out.println("WelcomeSocket portNumber = "+portInputNumber);
			
			
		} catch (IOException e) {
			System.out.println("GET failed, FTP-data port not allocated.");
		}
		outToServer.writeBytes("PORT "+ipAddress+","+x+","+y+"\r\n");
		outToServer.flush();
		portInputNumber++;
		replyParser(inFromServer.readLine());
		portNum++;		
	}
	
	public static void retr(String pathname){
		System.out.print("RETR "+pathname+"\r\n");
		fileCounter++;
		String fileName = "retr_files/files"+fileCounter;
		String retrReply = null;	
		try{
			outToServer.writeBytes("RETR "+pathname+"\r\n");
			outToServer.flush();
			retrReply = inFromServer.readLine();
			
			replyParser(retrReply);
		}
		catch (Exception e){
			System.out.println("Cant readLine from Server");
		}
		if(retrReply.substring(0,3).equals("150")){
			try {
					
			connectionSocket = welcomeSocket.accept();
			}catch (IOException e) {
			System.out.print("GET failed, FTP-data port not allocated.\r\n");
		}
		byte[] byteArray = new byte[1024];
		InputStream in = null;
		try{
			in = connectionSocket.getInputStream();		
		}
		catch (Exception e){
			System.out.print("Cant create inputstream");
		}
		try{
			
			FileOutputStream os = new FileOutputStream(fileName,false);
			BufferedOutputStream bos = new BufferedOutputStream(os);
			byte[] buffer = new byte[9999];
			int byteRead = in.read(byteArray,0,byteArray.length);
			bos.write(byteArray,0,byteRead);
			bos.close();
			os.close();
			connectionSocket.close();
			welcomeSocket.close();
		}
		catch(Exception e){
			
		}
		}
	}
	
	public static boolean socketConnection()  {
		boolean connectFails = false;

		try {
			clientSocket = new Socket(domain,portNumber);
			outToServer = new DataOutputStream(clientSocket.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			replyParser(inFromServer.readLine());
		}
		catch (Exception e){
			connectFails = true;
			System.out.println("Connect Fails = "+connectFails);
			return connectFails;
		}
		
		try{
			System.out.println("USER anonymous");
			outToServer.writeBytes("USER anonymous\r\n");
			outToServer.flush();
			replyParser(inFromServer.readLine());
			System.out.println("PASS guest@");
			outToServer.writeBytes("PASS guest@\r\n");
			outToServer.flush();
			String passReply = inFromServer.readLine();
			replyParser(inFromServer.readLine());
			System.out.println("SYST");
			outToServer.writeBytes("SYST\r\n");
			outToServer.flush();
			replyParser(inFromServer.readLine());
			System.out.println("TYPE I");
			outToServer.writeBytes("TYPE I\r\n");
			outToServer.flush();
			replyParser(inFromServer.readLine());
		}
		catch(Exception e){
			connectFails = true;
			return connectFails;
		}
		return connectFails;
	}

	//replyParser takes the input from client and parses it.
	//It also prints out input and flushes the buffered reader

	static public void replyParser(String input) throws IOException{
		String errorToken;

		String [] inputArr = input.split("\r\n"); //Takes the value of input up until \r\n is encountered (excluding \r\n)

		String[] stringArr = input.split("\r|\n");  //put line in an array at index 0.  Split if encounters \r or \n

		String[] echo = input.split(""); //echo is an array with each letter of line in the following index
		int count = 0;
		int index = 0;
		Boolean crlfError = false;

		//Check for crlf error.  Only goes in the for loop if crlf error.
		for(int i = 0; i < stringArr.length-1; i++){
			count++;
			index += stringArr[i].length();
			crlfError = true;
			index++;
			System.out.print(stringArr[i]+echo[index]);
			parse(stringArr[i], !(i == stringArr.length-1)); 
		}	

		//if CRLF is already known to be correct
		if(count == stringArr.length-1){
			String nocrlferror = stringArr[stringArr.length-1]+"\r\n";
			parse(nocrlferror, false);
		}
	}

	public static void parse(String s, Boolean crlfError){
		StringTokenizer tokenizedLine = new StringTokenizer(s);
		int numTokens = tokenizedLine.countTokens();
		int replyCodeNum = 0;
		int index = 0;
		String replyCode = "";
		String replyText = "";
		Boolean replyCodeError = false;
		Boolean replyTextError = false;


		if(tokenizedLine.hasMoreTokens()){
			replyCode += tokenizedLine.nextToken();
		}
		else{
			replyCodeError = true;
		}
		for(int i = 0; i < replyCode.length(); i++){
			int ascii = replyCode.charAt(i);
			if(ascii < 48 || ascii > 57){
				replyCodeError = true;
			}
		}
		if(!replyCodeError){
			replyCodeNum = Integer.parseInt(replyCode);
			if(replyCodeNum < 100 || replyCodeNum > 599){
				replyCodeError = true;
			}
		}
		if(replyCode.length() > 3){
			replyCodeError = true;
		}
		if(tokenizedLine.hasMoreTokens()){
			replyText += tokenizedLine.nextToken();	
		}
		else {
			if(s.length()-2 > 4){

				replyText += s.substring(4);
			}
			else{
				replyTextError = true;
			}
		}
		index = s.indexOf(replyText);
		if(replyText.contains("\r | \n")){
			replyTextError = true;
		}
		for(int i = 0; i < s.substring(index).length(); i++){
			int ascii = s.substring(index).charAt(i);
			if(ascii < 0 || ascii > 127){
				replyTextError = true;
			}
		}
		if(replyCodeError){
			printError("reply-code");
		}
		else if(replyTextError){
			printError("reply-text");
		}
		else if(crlfError){
			printError("<CRLF>");
		}
		else{
			System.out.println("FTP reply "+replyCode+" accepted. Text is: "+s.substring(index,s.length()-2));
		}
	}
}

