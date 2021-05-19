import java.io.*;
import java.net.*;
import java.util.Iterator;
import java.util.concurrent.*;

public class Controller {
    int cport;
    int R;
    int timeout;
    int rebalancePeriod;

    ConcurrentHashMap<String, FileIndex> index;
    ConcurrentHashMap<String, Connection> clientConnections;
    ConcurrentHashMap<String, Connection> dstoreConnections;


    private Controller(String[] args){
        cport = Integer.parseInt(args[0]);
        R = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        rebalancePeriod = Integer.parseInt(args[3]);

        clientConnections = new ConcurrentHashMap<>();
        dstoreConnections = new ConcurrentHashMap<>();
        index = new ConcurrentHashMap<>();
    }

    public ConcurrentHashMap<String, FileIndex> getIndex() {
        return index;
    }

    public ConcurrentHashMap<String, Connection> getDstoreConnections() {
        return dstoreConnections;
    }

    public ConcurrentHashMap<String, Connection> getClientConnections() {
        return clientConnections;
    }

    /**
     * TODO: refactor initialisation of client and dstore connections
     * @param args
     */
    public static void main(String[] args){

        Controller controller = new Controller(args);
        controller.start();
    }

    public void start(){
        ServerSocket ss = null;
        try { ss = new ServerSocket(cport);}
        catch (Exception e) { e.printStackTrace();}

        for(;;){
            try {
                System.out.println("Waiting for connection");
                Socket incoming = ss.accept();

                ConnectionHandler connectionHandler = new ConnectionHandler(incoming, this);

                new Thread(connectionHandler).start();

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
    public void storeOperation(String clientID, String filename, int filesize)
            throws IOException {

        if(checkFileExists(filename)){
            clientConnections.get(clientID).write("ERROR_FILE_ALREADY_EXISTS");
            return;
        }

        Iterator<String> it = dstoreConnections.keySet().iterator();
        StringBuilder portString = new StringBuilder();

        portString.append("STORE_TO");

        String[] Rdstores = new String[R];

        for(int i = 0; i < R; i++){
            String dstoreI = it.next();
            String[] string = dstoreI.split(" ");
            String port = string[1];
            System.out.println("selected port: " + port);
            updateIndex(filename, filesize, port);

            Rdstores[i] = dstoreI;
            portString.append(" " + port);
        }

        portString.append("\n");

        clientConnections.get(clientID).write(portString.toString());

        System.out.println("R ports: " + portString);

        while(!checkAllDstoreAcks(Rdstores, filename)){}

        clientConnections.get(clientID).write("STORE_COMPLETE");
    }

    /**
     * Checks that all connected Dstores that were selected by
     * the storeOperation method have responded with "STORE_ACK {filename}"
     * @param Rdstores array of selected dstore identifier strings
     * @param filename filename
     * @return true, if controller receives all dstore acks. false, otherwise
     * @throws IOException
     */
    public boolean checkAllDstoreAcks(String[] Rdstores, String filename)
            throws IOException{

        int ackCount = 0;

        for(String dstorePort : Rdstores){
            FileData fileData = index.get(dstorePort).getFileData(filename);
            if(fileData.getNumberOfAcks() == 1){
                ackCount++;
            }
        }

        return ackCount == R;
    }

    public void checkConnections() throws IOException {
        ConcurrentHashMap.KeySetView<String, Connection> dstoreSet =
                dstoreConnections.keySet();
        ConcurrentHashMap.KeySetView<String, Connection> clientSet =
                clientConnections.keySet();

        for(String s : dstoreSet){
            if(dstoreConnections.get(s).readLine() == null){
                dstoreConnections.remove(s);
                System.out.println(s + " disconnected");
            }
        }

        for(String s : clientSet){
            try{
                clientConnections.get(s).write("");
            } catch (Exception e){
                clientConnections.remove(s);
            }
        }
    }

    public boolean checkEnoughDstores(){
        ConcurrentHashMap.KeySetView<String, Connection> set =
                dstoreConnections.keySet();
        System.out.println("set: " + set.size());
        System.out.println("dc: " + dstoreConnections.size());
        System.out.println("R: " + R);
        return set.size() >= R;
    }

    /**
     * looks through all the files in the inner file index hashmap
     *to see if any has the same name as the file to be added
     * @param filename filename
     * @return boolean true if filename found, false otherwise
     */
    public boolean checkFileExists(String filename){
        for(String dstore : index.keySet()){
            ConcurrentHashMap<String, FileData> files = index.get(dstore).getFiles();
            if(files.containsKey(filename)){
                return true;
            }
        }
        return false;
    }

    public void updateIndex(String filename, int filesize, String dstorePort){
        System.out.println("store in progress");
        if(index.containsKey("dstore " + dstorePort)) {
            index.put(dstorePort, new FileIndex());

            FileData fileData = new FileData(filesize);
            index.get("dstore " + dstorePort).addFile(filename, fileData);
        }
    }
}
