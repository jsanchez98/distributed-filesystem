import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

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
            System.out.println(identifier + " connected");
        } catch (Exception e) {
            e.printStackTrace();
        }

        String clientID = null;

        if (identifier != null && identifier.startsWith("dstore")) {
            mainController.getDstoreConnections().put(identifier, connection);
            if(!mainController.getIndex().containsKey(identifier)) {
                mainController.getIndex().put(identifier, new FileIndex());
            }
            // above needs to check that dstore doesn't already exist in the index

            handleDstore(connection, identifier);

        } else if (identifier != null && identifier.startsWith("client")){
            int numberOfConnections = mainController.clientConnections.size();
            clientID = "client " + numberOfConnections;

            handleClient(connection, clientID);
        }


        if(identifier != null && identifier.startsWith("dstore")) {
            try {
                mainController.getDstoreConnections().get(identifier).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mainController.getDstoreConnections().remove(identifier);
        }

        if(clientID != null && identifier.startsWith("client")){
            mainController.getClientConnections().remove(clientID);
        }
    }

    private void handleDstore(Connection connection, String identifier){
        String message;
        for(;;) {
            try {
                message = connection.readLine();
            } catch (Exception e) {
                return;
            }

            if (message.startsWith("STORE_ACK")) {
                String[] parts = message.split(" ");
                String filename = parts[1];

                FileIndex files = mainController.getIndex().get(identifier);
                files.getFileData(filename).setTrueStoreAck();
            }

            if(message.startsWith("REMOVE_ACK")){
                String[] parts = message.split(" ");
                String filename = parts[1];

                FileIndex files = mainController.getIndex().get(identifier);
                files.getFileData(filename).setTrueRemoveAck();
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
            System.out.println("RECEIVED " + message);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        while(message != null) {
            if (!mainController.checkEnoughDstores()) {
                clientConnection.write("ERROR_NOT_ENOUGH_DSTORES");
                return;
            }

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

            if (message.startsWith("LOAD") || message.startsWith("RELOAD")) {
                try{
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    try {
                        dstores = mainController.loadOperation(clientID, filename, dstores);
                    } catch (FileDoesNotExistException e){
                        clientConnection.write(e.getMessage());
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            if(message.startsWith("REMOVE")){
                try{
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    ConcurrentHashMap<String, FileIndex> index = mainController.getIndex();
                    int numberOfDstoresWithFile = 0;

                    for(String dstore : index.keySet()){
                        if(index.get(dstore).files.containsKey(filename)){
                            numberOfDstoresWithFile++;
                            Connection dstoreConnection = mainController
                                    .getDstoreConnections()
                                    .get(dstore);
                            dstoreConnection.write("REMOVE " + filename);
                        }
                    }

                    while(!checkAllRemoveAcks(filename, numberOfDstoresWithFile)){}

                    for(String dstore : index.keySet()){
                        if(index.get(dstore).files.containsKey(filename)){
                            index.get(dstore).files.get(filename).setFalseRemoveAck();
                        }
                    }

                    if(numberOfDstoresWithFile == 0){
                        clientConnection.write("ERROR_FILE_DOES_NOT_EXIST");
                        return;
                    }

                    System.out.println("remove complete");

                    mainController.removeOperation(filename);

                    clientConnection.write("REMOVE_COMPLETE");

                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            try {
                message = clientConnection.readLine();
            } catch (Exception e) {
                return;
            }
        }
    }

    public boolean checkAllRemoveAcks(String filename, int dstores)
            throws IOException{

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
