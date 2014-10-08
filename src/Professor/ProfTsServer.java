package Professor;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.rmi.RemoteException;

import Telescope.RunTelescopes;

public class ProfTsServer extends Thread{
	/*
	 * This class implements the server responsible for professor-telescope communications
	 */
	//fields
	//debug sleep
	private long sleepTime = 500;
	private Professor prof;
	private Socket socket;
	private int name;
	private boolean end = false;
	//Constructor
	public ProfTsServer(Socket socket, int name){
		this.name = name;
		try {
			prof = Professor.getProfessor();
			this.socket = socket;
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	public void run(){
		//wait a message from telescope
		while(!end)
			waitMessage();
	}
	//Retrieve the array from the telescope
	public void retrieveArray() {
		printMessage("Retrieving array...");
		sendMessage(Messages.REQUEST_ARRAY);
	}
	//Print messages with the name of the thread
	private void printMessage(String message){
		System.out.println("[TS Server" + name + "]: " + message);
	}
	//wait a message from telescope
	private void waitMessage(){
		//printMessage("Waiting message...");
		try {
			BufferedReader readFromTS =new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String msg = readFromTS.readLine();
			gotMessage(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//Decode message and decide actions
	private void gotMessage(String msg){
		//Decode message
		//printMessage("Got Message: " + msg);
		if(msg.equals(Messages.REQUEST_KESSLER_MAX)){
			//Send kesslerMax to telescope
			debugSleep(sleepTime);
			sendKesslerMax();
		} else if(msg.equals(Messages.WONT_SEND)){
			//The telescope won't send anything
			debugSleep(sleepTime);
			gotNothing();
		} else if(msg.contains(Messages.SEND_KESSLER_MAX)){
			//The telescope sent a new kesslerMax
			debugSleep(sleepTime);
			newKesslerMaximum(msg);
		} else if(msg.contains(Messages.SEND_ARRAY)){
			//The telescope sent the data array
			debugSleep(sleepTime);
			newDataArray(msg);
		} else if(msg.equals(Messages.CLOSE_SERVER)){
			//Close this server thread
			endServer();
		}
	}
	//Get the new data array from the message and pass it to professor
	private void newDataArray(String msg) {
		int[] result = new int[10];
		String dataStr = msg.substring(Messages.SEND_ARRAY.length(),msg.length()-1);
		printMessage("Array received: " + dataStr);
		String[] data = dataStr.split(",");
		for(int i = 0; i < data.length; i++)
			result[i] = Integer.parseInt(data[i]);
		prof.setNewArray(result);
	}
	//The telescope sent us a new kesslerMaximum
	private void newKesslerMaximum(String msg) {
		if(RunTelescopes.isTokenRingEnabled()){
			//Token ring enabled - set new kesslerMax and new bestTscope
			int bestTscope = Integer.parseInt((msg.substring(Messages.SEND_KESSLER_MAX.length(), Messages.SEND_KESSLER_MAX.length()+1)));
			int newKessMax = Integer.parseInt((msg.substring(Messages.SEND_KESSLER_MAX.length()+1)));
			printMessage("The best telescope is tscope number " + bestTscope + " with a kesslerMaximum value of " + newKessMax +".");
			prof.setNewKessMax(newKessMax, bestTscope);
			finishedComm();
		} else{
			//Token ring disabled - Set new kesslerMaximum
			printMessage("The telescope sent a new kesslerMaximum.");
			int newKessMax = Integer.parseInt((msg.substring(Messages.SEND_KESSLER_MAX.length())));
			printMessage("The new value sent in from the ts is : " + newKessMax);
			prof.setNewKessMax(newKessMax, name);
			finishedComm();
		}
	}
	private void finishedComm() {
		//We're done
		prof.ImDone();
	}
	//The telescope won't send anything, try to wake somone else and close
	private void gotNothing() {
		printMessage("The telescope won't be sending anything.");
		finishedComm();
	}
	//Send kesslerMaximum to telescope
	private void sendKesslerMax() {
		printMessage("Trying to send kesslerMaximum to telescope...");
		//Ask kesslerMaximum from professor
		int kessMax = prof.getKesslerMaximum(name);
		//Professor returned kesslerMaximum, send it to telescope
		printMessage("Sending kesslerMaximum to telescope.");
		sendMessage(Messages.GIVE_KESSLER_MAX + kessMax);
	}
	//Close this server thread
	private void endServer(){
		printMessage("Server closed.");
		end = true;
	}
	//Send the given message to Professor
	private void sendMessage(String message){
		//printMessage("Sending message: " + message);
		DataOutputStream toTscope;
		try {
			toTscope = new DataOutputStream(socket.getOutputStream());
			toTscope.writeBytes(message + "\n");
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
