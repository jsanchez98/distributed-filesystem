import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionHandler implements Runnable{
    Socket socket;
    Controller mainController;

    ConnectionHandler(Socket socket, Controller mainController){
        this.socket = socket;
        this.mainController = mainController;
    }

    public void run(){
        System.out.println("connected");
        Connection connection = new Connection(socket);
        String identifier = null;

        try {
            identifier = connection.readLine();
            ControllerLogger.getInstance().messageReceived(socket, identifier);

        } catch (Exception e) {
            e.printStackTrace();
        }

        String clientID = null;


        if (identifier != null && identifier.startsWith("JOIN")) {
            String[] parts = identifier.split(" ");
            String port = parts[1];
            mainController.getDstoreConnections().put(port, connection);
            if(!mainController.getIndex().containsKey(port)) {
                mainController.getIndex().put(port, new FileIndex());
            }
            // above needs to check that dstore doesn't already exist in the index

            mainController.rebalancer.setNewDstoreTrue();
            handleDstore(connection, port);

        } else if (identifier != null){
            int numberOfConnections = mainController.clientConnections.size();
            clientID = "client " + numberOfConnections;

            handleClient(connection, clientID);
        }


        if(identifier != null && identifier.startsWith("JOIN")) {
            String[] parts = identifier.split(" ");
            String port = parts[1];
            try {
                mainController.getDstoreConnections().get(port).close();
                mainController.getDstoreConnections().remove(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mainController.getDstoreConnections().remove(identifier);
        }

        if(clientID != null){
            mainController.getClientConnections().remove(clientID);
        }
    }

    private void handleDstore(Connection connection, String identifier){
        String message;
        for(;;) {
            try {
                message = connection.readLine();
                ControllerLogger.getInstance().messageReceived(socket, message);


                if (message.startsWith("STORE_ACK")) {
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    FileIndex files = mainController.getIndex().get(identifier);
                    files.getFileData(filename).setTrueStoreAck();
                }

                if (message.startsWith("REMOVE_ACK")) {
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    FileIndex files = mainController.getIndex().get(identifier);
                    files.getFileData(filename).setTrueRemoveAck();
                }

                if (message.startsWith(Protocol.LIST_TOKEN)) {
                    mainController.rebalanceResponses.incrementAndGet();
                    mainController.dstoreListedFiles.put(identifier, new ArrayList<>());
                    String[] parts = message.split(" ");

                    for (int i = 1; i < parts.length; i++) {
                        mainController.dstoreListedFiles.get(identifier).add(parts[i]);
                    }
                    ControllerLogger.getInstance().log("dstorefiles " + mainController.dstoreListedFiles.toString());
                }

                if (message.startsWith(Protocol.REBALANCE_COMPLETE_TOKEN)) {
                    mainController.rebalanceCompleteResponses.incrementAndGet();
                }
            } catch (Exception e) {
                return;
            }
        }
    }

    /**
     * method to handle parsing and interpretation
     * of client's message
     * @param clientConnection
     */
    private void handleClient(Connection clientConnection, String clientID){

        String message = null;
        ArrayList<String> dstores = new ArrayList<>();

        try {
            message = clientConnection.readLine();
            ControllerLogger.getInstance().messageReceived(socket, message);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        while(message != null) {

            if (!mainController.checkEnoughDstores()) {
                clientConnection.write(Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN);
                ControllerLogger.getInstance().messageSent(socket, Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN);
                return;
            }

            while(mainController.rebalanceInProgress.get());

            mainController.clientConnections.put(clientID, clientConnection);

            if (message.startsWith("STORE")) {
                try {
                    String[] parts = message.split(" ");
                    String filename = parts[1];
                    int filesize = Integer.parseInt(parts[2]);

                    mainController.storeOperation(clientID, filename, filesize);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            else if (message.startsWith("LOAD") || message.startsWith("RELOAD")) {
                try{
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    String dstore = mainController.findDstore(filename, new ArrayList<>());

                    if(mainController.index.get(dstore).getFileData(filename).
                            getState().equals(State.store_in_progress)) {
                    } else {

                        try {
                            dstores = mainController.loadOperation(clientID, filename, dstores);
                        } catch (FileDoesNotExistException e) {
                            clientConnection.write(e.getMessage());
                            ControllerLogger.getInstance().messageSent(socket, e.getMessage());
                        }
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            else if(message.startsWith("REMOVE")){
                try{
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    String d = mainController.findDstore(filename, new ArrayList<>());

                    if(mainController.index.get(d).getFileData(filename).
                            getState().equals(State.store_in_progress)) {
                    } else {


                        ConcurrentHashMap<String, FileIndex> index = mainController.getIndex();
                        int numberOfDstoresWithFile = 0;

                        for (String dstore : index.keySet()) {
                            if (index.get(dstore).files.containsKey(filename)) {
                                numberOfDstoresWithFile++;
                                Connection dstoreConnection = mainController
                                        .getDstoreConnections()
                                        .get(dstore);
                                dstoreConnection.write(Protocol.REMOVE_TOKEN + " " + filename);
                                ControllerLogger.getInstance().messageSent(socket,
                                        Protocol.REMOVE_TOKEN + " " + filename);
                            }
                        }

                        long start = System.currentTimeMillis();
                        while (!checkAllRemoveAcks(filename, numberOfDstoresWithFile)) {
                            if (System.currentTimeMillis() - start > mainController.timeout) {
                                ControllerLogger.getInstance().log("Remove operation for " + filename + " timed out");
                                break;
                            }
                        }


                        for (String dstore : index.keySet()) {
                            if (index.get(dstore).files.containsKey(filename)) {
                                index.get(dstore).files.get(filename).setFalseRemoveAck();
                            }
                        }

                        if (numberOfDstoresWithFile == 0) {
                            clientConnection.write(Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
                            ControllerLogger.getInstance().messageSent(socket,
                                    Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
                            return;
                        }

                        System.out.println("remove complete");

                        mainController.removeOperation(filename);

                        clientConnection.write(Protocol.REMOVE_COMPLETE_TOKEN);
                        ControllerLogger.getInstance().messageSent(socket,
                                Protocol.REMOVE_COMPLETE_TOKEN);
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            else if(message.startsWith("LIST")){

                StringBuilder response = new StringBuilder();
                response.append("LIST");

                for(String fileName : mainController.listOfFiles){
                    response.append(" " + fileName);
                }

                clientConnection.write(response.toString());
                ControllerLogger.getInstance().messageSent(socket, response.toString());
            }

            else {ControllerLogger.getInstance().log("Malformed message received: " + message);}

            try {
                message = clientConnection.readLine();
                ControllerLogger.getInstance().messageReceived(socket, message);

            } catch (Exception e) {
                return;
            }
        }
    }

    public boolean checkAllRemoveAcks(String filename, int dstores){
        ConcurrentHashMap<String, FileIndex> index = mainController.getIndex();

        int ackCount = 0;

        for(String dstore : index.keySet()){
            if(index.get(dstore).files.containsKey(filename)) {
                FileData fileData = index.get(dstore).getFileData(filename);
                if (fileData.isRemoveAck()) {
                    ackCount++;
                }
            }
        }

        return ackCount == dstores;
    }
}
