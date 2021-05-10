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
            controllerConnection.write("dstore " + Integer.toString(port));

            for(;;){
                ServerSocket ss = new ServerSocket(port);
                Socket clientSocket = ss.accept();

                Connection clientConnection = new Connection(clientSocket);
                String commandString = clientConnection.readLine();

                if (commandString.startsWith("STORE")){
                    String[] storeArguments = commandString.split(" ");
                    String fileName = storeArguments[1];
                    int fileSize = Integer.parseInt(storeArguments[2]);
                    clientConnection.write("ACK");
                    byte[] fileContents = new byte[fileSize];
                    clientConnection.readBytes(fileContents);
                    storeToFile(fileContents, fileName, Integer.parseInt(storeArguments[2]));
                    controllerConnection.write("STORE_ACK " + fileName);
                }

                ss.close();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public int getPort(){
        return port;
    }

    public void storeToFile(byte[] contents, String fileName, int fileSize){

    }
}
