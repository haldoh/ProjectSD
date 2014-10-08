package Telescope;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import Professor.Messages;

public class Telescope extends Thread {
	/*
	 * This class defines the behavior of a telescope
	 */
	//debug sleep
	private long sleepTime = 1000;
	//data fields
	private int name = 0;
	private int[] data = {0,0,0,0,0,0,0,0,0,0};
	private int kesslerMinimum = 0;
	private int kesslerMaximum = 0;
	private int bestTscope = 0;
	private int port = 0;
	private boolean run = true;
	private Socket socket = null;
	private Socket prevTscope = null;
	private Socket nextTscope = null;
	private DataOutputStream toNextTscope;
	private DataOutputStream toProf;
	//State flags
	private boolean kessRequested = false;
	private boolean answerSent = false;
	//constructor
	public Telescope(int port, int name){
		this.port = port;
		this.name = name;
	}
	//Get data
	public int[] getData(){
		return data;
	}
	//Get kesslerMinimum
	public int getKesslerMinimum(){
		return kesslerMinimum;
	}
	//Run method
	public void run(){
		//Set up connections
		setupConnections();
		while(run){
			waitMessage();
		}
		printMessage("Telescope closed.");
	}
	//Get name
	public int getTSName(){
		return name;
	}
	//Token received - go ahead
	public void goAhead(){
		printMessage("Resuming operations after receiving token.");
		requestKesslerMaximum();
	}
	//Print a message using the telescope name
	private void printMessage(String message){
		System.out.println("[Telescope" + name + "]: " + message);
	}
	//Set up connection - if not already established
	private void setupConnections(){
		if(RunTelescopes.isTokenRingEnabled()){
			//Token ring is enabled - maintain a connection with previous and next telescope
			//Connect to Telescopes preceding this one
			connectPrevTscope();
			//Setup socket and wait for telescope next to this one to connect
			connectNextTscope();
		}
		//Wait connection from professor
		connectToProf();
	}
	//Connect to previous tscope
	private void connectPrevTscope(){
		if(prevTscope == null || prevTscope.isClosed()){
			try {
				prevTscope = new Socket("localhost", RunTelescopes.TRPorts[name - 1]);
				printMessage("Connected to Telescope" + (name-1) + " for token ring.");
			} catch (UnknownHostException e) {
				//printMessage("UnknownHostException.");
				e.printStackTrace();
			} catch (IOException e) {
				//printMessage("IOException.");
				e.printStackTrace();
			} catch (IndexOutOfBoundsException e){
				//the previous telescope doesn't exist - do nothing
			}
		}
	}
	//Connect to next tscope
	private void connectNextTscope(){
		printMessage("connect next.");
		if(nextTscope == null || nextTscope.isClosed()){
			//Check that this is not the last telescope
			if(name < (RunTelescopes.ports.length - 1)){
				try {
					//Set up a server socket
					ServerSocket welcomeSocket = new ServerSocket(RunTelescopes.TRPorts[name]);
					printMessage("Created socket for token ring on port: " + RunTelescopes.TRPorts[name]);
					//Wait for a connection
					printMessage("Waiting for token ring socket connection...");
					nextTscope = welcomeSocket.accept();
					printMessage("Accepted token ring socket connection");
				} catch (IOException e) {
					//printMessage("IOException.");
					e.printStackTrace();
				}
			} else {
				//This is the last telescope - connect to professor
				connectToProf();
			}
		}
	}
	//Connect to professor
	private void connectToProf(){
		try {
			if(socket == null || socket.isClosed()){
				//Set up a server socket
				ServerSocket welcomeSocket = new ServerSocket(port);
				printMessage("Created Telescope" + name + " on port: " + port);
				//Wait for a connection
				printMessage("Waiting for socket connection...");
				socket = welcomeSocket.accept();
				printMessage("Accepted socket connection");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//Wait for request from professor
	private void waitMessage(){
		//printMessage("Waiting for a message...");
		try {
			BufferedReader readFromProf =new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String msg = readFromProf.readLine();
			gotMessage(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//Decode message and decide actions
	private void gotMessage(String msg){
		//Decode message
		//printMessage("Got Message: " + msg);
		if(msg.equals(Messages.GENERATE_DATA)){
			debugSleep(sleepTime);
			generateData();
			/*
			 * If token ring is enabled, and this is the first telescope, go ahead
			 * If token ring is disabled, or is enabled but this is not the first tscope
			 * wait for token
			 */
			if(RunTelescopes.isTokenRingEnabled()){
				//Token ring enabled - first telescope go, others wait for token
				if(name != 0)
					waitToken();
				else
					compareMaxMin();
			} else{
				//Toke ring disabled - request kessMax from prof
				requestKesslerMaximum();
			}
		}else if(msg.equals(Messages.REQUEST_ARRAY)){
			if(answerSent){
				//Send array to professor
				debugSleep(sleepTime);
				sendArray();
			}
		} else if(msg.contains(Messages.GIVE_KESSLER_MAX)){
			if(kessRequested){
				//Professor sent me kesslerMaximum
				debugSleep(sleepTime);
				printMessage("Got kesslerMaximum form Professor.");
				kesslerMaximum = Integer.parseInt((msg.substring(Messages.GIVE_KESSLER_MAX.length())));
				compareMaxMin();
			}
		} else if(msg.equals(Messages.CLOSE_TELESCOPE)){
			//Close socket and end execution
			debugSleep(sleepTime);
			printMessage("Professor sent in termination message. Closing connection and terminating telescope...");
			endTelescope();
		}
	}
	private void compareMaxMin(){
		if(kesslerMaximum < kesslerMinimum){
			kesslerMaximum = kesslerMinimum;
			bestTscope = name;
			//notify professor that the telescope have a better kesslerMax
			sendKesslerMaximum();
		} else {
			//notify that the telescope is not going to send anything
			sendNoMax();
		}
	}
	private void waitToken() {
		printMessage("Waiting for token...");
		String msg = "";
		try {
			BufferedReader readFromProf = new BufferedReader(new InputStreamReader(prevTscope.getInputStream()));
			msg = readFromProf.readLine();
			gotMessage(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
		//Decode message
		if(msg != null && msg.contains(Messages.GIVE_TOKEN)){
			printMessage("Got Token.");
			debugSleep(sleepTime);
			//Obtain data from message
			parseToken(msg);
			debugSleep(sleepTime);
			//Resume execution by comparing kesslerMaximum and kesslerMinimum
			compareMaxMin();
		}
	}
	//Parse token
	private void parseToken(String msg){
		//printMessage("Token received: " + msg);
		bestTscope = Integer.parseInt(msg.substring(Messages.GIVE_TOKEN.length(), Messages.GIVE_TOKEN.length()+1));
		//printMessage("Best Tscope received: " + bestTscope);
		kesslerMaximum = Integer.parseInt(msg.substring(Messages.GIVE_TOKEN.length()+1));
		//printMessage("kesslerMaximum received: " + kesslerMaximum);
	}
	//Generate data
	private void generateData(){
		printMessage("Professor requests data. Generating data.");
		//Reset state flags
		kessRequested = false;
		answerSent = false;
		for(int i = 0; i < data.length; i++){
			//Since generating data could require some time, use sleep to simulate complex computation
			try {Thread.sleep(500);}
			catch (InterruptedException e) {e.printStackTrace();}
			data[i] = (int) (Math.random() * Integer.MAX_VALUE);
		}
		Arrays.sort(data);
		kesslerMinimum = data[0];
	}
	//End the connection to Professor and close the telescope
	private void endTelescope() {
		//Notify to server the end of the execution
		printMessage("Notifying server that I'm closing...");
		sendMessage(Messages.CLOSE_SERVER);
		//Close socket and end execution
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		run = false;
		//trHandler.endHandler();
		this.interrupt();
	}
	//request actual value of kesslerMaximum
	private void requestKesslerMaximum(){
		printMessage("Requesting kesslerMaximum from Professor...");
		sendMessage(Messages.REQUEST_KESSLER_MAX);
		kessRequested = true;
	}
	//notify professor that the telescope have a better kesslerMax and send it
	private void sendKesslerMaximum(){
		if(RunTelescopes.isTokenRingEnabled()){
			//if token ring is enabled, send kessMax to next telescope with token
			printMessage("New value of kesslerMaximum found.");
			giveToken();
		} else {
			//if token ring is disabled, send kessMax to professor
			printMessage("Sending new kesslerMaximum to Professor. Value: " + kesslerMinimum);
			sendMessage(Messages.SEND_KESSLER_MAX + kesslerMaximum);
			answerSent = true;
		}
	}
	//Send array to professor
	private void sendArray(){
		printMessage("Professor requests data array. Sending array.");
		String s = "";
		for(int n : data)
			s = s + n + ",";
		sendMessage(Messages.SEND_ARRAY + s);
	}
	//notify that the telescope is not going to send anything
	private void sendNoMax(){
		if(RunTelescopes.isTokenRingEnabled()){
			//if token ring is enabled, send kessMax to next telescope with token
			printMessage("No new value of kesslerMaximum found.");
			giveToken();
		} else {
			//if token ring is disabled, notify professor that i won't send anything
			printMessage("Notifying Professor that I won't be sending anything.");
			sendMessage(Messages.WONT_SEND);
			answerSent = true;
		}
	}
	//Give token ring to next telescope
	private void giveToken(){
		if(name < (RunTelescopes.ports.length-1)){
			try {
				toNextTscope = new DataOutputStream(nextTscope.getOutputStream());
				toNextTscope.writeBytes(Messages.GIVE_TOKEN + bestTscope + kesslerMaximum + "\n");
				//printMessage("Best Tscope sent: " + bestTscope);
				//printMessage("kesslerMaximum sent: " + kesslerMaximum);
			} catch (IOException e) {
				//do nothing
			}
			answerSent = true;
			printMessage("Token sent.");
		} else {
			//This is the last telescope - send result to professor
			sendMessage(Messages.SEND_KESSLER_MAX + bestTscope + kesslerMaximum);
			answerSent = true;
		}
	}
	//Send the given message to Professor
	private void sendMessage(String message){
		//printMessage("Sending message: " + message);
		try {
			toProf = new DataOutputStream(socket.getOutputStream());
			toProf.writeBytes(message + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//Sleep for debug
	private static void debugSleep(long time){
		if(time > 0){
			try {
				Thread.sleep(time);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
