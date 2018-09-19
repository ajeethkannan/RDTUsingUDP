
/**
 * 
 * Initial class that calls client if it is -c in command line.
 * Calls server if it is -s in the command line.
 * 
 * @author Ajeeth Kannan
 */
public class fcntcp 
{
	// main function.
	public static void main(String args[])
	{
		if(args[0].equals("-s") || args[0].equals("--server"))
		{
			Server server = new Server(args);
			server.run();
		}
		else if(args[0].equals("-c") || args[0].equals("--client"))
		{
			Client client = new Client(args);
			client.run();
		}
		else
		{
			throw new IllegalArgumentException("first argument needs to be -c or -s");
		}
	}
}
