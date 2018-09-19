
import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.ArrayList;

/**
 * 
 * Client class sends a file specified in the command line to a server address. The client 
 * send the file using UDP. But to a reliable data transfer; congestion control, error 
 * detection and retransmission are implemented.
 * 
 * @author Ajeeth Kannan
 */
public class Client 
{
	private String args[]; // command line arguments.
	private String fileName = ""; // file name.
	private String sAddress = ""; // server IP address.
	private String state = "Slow start"; // state of the congestion control window.
	
	private InetAddress serverAddess; // server IP address.
	
	private boolean isQuiet = false; // isQuiet option in command line.
	
	private int timeOut = 1000; // time out specified in command line.
	private int port; // port# specified in command line.
	private int segment; // number of packets to be sent to the receiver.
	private int neededSegment; // Next needed segment by the receiver.
	private int lastAck; // last acknowledgement received.
	private int count; // temporary variable.
	private int windowSize = 1; // window size while sending.
	private int threshold  = 512; // threshold while sending.
	
	private long fileLength; // length of the file. 
	
	// unsent packets to the receiver.
	private ArrayList<Integer> needToSendList = new ArrayList<Integer>(); 
	// window buffer.
	private ArrayList<SendPacket> window = new ArrayList<SendPacket>();
	// temporary buffer which holds just the packet numbers that are in the window.
	private ArrayList<Integer> windowInt = new ArrayList<Integer>();
	
	private byte receiveInfo[] = new byte[256]; // receive message.
	private byte sendInfo[]; // send message.
	private byte packet[][]; // total packets to be sent.
	
	private DatagramSocket datagramSocket; 
	private FileInputStream fileInputStream;
	private File file;
	
	// constructor.
	public Client(String args[])
	{
		this.args = args;
		getArguments(); // manages the arguments entered bu the user.
		
		try
		{
			serverAddess = InetAddress.getByName(sAddress);
			file = new File(fileName);
			fileInputStream = new FileInputStream(file);
			fileLength = file.length();
			datagramSocket = new DatagramSocket(8888);
		}
		catch(Exception e)
		{
		
		}
		
	}
	
	/**
	 * The timer class manages the timeout option for reliable data transfer.
	 * Once the timeout is detected the thread will clear the window except the
	 * first packet in the window. Now the window size will be 1.
	 * 
	 * @author Ajeeth Kannan
	 */
	private class Timer extends Thread
	{
		/**
		 * overridden run method from thread class. 
		 */
		public void run()
		{
			try
			{
				while( true )
				{
					Thread.sleep(timeOut);
					if( !window.isEmpty() )
					{
						if( isQuiet == false ) {
							System.out.println("It is timeout");
						}
						// sendPacket has the first element of the window.
						// creating a thread.
						SendPacket sendPacket =  new SendPacket( window.get(0).i );
						windowSize = 1; // window size is 1.
						neededSegment = sendPacket.i;
						window.clear(); // window is cleared.
						windowInt.clear(); 
						window.add(sendPacket); // sendPacket is added to the window.
						windowInt.add(neededSegment);
						window.get(0).start(); // thread is started.
					}
				}
			}
			catch(Exception e)
			{
				
			}
		}
		
	}
	
	/**
	 * SendPacket is a thread which sends a packet that is need to be sent to 
	 * the receiver. 
	 * 
	 * @author Ajeeth Kannan
	 */
	int times = 0;
	private class SendPacket extends Thread
	{
		// Packet ID.
		private int i;
		
		
		
		// constructor.
		public SendPacket(  int i )
		{
			this.i = i;
		}
		
		// Called when the thread is starting.
		public void run()
		{
			try
			{
				
				if( isQuiet == false ) {
					System.out.println("Sending" + " " + "packet# : " + ( i )  + " " + "Window size : " + windowSize 
							+ " " + "State : " + state);
				}
				// sends the packet to the receiver.
				DatagramPacket sendPacket = new DatagramPacket(packet[i], packet[i].length, serverAddess, 8880);
				datagramSocket.send(sendPacket);
				
			}
			catch(Exception e)
			{
				
			}
		}
		
