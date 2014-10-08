package Professor;

public interface ProfessorIf extends java.rmi.Remote{
	
	public int getJob() throws java.rmi.RemoteException;
	
	public void sendResult(int divisor) throws java.rmi.RemoteException;
	
	public void sendAbort(int data) throws java.rmi.RemoteException;
}
