import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

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
                files.getFileData(filename).incrementAcks();
            }
        }
    }

    /**
     * method to handle parsing and interpretation
     * of client's message
     * @param connection
     */
    private void handleClient(Connection connection, String clientID){

        String message = null;
        ArrayList<String> dstores = new ArrayList<>();

        try {
            message = connection.readLine();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        while(message != null) {
            if (!mainController.checkEnoughDstores()) {
                connection.write("ERROR_NOT_ENOUGH_DSTORES");
                return;
            }

            mainController.clientConnections.put(clientID, connection);

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
                        connection.write(e.getMessage());
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            try {
                message = connection.readLine();
            } catch (Exception e) {
                return;
            }
        }
    }
}
