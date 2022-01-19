import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Server {
	
	
	/*
	 * Un thread qui se charge de traiter la demande de chaque client sur un socket particulier
	 */
	
	private static final String REGEX_IP = "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
	private static final Pattern IPv4_PATTERN = Pattern.compile(REGEX_IP);
	private static String currentPath = new File("").toPath().toAbsolutePath().toString() + "\\";
	private static Vector<String> directoryVector = new Vector <String>();
	
	private static class ClientHandler extends Thread {
		private Socket socket;
		private int clientNumber;
		
		
		private void clientInfo(String command, String address, String port) {
			DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd@HH:mm:ss");
			System.out.println( "[" + address + ":" + port + "//" + sdf.format(new Date()) + "]: " + command);
		}
		
		private boolean createFolder(String name) {
			return new File( currentPath + name).mkdir();
		}
		
		private boolean deleteFolder(String name) {
			try {Files.deleteIfExists(Paths.get(currentPath + name));
		}
        catch(NoSuchFileException e)
        {
            System.out.println("Ce dossier n'existe pas dans le repertoire courant.");
            return false;
        }
        catch(DirectoryNotEmptyException e)
        {
            System.out.println("Le repertoire n'est pas vide.");
            return false;
        }
        catch(IOException e)
        {
            System.out.println("Impossible d'effacer le dossier.");
            return false;
        }
			System.out.println("Dossier effacer.");
			return true;
		}
		
		private String changeDirectory(String name) {
			String backDirectory = "";
			String forwardDirectory = currentPath + name;
			
			if (name.equals("..")) {
				String lastPath = directoryVector.lastElement();
				directoryVector.remove(directoryVector.size() - 1);
				backDirectory = currentPath.substring(0, currentPath.length() - lastPath.length());
				currentPath = backDirectory;
				return "Vous êtes dans le dossier " + name;
			}
			else if(Paths.get(forwardDirectory).toFile().isDirectory()) {
				directoryVector.add(name + "\\");
				currentPath = forwardDirectory + "\\";
				return "Vous êtes dans le dossier " + name;
			}
			else {
				return "Erreur: Répertoire introuvable.";
			}
			
		}
		
		private void filesList(DataOutputStream o) {
				File file = new File(currentPath);
				File[] listOfFiles = file.listFiles();
				System.out.println(currentPath);
				try {					
					for(File f : listOfFiles) {
						if(f.isDirectory()) o.writeUTF("[Folder] " + f.getName());
						else if (f.isFile()) o.writeUTF("[File] " + f.getName());
					}
					if(listOfFiles.length == 0) o.writeUTF("Aucun  dossier ou fichier trouvé");
					o.writeUTF("-done-");
				} catch (IOException e) {
					System.out.println("Une erreur c'est produite");
				}	
		}
		
		private String uploadFile(String name)
		{
			try {
				DataInputStream dis = new DataInputStream(socket.getInputStream());
				FileOutputStream fos = new FileOutputStream(currentPath + name);
				byte[] buffer = new byte[10240];
				long remaining = dis.readLong();;
				int read = 0;
				while((read = dis.read(buffer, 0, (int)Math.min(buffer.length, remaining)))> 0)
				{
					fos.write(buffer, 0, read);
					remaining -= read;
				}
				fos.close();
				return "Le fichier " + name + "  à bien été téléversé";
			} catch (IOException e) {
				return "Erreur du upload";
				}
		}
		
		private void downloadFile(String name, DataOutputStream o) throws IOException {
			File file =  Paths.get(currentPath + name).toFile();
			if(file.isFile() || file.isDirectory()) {
				o.writeUTF("downloading..");
				DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
				FileInputStream fis = new FileInputStream(file.toString());
							
				dos.writeLong(file.length());
				int length;
				byte[] buffer = new byte[10240];
				while((length = fis.read(buffer)) > 0)
				{
					dos.write(buffer, 0 ,length);
				}
				fis.close();
				o.writeUTF("Le fichier " + name + " à bien été téléchargé");
				
			} else  o.writeUTF("Fichier inexistant");
		}
		
		@SuppressWarnings("unused")
		private void zipFile(String name,  DataOutputStream o) throws IOException {
			File file = Paths.get(currentPath + name).toFile();
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			ZipOutputStream  zos = new ZipOutputStream(dos);
			FileInputStream fis = new FileInputStream(file.toString());
			ZipEntry e = new ZipEntry(name);
			int length;
			byte[] buffer = new byte[10240];
			zos.putNextEntry(e);
			
			while((length = fis.read(buffer)) > 0)
			{
				zos.write(buffer, 0 ,length);
			}
			fis.close();
			o.writeUTF("Le fichier " + name + ".zip à bien été téléchargé");
			
		}
		
		public ClientHandler(Socket socket, int clientNumber)
		{
			this.socket = socket;
			this.clientNumber = clientNumber;
			System.out.println("New connection with clien# " + clientNumber + " at " + socket);
		}
		
		public void run()
		{
			try
			{
				//Creation d'un canal sortant pour envoyer des messages au client
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				
				// Envoie d'un message au client
				out.writeUTF("Hello from server - you are client#" + clientNumber);
				
				InputStreamReader in = new InputStreamReader(socket.getInputStream());
				BufferedReader bf = new BufferedReader(in);
				
				String inputReceived = "";
				String addressIP = bf.readLine();
				String portClient = bf.readLine();
				while(inputReceived != null) {
					inputReceived = bf.readLine();
					String[] command = inputReceived.split(" ");
					clientInfo(inputReceived, addressIP, portClient);
					
					switch(command[0]) {
					
					case "mkdir":
						try {
							if(createFolder(command[1])) out.writeUTF("Le dossier " + command[1] + " a été créé");
							else out.writeUTF("Erreur: dossier n'a pas été créé");
						} catch(IOException e) {
							System.out.println("Impossible de créer le dossier");
						}
						break;
						
					case "delete":
						if(deleteFolder(command[1])) out.writeUTF("Le dossier " + command[1] + " a été supprimé");
						else out.writeUTF("Erreur: dossier n'a pas été effacé");
						break;
						
					case "cd":
						try {
							String message = changeDirectory(command[1]);
							out.writeUTF(message);
						} catch (EOFException e) {
							out.writeUTF("Erreur dans le changement de répertoire");
						}
						break;
						
					case "ls":
						filesList(out);
						break;
						
					case "upload":
						String message = uploadFile(command[1]);
						out.writeUTF(message);
						break;
						
					case "download":
						try { downloadFile(command[1], out); }
						catch (IOException e) {
							out.writeUTF("Erreur du téléchargement");
						}
						break;
					}
				}
				
			} catch (IOException e)
			{
				System.out.println("Error Handling client#" + clientNumber + ": " + e);
			}
			finally
			{
				try
				{
					socket.close();
				} catch (IOException e)
				{
					System.out.println("Couldnt close a socket");
				}
				System.out.println("Connection with client#" + clientNumber + " closed");
			}
		}
	}
	
	
	private static ServerSocket listener;
	
	/*
	 * TODO: Application du server
	 */
	
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
		//Compteur incremente a chaque connexion d'un client au server
		int clientNumber = 0;
		
		//Address et port du server: 
		Scanner input = new Scanner(System.in);
		// L'address IP du server est setup
		System.out.println("SERVER: Entrer une adresse IP pour le serveur");
		String serverAddress = input.nextLine();
		while(!IPvalidation(serverAddress)) {
			System.out.println("Adresse IP n'est pas valide. Réessayez");
			input = new Scanner(System.in);
			serverAddress = input.nextLine();
		}
	
		// Le port du server est setup
		System.out.println("Entrer un port d'ecoute pour le serveur");
		String serverPortInput = input.nextLine();
		while(!portValidation(serverPortInput)) {
			System.out.println("Le port n'est pas valide. Veuillez choisir entre 5002 et 5049");
			input = new Scanner(System.in);
			serverPortInput = input.nextLine();
		}
		input.close();
		
		//Creation de la connexion pour communiquer avec les clients
		listener = new ServerSocket();
		listener.setReuseAddress(true);
		InetAddress serverIP = InetAddress.getByName(serverAddress);
		
		//Association de l'adresse et du port a la connexion
		int serverPort = Integer.parseInt(serverPortInput);
		listener.bind(new InetSocketAddress(serverIP, serverPort));
		
		System.out.format("The server is running on %s:%d%n", serverAddress, serverPort);
		
		try
		{
			/*
			 * A chaque fois qu'un nouveau client se connecte, on execute la fonction Run() 
			 * de l'objet ClientHandler.
			 */		
			while(true)
			{
				//Important:la fonction accept() est bloquante: attend qu'un prochain client se connect
				//Une nouvelle connection : on implement le compteur clientNumber
				new ClientHandler(listener.accept(), clientNumber++).start();
			}
		}
		finally
		{
			//Fermeture de la connexion
			listener.close();
		}
		
	
	}
	
	
}
