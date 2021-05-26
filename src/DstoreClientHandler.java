import java.io.IOException;
import java.net.*;

public class DstoreClientHandler implements Runnable {
    Socket clientSocket;
    Socket controllerSocket;
    Dstore dstore;

    DstoreClientHandler(Socket clientSocket,
                        Socket controllerSocket,
                        Dstore dstore){
        this.clientSocket = clientSocket;
        this.controllerSocket = controllerSocket;
        this.dstore = dstore;
    }

    @Override
    public void run() {
        try {
            Connection clientConnection = new Connection(clientSocket);
            Connection controllerConnection = new Connection(controllerSocket);

            String commandString = clientConnection.readLine();

            if (commandString.startsWith("STORE")) {
                System.out.println("store entered");
                String[] storeArguments = commandString.split(" ");
                String fileName = storeArguments[1];
                int fileSize = Integer.parseInt(storeArguments[2]);

                clientConnection.write("ACK");

                byte[] fileContents = new byte[fileSize];
                clientConnection.readBytes(fileContents);

                dstore.storeToFile(fileContents, fileName);

                controllerConnection.write("STORE_ACK " + fileName);
            } else if (commandString.startsWith("LOAD_DATA")) {
                System.out.println("load entered");
                String[] loadArguments = commandString.split(" ");
                String fileName = loadArguments[1];

                dstore.loadFile(fileName, clientConnection);
            }

            clientConnection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
