import java.net.*;

public class DstoreControllerHandler implements Runnable {
    private Socket controllerSocket;
    Dstore dstore;

    DstoreControllerHandler(Socket socket, Dstore dstore){
        this.controllerSocket = socket;
        this.dstore = dstore;
    }

    @Override
    public void run() {
        Connection controllerConnection = new Connection(controllerSocket);
        controllerConnection.write("JOIN " + dstore.port);
        DstoreLogger.getInstance().messageSent(controllerSocket,
                "dstore " + dstore.port);

        try {
            String message = controllerConnection.readLine();
            DstoreLogger.getInstance().messageReceived(controllerSocket, message);

            while(message != null){

                if(message.startsWith("REMOVE")){
                    String[] parts = message.split(" ");
                    String filename = parts[1];

                    boolean result = dstore.removeFile(filename);
                    if(result){
                        controllerConnection.write(Protocol.REMOVE_ACK_TOKEN + " " + filename);
                        DstoreLogger.getInstance().messageSent(controllerSocket,
                                Protocol.REMOVE_ACK_TOKEN + " " + filename);
                    } else {
                        controllerConnection.write(Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN + " " + filename);
                        DstoreLogger.getInstance().messageSent(controllerSocket,
                                Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN + " " + filename);
                    }
                }

                else if(message.startsWith(Protocol.REBALANCE_TOKEN)){
                    String[] parts = message.split(" ");
                    int numberOfFilesToSend = Integer.parseInt(parts[1]);
                    int k = 2;

                    for(int i = 0; i < numberOfFilesToSend; ++i){
                        String filename = (parts[k]);
                        k++;
                        int numberOfDstores = Integer.parseInt(parts[k]);
                        for(int j = 0; j < numberOfDstores; ++j){
                            k++;
                            int dstore = Integer.parseInt(parts[k]);

                            sendRebalanceToDstore(filename, dstore);
                        }
                    }

                    int numberOfFilesToRemove = Integer.parseInt(parts[++k]);

                    for(int i = 0; i < numberOfFilesToRemove; ++i){
                        dstore.removeFile(parts[k]);
                        k++;
                    }

                    controllerConnection.write(Protocol.REBALANCE_COMPLETE_TOKEN);
                }

                else if(message.startsWith(Protocol.LIST_TOKEN)){
                    StringBuilder file_list = new StringBuilder();
                    file_list.append(Protocol.LIST_TOKEN + " ");

                    for(String file : dstore.files.keySet()){
                        file_list.append(file + " ");
                    }

                    controllerConnection.write(file_list.toString());
                    DstoreLogger.getInstance().messageSent(controllerSocket,
                             file_list.toString());
                }

                else {DstoreLogger.getInstance().log("Malformed message received: " + message);}

                message = controllerConnection.readLine();
                DstoreLogger.getInstance().messageReceived(controllerSocket, message);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void sendRebalanceToDstore(String filename, int port){
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            Socket dsocket = new Socket(localhost, port);
            Connection dstoreConnection = new Connection(dsocket);
            int filesize = dstore.files.get(filename);
            dstoreConnection.write(Protocol.REBALANCE_STORE_TOKEN + " " + filename + " " + filesize);

            if(dstoreConnection.readLine().equals("ACK")){
                dstore.loadFile(filename, dstoreConnection);
                dstoreConnection.close();
            }
            dstoreConnection.close();
        } catch (Exception e){
            DstoreLogger.getInstance().log(e.getMessage());
        }
    }
}
