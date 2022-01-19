import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Client
{
	
	private static Socket socket;
	
	private static final String REGEX_IP = "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
	private static final Pattern IPv4_PATTERN = Pattern.compile(REGEX_IP);
	
	public static boolean IPvalidation(final String ip) {
		return IPv4_PATTERN.matcher(ip).matches();
	}
	
	public static boolean portValidation(final String port) {
		try {
			if(Integer.parseInt(port) < 5002 || Integer.parseInt(port) > 5049) return false;
			else return true;
		} catch (NumberFormatException e) {
			System.out.println("Valeur entrée n'est pas un nombre.");
			return false;
		}
	}
	
	public static void main(String[] args) throws Exception
	{
		//Addresse et port du server
		Scanner input = new Scanner(System.in);
		// L'address IP du server est setup
		System.out.println("CLIENT: Entrer l'adresse IP du serveur");
		String serverAddress = input.nextLine();
		while(!IPvalidation(serverAddress)) {
			System.out.println("Adresse IP n'est pas valide. Réessayez");
			input = new Scanner(System.in);
			serverAddress = input.nextLine();
		}
	
		// Le port du server est setup
		System.out.println("Entrer le port d'ecoute du serveur");
		String clientPortInput = input.nextLine();
		while(!portValidation(clientPortInput)) {
			System.out.println("Le port n'est pas valide. Veuillez choisir entre 5002 et 5049");
			input = new Scanner(System.in);
			clientPortInput = input.nextLine();
		}
		
		//Creation d'une nouvelle connexion avec le server
		int port = Integer.parseInt(clientPortInput);
		socket = new Socket(serverAddress, port);
		
		System.out.format("The 1server is running on %s:%d%n", serverAddress, port);
		
		//Creation d'un canal entrant pour recevoir les messages enoyer par le server
		DataInputStream in = new DataInputStream(socket.getInputStream());
		// Canal sortant pour ecrire des messages au serveur
		PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
		
		//Attente de la reception d'un message envoyer par le server sur le canal
		String helloMessageFromServer = in.readUTF();
		System.out.println(helloMessageFromServer);
		
		
		//Envoie des Info (Address et Port)
		out.println(serverAddress);
		out.println(Integer.toString(port));
		do {
			System.out.println("Veuillez entrer une commande: ");
			String userInput = "";
			userInput = input.nextLine();
			while(userInput.isBlank()) {
				userInput = input.nextLine();
			}
			String[] command = userInput.split(" ");
			
			switch(command[0]) {
			case "mkdir":
				out.println(userInput.toString());
				System.out.println(in.readUTF());
				break;
				
			case "delete":
				out.println(userInput.toString());
				System.out.println(in.readUTF());
				break;
				
			case "cd":
				out.println(userInput.toString());
				System.out.println(in.readUTF());
				break;
				
			case "ls":
				out.println(userInput.toString());
				String value;
				while(!(value = in.readUTF()).equals("-done-")) {
					System.out.println(value);
				}
				break;
				
			case "upload":
				out.println(userInput.toString());
				File fileToUpload = new File("" + command[1]);
				if(fileToUpload.isFile()) {
					DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
					FileInputStream fileStream = new FileInputStream(fileToUpload.toString());
					outputStream.writeLong(fileToUpload.length());
					int read;
					byte[] buffer = new byte[10240];
					while((read = fileStream.read(buffer)) > 0) {
						outputStream.write(buffer, 0, read);
					}
					fileStream.close();
					System.out.println(in.readUTF());
				} else System.out.println("Aucun fichier");
				break;
				
			case "download":
				out.println(userInput.toString());
				String message = in.readUTF();
				if (message.equals("downloading..")) {
					System.out.println(message);
					DataInputStream dataInput = new DataInputStream(socket.getInputStream());
					FileOutputStream fileOutput = new FileOutputStream("" + command[1]);
					byte[] buffer = new byte[10240];
					
					long remaining = dataInput.readLong();
					int read = 0;
					while((read = dataInput.read(buffer, 0, (int) Math.min(buffer.length, remaining)))> 0)
					{
						fileOutput.write(buffer, 0 , read);
						remaining -= read;
					}
					fileOutput.close();
					System.out.println(in.readUTF());
				} else System.out.println("Erreur: Fichier introuvable");
				break;
				
			default :
				System.out.println("La commande entrée est invalide");
				break;
			}
		} while(!socket.isClosed());
		

		//Fermeture de connexion avec server
		socket.close();
		input.close();
		
	}
}