		/**
		 * Once a thread is started the main thread will call startAgain to send the 
		 * packet again.
		 */
		public void startAgain()
		{
			try
			{
				
				if( isQuiet == false ) {
					System.out.println("Sending" + " " + "packet# : " + ( i )  + " " + "Window size : " + windowSize 
							+ " " + "State : " + state);
				}
				// sends the packet to the receiver.
				DatagramPacket sendPacket = new DatagramPacket(packet[i], packet[i].length, serverAddess, 8880);
				datagramSocket.send(sendPacket);
				
			}
			catch(Exception e)
			{
				
			}
		}
		
		/**
		 * Returns true if the senmentVal is greater than i. 
		 * Returns false if the segmentVal is less than i. 
		 *
		 * @param segmantVal needed packet message sent from the receiver. 
		 * @return true if the segmentVal is greater than i.
		 */
		public boolean stopThread(int segmantVal)
		{
			if(segmantVal > i)
			{
				return true;
			}
			else
			{
				return false;
			}
		}
		
	}
	
	@SuppressWarnings("deprecation")
	/**
	 * Actual client functions are happening in this method. Sending a packet,
	 * making a packet, retransmission, window size and dropped packet are hannded 
	 * in this method.
	 */
	public void run()
	{
		
		setUpPacket();
		
		try
		{
			// initial handshake.
			sendInfo = (fileName + " " + fileLength + " " + segment).getBytes();
			DatagramPacket sendInformation = new DatagramPacket(sendInfo, sendInfo.length, serverAddess, 8880);
			datagramSocket.send(sendInformation);
			DatagramPacket receiveInformation = new DatagramPacket(receiveInfo, receiveInfo.length);
			datagramSocket.receive(receiveInformation);
			neededSegment = Integer.parseInt( new String( receiveInfo ).trim() );
			
			// runs till the acknowledgement for the last packet. 
			while(neededSegment != segment)
			{
				// initialize timer.
				Timer timer = new Timer();
				
				// times to run the loop.
				int times = windowSize - window.size();
				
				for( int i = 0; i < times; i++ )
				{
					
					if( !windowInt.contains(neededSegment+i) && neededSegment+i < segment )
					{
						SendPacket sendPacket = new SendPacket(neededSegment + i);
						sendPacket.start();
						window.add( sendPacket );
						windowInt.add(neededSegment + i);
					}
					else if(windowInt.contains(neededSegment+i))
					{	
						for( int k = 0; k < window.size(); k++ )
						{
							if( window.get(k).i == neededSegment + i )
							{
								if( window.get(k).i == neededSegment  )
								{
									window.get(k).startAgain();
									break;
								}
							}
						}
					}
					else
					{
						
					}
				}
				
				// starts timer.
				timer.start();
				
				// receives acknowledgement.
				DatagramPacket receiveAck = new DatagramPacket(receiveInfo, receiveInfo.length);
				datagramSocket.receive(receiveAck);
				
				// if timer is still alive after the acknowledgement, stop the timer.
				if( timer.isAlive() )
				{
					timer.stop();
				}
				
				int messageReceived = Integer.parseInt( (new String(receiveInfo)).trim() );
				if( isQuiet == false )
				{
					System.out.println("Acknowledgement Received:" + " " + messageReceived);
				}
				int iterSize = window.size();
				
				for( int i = iterSize-1; i >= 0; i-- )
				{
					
					// if more than third duplicate acknowledgement, make window size to 1.
					if( i == iterSize-1 )
					{
						if( lastAck == messageReceived  )
						{
							count++;
							
						}
						else
						{
							count = 0;
						}

						if(count >= 2)
						{
							windowSize = 1;
							timer.stop();
							windowSize = 1;
							window.clear();
							windowInt.clear();
							if( isQuiet == false ) {
								System.out.println(">= 3 duplicate acknowledgement");
								System.out.println("Window size is going to be 1");
								state = "Slow start";
							}
							break;
						}
					}
					
					// stop the thread for received acknowledgement.
					if( window.get(i).stopThread(messageReceived) )
					{
						
						if( !window.isEmpty() && !windowInt.isEmpty() )
						{
							window.remove(i);
							windowInt.remove(i);
						}
					}
					
					// increase the window size according to the previous window value.
					if( i == 0 )
					{
						if(  windowSize > threshold )
						{
							state = "Congestion avoidance";
							windowSize = windowSize + 1;
						}
						else
						{
							state = "Slow start";
							windowSize = windowSize + windowSize;
						}
					}
					
				}
				
				neededSegment = messageReceived;
				lastAck = messageReceived;
				
			}
			
			// send final message.
			sendInfo = ("-1").getBytes();
			DatagramPacket finalInformation = new DatagramPacket(sendInfo, sendInfo.length, serverAddess, 8880);
			datagramSocket.send(finalInformation);
			
		}
		catch(Exception e)
		{
			
		}
		
		hashCheck();
		
	}
	
