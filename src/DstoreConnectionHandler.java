import java.io.IOException;
import java.net.*;

public class DstoreConnectionHandler implements Runnable {
    Socket clientSocket;
    Socket controllerSocket;
    Dstore dstore;

    DstoreConnectionHandler(Socket clientSocket,
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
            DstoreLogger.getInstance().messageReceived(clientSocket, commandString);

            if (commandString.startsWith("STORE") ||
                    commandString.startsWith(Protocol.REBALANCE_STORE_TOKEN)) {
                String[] storeArguments = commandString.split(" ");
                String fileName = storeArguments[1];
                int fileSize = Integer.parseInt(storeArguments[2]);

                clientConnection.write(Protocol.ACK_TOKEN);
                DstoreLogger.getInstance().messageSent(clientSocket,
                        Protocol.ACK_TOKEN);

                byte[] fileContents = clientConnection.read(fileSize);

                dstore.storeToFile(fileContents, fileName);

                if(commandString.startsWith("STORE")) {
                    controllerConnection.write(Protocol.STORE_ACK_TOKEN + " " + fileName);
                    DstoreLogger.getInstance().messageSent(clientSocket,
                            Protocol.STORE_ACK_TOKEN + " " + fileName);
                }
            }

            else if(commandString.startsWith("LOAD_DATA")) {
                String[] loadArguments = commandString.split(" ");
                String fileName = loadArguments[1];

                dstore.loadFile(fileName, clientConnection);
            }

            else { DstoreLogger.getInstance().log("Malformed message received: " + commandString); }

            clientConnection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
