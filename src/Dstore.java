import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Dstore {
    int port;
    int cport;
    int timeout;
    String file_folder;

    Dstore(String[] args){
        this.port = Integer.parseInt(args[0]);
        this.cport = Integer.parseInt(args[1]);
        this.timeout = Integer.parseInt(args[2]);
        this.file_folder = args[3];
    }

    public static void main(String[] args) throws Exception {
        Dstore d = new Dstore(args);
        d.start();
    }

    public void start(){
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 4323);
            Connection controllerConnection = new Connection(socket);
            controllerConnection.write("dstore " + port);

            ServerSocket ss = new ServerSocket(port);
            Socket clientSocket = ss.accept();

            Connection clientConnection = new Connection(clientSocket);

            String commandString = clientConnection.readLine();

            while(commandString != null){
                if (commandString.startsWith("STORE")){
                    System.out.println("store entered");
                    String[] storeArguments = commandString.split(" ");
                    String fileName = storeArguments[1];
                    int fileSize = Integer.parseInt(storeArguments[2]);

                    clientConnection.write("ACK");

                    byte[] fileContents = new byte[fileSize];
                    clientConnection.readBytes(fileContents);

                    storeToFile(fileContents, fileName);

                    controllerConnection.write("STORE_ACK " + fileName);
                } else if (commandString.startsWith("LOAD_DATA")){
                    System.out.println("load entered");
                    String[] loadArguments = commandString.split(" ");
                    String fileName = loadArguments[1];

                    try {
                        loadFile(fileName, clientConnection);
                    } catch (Exception e){
                        break;
                    }
                }

                try {
                    commandString = clientConnection.readLine();
                } catch (Exception e){
                    break;
                }
            }

            clientConnection.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public int getPort(){
        return port;
    }

    public void storeToFile(byte[] contents, String folderName){
        try {
            File file = new File(System.getProperty("user.dir") + "/" + file_folder + "/" + folderName);

            file.getParentFile().mkdirs();

            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(contents);
            fos.flush();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void loadFile(String filename, Connection connection) throws IOException {
        try {
            File requestedFile = new File(file_folder + "/" + filename);
            InputStream in = new FileInputStream(requestedFile);
            byte[] fileContent = in.readAllBytes();
            connection.writeBytes(fileContent);
        } catch (Exception e){
            connection.close();
        }
    }
}
