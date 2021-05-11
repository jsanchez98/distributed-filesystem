import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.*;

public class Controller {
    static int cport;
    static int R;
    static int timeout;
    static int rebalancePeriod;
    static ConcurrentHashMap<String, FileIndex> index;
    static ConcurrentHashMap<String, Connection> clientConnections;
    static ConcurrentHashMap<String, Connection> dstoreConnections;

    /**
     * TODO: refactor initialisation of client and dstore connections
     * @param args
     */
    public static void main(String[] args){
        System.out.println("changed");
        cport = Integer.parseInt(args[0]);
        R = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        rebalancePeriod = Integer.parseInt(args[3]);

        clientConnections = new ConcurrentHashMap<>();
        dstoreConnections = new ConcurrentHashMap<>();
        index = new ConcurrentHashMap<>();

        /*
        for(int i = 0; i < R; i++){
            ConcurrentHashMap<String, Integer> file = new ConcurrentHashMap<>();
            file.put("filename", 3);
            index.put(Integer.toString(i), file);
        } */

        try{
            ServerSocket ss = new ServerSocket(cport);

            for(;;){
                System.out.println("Waiting for connection");
                Socket client = ss.accept();
                System.out.println("connected");

                Connection connection  = new Connection(client);
                String identifier = connection.readLine();

                if(!checkEnoughDstores()){
                    connection.write("ERROR_NOT_ENOUGH_DSTORES");
                    continue;
                }

                if(identifier.equals("client")) {
                    int numberOfConnections = clientConnections.size() + 1;
                    String clientID = "client " + numberOfConnections;
                    clientConnections.put(clientID, connection);

                    System.out.println("Client ID: " + clientID);

                    //byte[] bytes = new byte[1000]; int bytelen;
                    String input = connection.readLine();

                    String[] clientArgs = getClientArguments(input);

                    handleCommand(clientArgs, clientID);

                } else if (identifier.startsWith("dstore")){
                    dstoreConnections.put(identifier, connection);
                    index.put(identifier, new FileIndex());
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Method to perform client commands
     * @param clientArgs
     */
    public static void handleCommand(String[] clientArgs, String clientID){
        String command = clientArgs[0];

        if(command.equals("STORE")){
            try {
                String filename = clientArgs[1];
                int filesize = Integer.parseInt(clientArgs[2]);

                storeOperation(clientID, filename, filesize);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Method to choose R Dstores, update their index entries
     * with the new file, and write them to the client.
     * Sends "STORE_COMPLETE" to client if successful.
     * @param clientID client
     * @param filename filename
     * @param filesize filesize
     * @throws IOException for connection I/O
     */
    public static void storeOperation(String clientID, String filename, int filesize)
            throws IOException {

        if(checkFileExists(filename)){
            return;
        }

        Iterator<String> it = index.keySet().iterator();
        StringBuilder portString = new StringBuilder();

        portString.append("STORE_TO");

        String[] Rdstores = new String[R];

        for(int i = 0; i < R; i++){
            String dstoreI = it.next();
            String[] string = dstoreI.split(" ");
            String port = string[1];
            updateIndex(filename, filesize, port);
            Rdstores[i] = dstoreI;
            portString.append(" " + port);
        }

        portString.append("\n");

        clientConnections.get(clientID).write(portString.toString());

        System.out.println("R ports: " + portString);

        if(checkAllDstoreAcks(Rdstores, filename)){
            clientConnections.get(clientID).write("STORE_COMPLETE");
        }
    }

    /**
     * Checks that all connected Dstores that were selected by
     * the storeOperation method have responded with "STORE_ACK {filename}"
     * @param Rdstores
     * @param filename
     * @return
     * @throws IOException
     */
    public static boolean checkAllDstoreAcks(String[] Rdstores, String filename)
            throws IOException{

        int ackCount = 0;

        for(String dstorePort : Rdstores){
            Connection dstoreConnection = dstoreConnections.get(dstorePort);
            if(dstoreConnection.readLine().equals("STORE_ACK " + filename)){
                ackCount++;
            }
        }

        return ackCount == R;
    }

    public static boolean checkEnoughDstores(){
        ConcurrentHashMap.KeySetView<String, Connection> set =
                dstoreConnections.keySet();
        return set.size() >= R;
    }

    /**
     * looks through all the files in the inner file index hashmap
     *to see if any has the same name as the file to be added
     * @param filename filename
     * @return boolean true if filename found, false otherwise
     */
    public static boolean checkFileExists(String filename){
        for(String dstore : index.keySet()){
            ConcurrentHashMap<String, Integer> files = index.get(dstore).getFiles();
            if(files.containsKey(filename)){
                return true;
            }
        }
        return false;
    }

    /**
     * Method to extract arguments from bytes of client's
     * input stream
     * @param input
     * @return argumentList
     */
    public static String[] getClientArguments(String input){
        String[] argumentList = input.split(" ");
        return argumentList;
    }

    public static void updateIndex(String filename, int filesize, String dstorePort){
        System.out.println("store in progress");
        if(index.containsKey(dstorePort)) {
            index.put(dstorePort, new FileIndex());
        } else {
            index.get(dstorePort).add(filename, filesize);
        }
    }
}
