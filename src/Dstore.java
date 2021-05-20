import java.io.*;
import java.net.*;

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

            for(;;){
                String commandString = clientConnection.readLine();

                if (commandString.startsWith("STORE")){
                    String[] storeArguments = commandString.split(" ");
                    String fileName = storeArguments[1];
                    int fileSize = Integer.parseInt(storeArguments[2]);

                    clientConnection.write("ACK");

                    byte[] fileContents = new byte[fileSize];
                    clientConnection.readBytes(fileContents);

                    storeToFile(fileContents, fileName);

                    controllerConnection.write("STORE_ACK " + fileName);
                } else if (commandString.startsWith("LOAD_DATA")){
                    String[] loadArguments = commandString.split(" ");
                    String fileName = loadArguments[1];

                    loadFile(fileName, clientConnection);
                }

                clientConnection.close();
                ss.close();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public int getPort(){
        return port;
    }

    public void storeToFile(byte[] contents, String folderName){
        try {
            System.out.println(System.getProperty("user.dir"));
            File file = new File(System.getProperty("user.dir") + "/" + "Dstore" + port + "/" + folderName);
            System.out.println(file.getAbsolutePath());

            file.getParentFile().mkdirs();

            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(contents);
            fos.flush();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void loadFile(String filename, Connection connection){
        try {
            File requestedFile = new File(filename);
            InputStream in = new FileInputStream(requestedFile);
            byte[] fileContent = in.readAllBytes();

            connection.writeBytes(fileContent);
        } catch (Exception e){ e.printStackTrace(); }


    }
}
