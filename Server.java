
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Server class receives a file sent by a client. The Server receives the file using UDP. 
 * But to a reliable data transfer; congestion control, error detection and retransmission are 
 * implemented in the client. The server will send an acknowledgement if it receives a 
 * packet. The server does in-order packet storing in the file. 
 * 
 * @author Ajeeth Kannan
 */
public class Server
{
	
	private String args[]; // command line arguments.
	private String fileName = ""; // file name.
	
	private boolean isQuiet = false; // isQuiet option in command line.
	
	private long fileLength; // length of the file. 
	private int port; // port# specified in command line.
	private int segment; // number of packets to be received.
	private int neededSegment = 0; //  Next needed segment by the receiver.
	private int clientPort; // port# of the client.
	
	private BufferedWriter bufferedWriter;
	private File file;
	
	private byte receiveInfo[] = new byte[1432]; // receive message byte array.
	private byte response[]; // sending response to the client.
	private byte packet[][]; // total packets to be sent.
	private InetAddress clientIP; // IP address if the client.
	
	// window buffer.
	private ArrayList<Window> window = new ArrayList<Window>();
	
	private DatagramSocket datagramSocket;
	
	// constructor.
	public Server(String args[])
	{
		this.args = args;
		getArguments();
		
		try
		{
			datagramSocket = new DatagramSocket(8880);
		}
		catch(Exception e)
		{
			
		}
		
	}
	
	/**
	 * The class is like a buffer. It stores the entire packet received from the client. 
	 * 
	 * @author Ajeeth Kannan
	 */
	private class Window
	{
		int packetName;
		byte[] packet;
		
		public Window(int packetName)
		{
			this.packetName = packetName;
			this.packet = new byte[1400];
		}
		
		public Window(byte[] packet)
		{
			this.packet = packet;
			this.packetName = Integer.parseInt( (new String(Arrays.copyOfRange(this.packet, 0, 16) ) ).trim() );
		}
		
		/**
		 * The method overrides equal method
		 */
		public boolean equals(Object object)
		{
		
			boolean returnValue = false;
	
			Window needToCheck = (Window) object;
			
			if( this.packetName == needToCheck.packetName) 
			{
				returnValue = true;
			}
			
			return returnValue;
		}
		
	}
	
	/**
	 * 
	 * It checks the error in a packet. And stores the packet in the 
	 * file (Also manages In order fashion).
	 * 
	 * @author Ajeeth Kannan
	 */
	private class ProcessPacket extends Thread
	{
		
		/**
		 * run() method called when thread is started.
		 */
		public void run()
		{
			while( !window.isEmpty() )
			{
				Window object = new Window(neededSegment);
				if( window.contains(object) )
				{
					
					for( int i = window.size()-1; i>=0; i-- )
					{
						try
						{
								if( window.get(i) != null )
								{
									if( window.get(i).packetName == neededSegment )
									{
							
									// if error in the packet ask to resend.
									if( checkSum( window.get(i).packet , neededSegment) )
									{
										window.remove(i);
										neededSegment++;
									}
									else
									{
										window.remove(i);
										break;
									}
								}
							}
						}
						catch(NullPointerException e) {
							
						}
					}
				}
				else
				{	
					for( int i = window.size()-1; i>=0; i-- )
					{
						if( window.get(i) == null )
						{
							window.remove(i);
						}
					}
					break;
				}
				try
				{
					// wait for the next packet. If no process reply to the client.
					Thread.sleep(10);
				}
				catch(Exception e)
				{
					
				}
			}
			
			try
			{
	
				// Send acknowledgement.
				response = new String(neededSegment+ "").getBytes();
				if( isQuiet == false )
				{
					System.out.println("Acknowledgement sending : " + " " + neededSegment);
				}
				DatagramPacket sendInformation = new DatagramPacket(response, response.length, clientIP, clientPort);
				datagramSocket.send(sendInformation);
			}
			catch(Exception e)
			{
				
			}
			
		}
		
