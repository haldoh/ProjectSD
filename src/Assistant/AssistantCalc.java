package Assistant;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import Professor.ProfessorIf;

public class AssistantCalc extends Thread{
	//sleep debug
	private long sleepTime = 1500;
	//fields
	private ProfessorIf prof;
	private String path;
	private int data;
	private boolean run = true;
	
	//constructor
	public AssistantCalc(String path){
		this.path = path;
	}
	//Run
	public void run(){
		printMessage("Assistant Calculator started.");
		//Retrieve professor from rmiregistry
		getProfessor();
		while(run){
			data = -1;
			//Ask data to professor
			debugSleep(sleepTime);
			try {
				printMessage("Requesting job...");
				data = prof.getJob();
				printMessage("Received value: " + data);
				//Process
				debugSleep(sleepTime);
				int divisor = process();
				//Return result
				debugSleep(sleepTime);
				//printMessage("Bigger divisor found for number " + data + " is " + divisor + ".");
				prof.sendResult(divisor);
			} catch (RemoteException e) {
				printMessage("Professor is not responding. Try to recontact professor...");
				prof = null;
				getProfessor();
			}
		}
		printMessage("Execution ended.");
	}
	//End current calculation, then end execution
	public void endAssistant(){
		run = false;
		if(data == -1){
			printMessage("Execution ended.");
			System.exit(0);
		}
	}
	//Abort execution
	public void abortAssistant(){
		printMessage("Aborting...");
		try {
			prof.sendAbort(data);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NullPointerException e){
			printMessage("Professor isn't responding. Aborting without notification.");
		}
		data = -1;
		endAssistant();
	}
	//Get professor from rmiregistry
	private void getProfessor(){
		while(prof == null && run){
			try {
				Object obj = Naming.lookup(path);
				prof = (ProfessorIf) obj;
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				printMessage("RMIRegistry could not be contacted... Retrying in 10 seconds.");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			} catch (NotBoundException e) {
				printMessage("Professor is not bound to the selected path in registry... Retrying in 10 seconds.");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
	}
	//Process data
	private int process(){
		if(data >= 0){
			printMessage("Processing data... ");
			debugSleep(5000);
			//Valid data, try to find a divisor
			for(int i = 2; i < data; i++){
				if((data % i) == 0){
					printMessage("Done.");
					return (int)(data / i);
				}
			}
		}
		printMessage("Received invalid data from professor.");
		return -1;
	}
	//Sleep for debug
	private static void debugSleep(long time){
		if(time > 0){
			try {
				Thread.sleep(time);
			} catch (InterruptedException e) {/*Do nothing*/}
		}
	}
	//Print a message
	private void printMessage(String message){
		System.out.println("[AssistantCalc]: " + message);
	}
}
