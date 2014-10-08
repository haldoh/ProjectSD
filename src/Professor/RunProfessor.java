package Professor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;

public class RunProfessor {
	
	public static void main(String[] args) {
		//path of the service
		String host = "localhost";
		String path = "//" + host + "/Professor";
		int port = 1099;
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		//Select port for rmiregistry
		System.out.print("\nSelect port for registry [default:"+port+"]: ");
		try {
			String portStr = inFromUser.readLine();
			try{
				port = Integer.parseInt(portStr);
			} catch (NumberFormatException e){
				//do nothing and leave the port as it is
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	    try {
	    	//launch registry
	    	java.rmi.registry.LocateRegistry.createRegistry(port);
	    	System.out.println("\nRegistry launched.");
	    	//get the Professor
	    	Professor prof = Professor.getProfessor();
	    	System.out.println("\nGot the Professor.");
	    	//bind professor to path
	    	Naming.rebind(path, prof);
	    	System.out.println("\nProfessor binded.");
	    	System.out.println("\nProfessor is up and running at: " + path);
	    	//Choose what to do until end
	    	System.out.println("\nExperiment started.");
	    	while(true){
	    		System.out.println("\nPress 1 to get data from the telescopes");
	    		System.out.println("\nPress 0 to terminate");
				String choiceStr = inFromUser.readLine();
				int choice = -1;
				try{
					choice = Integer.parseInt(choiceStr);
				} catch (NumberFormatException e){
					//do nothing and leave the choice as it is
				}
				switch(choice){
				case 0:
					//will exit
					prof.closeSockets();
					System.exit(0);
				case 1:
					//Try connection to telescopes
					prof.getTelescopesData();
					break;
				default:
					//will notify unknown input
					System.out.println("\nUnexpected choice.");
				}
	    	}
	    }
	    catch (RemoteException e) {
	    	e.printStackTrace();
	    }
	    catch (MalformedURLException e) {
	    	e.printStackTrace();
	    }
	    catch (IOException e) {
	    	e.printStackTrace();
	    }    	
	}
}
