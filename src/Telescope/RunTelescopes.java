package Telescope;

public class RunTelescopes {
	/*
	 * This class will instantiate the Telescopes
	 */
	//Telescopes' ports
	public static final int[] ports = {6710,6711,6712,6713,6714};
	//Telescopes ports to be used for token ring connections
	public static final int[] TRPorts = {6720,6721,6722,6723,6724};
	//Flag to enable Token Ring
	private static boolean tokenRingEnabled = true;
	public static void main(String[] args){
		//Create and launch telescopes
		for(int i = 0; i < ports.length; i++){
			Telescope ts = new Telescope(ports[i], i);
			ts.start();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	//Get the value of tokenRingEnabled
	public static boolean isTokenRingEnabled(){
		return tokenRingEnabled;
	}
}
