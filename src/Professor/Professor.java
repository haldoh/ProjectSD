package Professor;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;

import Telescope.RunTelescopes;

public class Professor extends UnicastRemoteObject implements ProfessorIf{
	private static final long serialVersionUID = 1L;
	private static final int KESSLER_CONSTANT = (int) Math.pow(2,29);
	//The only professor instance
	private static Professor prof = null;
	//fields
	private int kesslerMaximum = 0;		//the value of kesslerMaximum
	private int bestTelescope = -1;		//the best telescope to which ask data
	private LinkedList<LinkedList<Integer>> data = new LinkedList<LinkedList<Integer>>();	//data collected
	private LinkedList<Integer> currData = new LinkedList<Integer>();
	private LinkedList<Integer> abortedData = new LinkedList<Integer>();
	private int succCounter = 0;		//Counter of successful experiments
	private boolean kessMonitor = true;	//monitor on variable kesslerMaximum
	private int servDone;				//number of servers that completed execution
	private boolean doneFlag;			//flag to end busy waiting during data collection
	private boolean finished;			//flag to end busy waiting for the new array
	private Object profLock = new Object();	//Object used as lock to make professor wait
	private Object assistLock = new Object(); //Object used as lock to make assistants wait
	private Socket[] tsSockets = new Socket[RunTelescopes.ports.length];	//sockets to telescopes
	private ProfTsServer serv[] = new ProfTsServer[RunTelescopes.ports.length];	//server threads responsible for tscope communications
	//Constructor
	private Professor() throws RemoteException{
		for(int i = 0; i < RunTelescopes.ports.length; i++){
			tsSockets[i] = null;
		}
	}
	//Get the instance of Professor
	public static synchronized Professor getProfessor() throws RemoteException{
		if(prof == null){
			prof = new Professor();
		}
		return prof;
	}
	//Get kesslerMaximum if not already in use
	public synchronized int getKesslerMaximum(int name){
		while(!kessMonitor){
			try {
				printMessage("Telescope" + name + " will wait...");
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		kessMonitor = false;
		return kesslerMaximum;
	}
	//Method used by servers to tell professor that they have done their job
	public synchronized void ImDone(){
		synchronized(profLock){
			if(RunTelescopes.isTokenRingEnabled()){
				//Token ring enabled - received result form telescopes
				doneFlag = true;
				profLock.notifyAll();
			} else{
				//Token ring disabled - free resources and check if all telescopes are done
				servDone++;
				kessMonitor = true;
				notifyAll();
				if(servDone == RunTelescopes.ports.length){
					doneFlag = true;
					profLock.notifyAll();
				}
			}
		}
	}
	//We got a new kessMax
	public void setNewKessMax(int newKessMax, int name) {
		kesslerMaximum = newKessMax;
		bestTelescope = name;
		printMessage("The new kesslerMaximum (from tscope" + name + ") is: " + kesslerMaximum);
	}
	//Get the sockets to the telescopes
	public Socket[] getTsSockets(){
		return tsSockets;
	}
	//Get data from the telescopes
	public void getTelescopesData(){
		//Reset kesslerMaximum and bestTelescope
		kesslerMaximum = 0;
		bestTelescope = -1;
		//Reset counter for servers that are done
		servDone = 0;
		doneFlag = false;
		finished = false;
		//Check if connection is established
		setTelescopesSockets();
		//Ask telescopes to generate data
		askGenData();
		//Start servers responsible for collecting telescopes messages
		startServers();
		synchronized(profLock){
			//Wait until servers are done
			while(!doneFlag){
				try {
					profLock.wait();
				} catch (InterruptedException e) {e.printStackTrace();}
			}
			//Get the array from the best telescope
			getNewArray();
			//Wait while server collects the new data array
			while(!finished){
				try {
					profLock.wait();
				} catch (InterruptedException e) {e.printStackTrace();}
			}
		}
	}
	//Save the new data array
	public void setNewArray(int[] newArray){
		//Convert array to list
		LinkedList<Integer> newData = new LinkedList<Integer>();
		for(int i : newArray){
			newData.add(i);
		}
		synchronized(assistLock){
			synchronized(profLock){
				//Add new data
				data.add(newData);
				printMessage("New data array received.");
				finished = true;
				//Notify professor that the array has arrived
				profLock.notifyAll();
				//Notifiy waiting assistants
				assistLock.notifyAll();
			}
		}
	}
	/*
	 * Notify end of data collection to telescopes
	 * The telescopes will notify their server, and it will shut down
	 * Wait until all the servers are close before ending the method
	 */
	public void closeSockets(){
		for(int i = 0; i < tsSockets.length; i++){
			DataOutputStream toTscope;
			try {
				//Send message to telescope
				toTscope = new DataOutputStream(tsSockets[i].getOutputStream());
				toTscope.writeBytes(Messages.CLOSE_TELESCOPE + "\n");
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException e){
				/*
				 * if objects are null, connection was already closed
				 * (or it was never open to begin with)
				 */
			}
		}
		while(checkAliveServers()){
			//busy waiting
		}
		printMessage("Experiment ended with " + succCounter + " successful observations.");
	}
	//Check if servers are alive
	private boolean checkAliveServers(){
		boolean result = false;
		for(int i = 0; i < serv.length; i++){
			try {
				result = result || serv[i].isAlive();
			} catch (NullPointerException e){
				/*
				 * If serv[i] is null, the server is surely already down
				 */
			}
		}
		return result;
	}
	private void getNewArray() {
		/*
		 * Ask server to retrieve the array from the telescope
		 * The server will give the new array to Professor using the method setNewArray
		 */
		printMessage("Asking Telescope" + bestTelescope + " to send data...");
		serv[bestTelescope].retrieveArray();
	}
	//Set up connection to telescopes
	private void setTelescopesSockets(){
		//if socket doesn't exist or the connection is closed, create new socket
		for(int i = 0; i < tsSockets.length; i++){
			connectTscope(i);
		}
	}
	//Operations to establish connection between professor and telescope
	private void connectTscope(int name){
		if(tsSockets[name] == null || tsSockets[name].isClosed()){
			try {
				tsSockets[name] = new Socket("localhost", RunTelescopes.ports[name]);
				System.out.println("Got a connection on port: " + RunTelescopes.ports[name]);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	//Ask telescopes to generate data
	private void askGenData(){
		try {
			for(int i = 0; i < tsSockets.length; i++){
				DataOutputStream toTelescope = new DataOutputStream(tsSockets[i].getOutputStream());
				toTelescope.writeBytes(Messages.GENERATE_DATA + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/*
	 * Start servers responsible to collect data
	 * if they are not already running
	 */
	private void startServers(){
		for(int i = 0; i < tsSockets.length; i++){
			if(serv[i] == null || !serv[i].isAlive()){
				serv[i] = new ProfTsServer(tsSockets[i], i);
				serv[i].start();
			}
		}
	}
	@Override
	public int getJob() throws RemoteException {
		//printMessage("An Assistant is requesting job...");
		synchronized(assistLock){
			int value = -1;
			//Check if there is aborted data to be elaborated
			if(!abortedData.isEmpty()){
				printMessage("Giving a previously aborted job to Assistant");
				value = abortedData.pop();
				return value;
			}
			//Guarded block
			while(currData.isEmpty() && data.isEmpty()){
				//There is no more data - wait
				try {
					printMessage("No data, Assistant will wait...");
					assistLock.wait();
				} catch (InterruptedException e1) {e1.printStackTrace();}
			}
			//Check if data is available and retrieve it
			if(currData.isEmpty()){
				//Current data is empty, get a new list of data
				//printMessage("Popping new list from data.");
				currData = data.pop();
			}
			//printMessage("Popping new value from current data.");
			value = currData.pop();
			printMessage("Sending data " + value + " to an assistant.");
			return value;
		}
	}
	@Override
	public void sendResult(int divisor) throws RemoteException {
		//printMessage("Received value " + divisor + " from an assistant.");
		if(divisor >= KESSLER_CONSTANT){
			//An assistant found a good divisor! Increment counter
			//printMessage("The value " + divisor + " satisfies the Kessler's Disequation!");
			succCounter++;
		} else {
			//printMessage("The value does not satisfy the Kessler's Disequation!");
		}
	}
	@Override
	public void sendAbort(int data) throws RemoteException {
		synchronized(assistLock){
			abortedData.add(data);
		}
	}
	//Print message
	private void printMessage(String message){
		System.out.println("[Professor]: " + message);
	}
	//Sleep for debug
	/*private static void debugSleep(long time){
		if(time > 0){
			try {
				Thread.sleep(time);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}*/
}