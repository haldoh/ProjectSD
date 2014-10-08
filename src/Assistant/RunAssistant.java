package Assistant;

public class RunAssistant{
	//main
	public static void main(String args[]){
		//Path of Professor
		String path = "//localhost/Professor";
		System.out.println("Assistant is running.");
		System.out.println("Type \"end\" to end the program when the current computation is done.");
		System.out.println("Type \"abort\" to end the program without waiting for the computation to end.\n");
		//Start processing handler
		AssistantCalc calc = new AssistantCalc(path);
		calc.start();
		//Start input handler
		AssistantInput input = new AssistantInput(calc);
		input.start();
	}
}