    /**
     * setup the packet. Size of the packet is 288 bytes. 
     *  0 - 15  for segment number.
     * 16 - 31  for checksum.
     * 32 - 388 for data.
     */
	private void setUpPacket()
	{
		int mod = (int)fileLength % 1400;
		segment = (int)fileLength / 1400;
		if( mod == 0 )
		{
			packet = new byte[segment][];
		}
		else
		{
			segment = segment + 1;
			packet = new byte[segment][];
		}
		for( int i = 0; i < segment; i++ )
		{
			int temp;
			if( i != segment-1 || mod == 0 )
			{
				temp = 1400;
				packet[i] = new byte[1400+32];
			}
			else
			{
				temp = mod;
				packet[i] = new byte[mod+32];
			}
			try
			{
				byte tempByte[] = new byte[temp]; 
				fileInputStream.read(tempByte, 0, temp);
				for( int j = 0; j < tempByte.length; j++ )
				{
					packet[i][j+32] = tempByte[j];
				}
				
				byte segmentNumber[] = (i + "").getBytes();
				for( int j = 0; j < segmentNumber.length; j++ )
				{
					packet[i][j] = segmentNumber[j];
				}
				
				// adds checksum.
				MessageDigest digest = MessageDigest.getInstance("md5");
				digest.update(packet[i]);
				byte hashFunction[] = digest.digest();
				for( int j = 16; j < 16 + hashFunction.length; j++ )
				{
					packet[i][j] = hashFunction[j-16];
				}
				
			}
			catch(Exception e)
			{
				
			}
			
			needToSendList.add(i);
		}
	}
	
	// manage the command line arguments.
	private void getArguments()
	{
		try
		{
			for(int i = 0; i < args.length; i++)
			{
				if(args[i].equals("-q") || args[i].equals("--quiet"))
				{
					isQuiet = true;
				}
				if(args[i].equals("-t") || args[i].equals("--timeout"))
				{
					timeOut = Integer.parseInt( args[i+1] );
				}
				if(args[i].equals("-f") || args[i].equals("--file"))
				{
					fileName = args[i+1];
				}
				if(args[i].contains("."))
				{
					sAddress = args[i];
				}
				if(args[i].equalsIgnoreCase("localhost"))
				{
					sAddress = "127.0.0.1";
				}
			}
			if(fileName.equals("") || sAddress.equals(""))
			{
				throw new IllegalArgumentException("Filename or Serveraddress is empty");
			}
			port = Integer.parseInt(args[args.length-1]);
		}
		catch(NumberFormatException e)
		{
			throw new NumberFormatException("Port number should be an integer");
		}
	}
	
	// check the hash function of the file.
	void hashCheck() 
	{
		try
		{
			MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
			FileInputStream fileInputStream = new FileInputStream(fileName);
			byte[] dataBytes = new byte[1024];
			
			int read = 0; 
			
			while (( read = fileInputStream.read(dataBytes)) != -1) 
			{
				messageDigest.update(dataBytes, 0, read);
			};

			byte[] messageDigestbytes = messageDigest.digest();
	   
			StringBuffer stringBuffer = new StringBuffer("");
			for (int i = 0; i < messageDigestbytes.length; i++) {
				stringBuffer.append(Integer.toString((messageDigestbytes[i] & 0xff) + 0x100, 16).substring(1));
			}
	    
			System.out.println("Hash Function:" + stringBuffer.toString());
			fileInputStream.close();
	    
		}
		catch(Exception e)
		{
			
		}
	}
	
}