		// Checks for error in the packet using MD5.
		private boolean checkSum( byte[] windowByte , int segmentID)
		{
		
			boolean returnValue = false;
			
			int segmentInt = Integer.parseInt( new String( Arrays.copyOfRange(windowByte, 0,16) ).trim() );
			
			byte trimByte[] = null;
			
			if( segmentInt != segment - 1 )
			{
			    trimByte = windowByte;
			}
			else
			{
				int size = (int) ( fileLength - ( 1400 * ( segment - 1 ) ) );
				trimByte = Arrays.copyOfRange(windowByte, 0, size + 32);
			}
			
			byte tempPacket[] = new byte[trimByte.length];
			
			for( int j = 0; j < 16; j++ )
			{
				tempPacket[j] = trimByte[j];
			}
			for( int j = 32; j < trimByte.length; j++  )
			{
				tempPacket[j] = trimByte[j];
			}
			
			try
			{
				
				byte hashFunction[] = Arrays.copyOfRange(trimByte, 16, 32);
		
				MessageDigest digest = MessageDigest.getInstance("md5");
				digest.update(tempPacket);
				byte dataHashFunction[] = digest.digest();
				
				if( ( new String(hashFunction) ).equals( new String(dataHashFunction) ) )
				{
					if( isQuiet == false )
					{
						System.out.println("Storing packet# " + neededSegment + " in the file");
					}
					// store the packet in the file.
					for( int i = 0; i < trimByte.length; i++ )
					{
						packet[neededSegment][i] = trimByte[i];
					}
					returnValue = true;
				}
				else
				{
					if( isQuiet == false )
					{
						System.out.println("Packet# " + neededSegment + " has an error");
					}
				}
				
			}
			catch(Exception e)
			{
				
			}
			
			return returnValue;
		}
		
		
	}
	
	/**
	 * Receives message from the client. Stores the packet in the window and starts 
	 * ProcessPacket if not started. 
	 * 
	 */
	public void run()
	{
		try
		{
			
			// First message from the client.
			DatagramPacket receiveInformation = new DatagramPacket( receiveInfo , receiveInfo.length );
			datagramSocket.receive(receiveInformation);
			clientIP = receiveInformation.getAddress();
			clientPort = receiveInformation.getPort();
			String info[] = ( new String( receiveInfo ).trim() ).split(" ");
			fileName = "0"+info[0];
			fileLength = Long.parseLong(info[1]);
			segment = Integer.parseInt(info[2]);
			
			setUpPacket();
			
			// replay for the first message.
			response = (neededSegment+"").getBytes();
			DatagramPacket sendInformation = new DatagramPacket(response, response.length, clientIP, clientPort);
			datagramSocket.send(sendInformation);
			
			ProcessPacket processPacket = new ProcessPacket();
			
			// Gets the packet.
			while( true )
			{
				byte receiveByte[] = new byte[1432];
				DatagramPacket receivePacket = new DatagramPacket( receiveByte , receiveByte.length );
				datagramSocket.receive(receivePacket);
				
				String message = new String(Arrays.copyOfRange(receiveByte, 0, 16));
				message = message.trim();
				if( message.equals("-1") )
				{
					break;
				}
				else
				{
					if( isQuiet == false )
					{
						System.out.println("Received packet : " + " " + message);
					}
					// adds in the window.
					window.add(new Window(receiveByte));
					// starts processPAcket if not started.
					if( !processPacket.isAlive() )
					{
						processPacket = new ProcessPacket();
						processPacket.start();
					}
				}
					
				
			}
			
			file = new File(fileName);
			bufferedWriter = new BufferedWriter(new FileWriter(file));
			for( int i = 0; i < segment; i++ )
			{
				
				bufferedWriter.flush();
				bufferedWriter.write(new String(packet[i],32,packet[i].length-32));
			}
			
			bufferedWriter.flush();
			
		}
		catch(Exception e)
		{
			
		}
		
		hashCheck();
		
	}
	
	//setup the packet. Size of the packet is 288 bytes. 
	private void setUpPacket()
	{
		int mod = (int)fileLength % 1400;
		
		packet = new byte[segment][];
		
		for( int i = 0; i < segment; i++ )
		{
			if( i != segment-1 || mod == 0 )
			{
				packet[i] = new byte[1400+32];
			}
			else
			{
				packet[i] = new byte[mod+32];
			}
		}
	}
	
	// manage the command line arguments.
	private void getArguments()
	{
		for(int i = 0; i < args.length; i++)
		{
			if(args[i].equals("-q") || args[i].equals("--quiet"))
			{
				isQuiet = true;
			}
		}
		try
		{
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
