package Assistant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class AssistantInput extends Thread{
	//fields
	private AssistantCalc calc;
	private boolean run = true;
	BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
	//constructor
	public AssistantInput(AssistantCalc calc){
		this.calc = calc;
	}
	//run
	public void run(){
		while(run){
			//wait an input from user
			printMessage("Input handler started.");
			waitInput();
		}
	}
	//End assistant
	public void endAssistant(){
		run = false;
		try {
			inFromUser.close();
		}
		catch (IOException e){e.printStackTrace();}
		catch (NullPointerException e){e.printStackTrace();}
		printMessage("Execution ended.");
	}
	//Wait input from user
	private void waitInput(){
		try {
			//Read string from input
			String choiceStr = inFromUser.readLine();
			if(choiceStr.equals("end")){
				//End the assistant
				endAssistant();
				calc.endAssistant();
			} else if(choiceStr.equals("abort")){
				//Abort computation
				printMessage("Aborting...");
				calc.abortAssistant();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//Print a message
	private void printMessage(String message){
		System.out.println("[AssistantInput]: " + message);
	}
}
