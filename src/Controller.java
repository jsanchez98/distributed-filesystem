import java.io.*;
import java.net.*;
import java.util.*;
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
     * @param fileName filename
     * @param filesize filesize
     * @throws IOException for connection I/O
     */
    public void storeOperation(String clientID, String fileName, int filesize)
            throws IOException {

        if(checkFileExists(fileName)){
            clientConnections.get(clientID).write("ERROR_FILE_ALREADY_EXISTS");
            return;
        }

        String[] Rdstores = selectRdstores();

        StringBuilder portString = new StringBuilder();
        portString.append("STORE_TO");

        for(String dstore : Rdstores){
            String[] string = dstore.split(" ");
            String port = string[1];
            portString.append(" " + port);
            updateIndex(fileName, filesize, dstore);
        }

        portString.append("\n");

        clientConnections.get(clientID).write(portString.toString());

        while(!checkAllDstoreAcks(Rdstores, fileName)){}

        clientConnections.get(clientID).write("STORE_COMPLETE");
    }

    public ArrayList<String> loadOperation(String clientID, String fileName, ArrayList<String> dstores) throws FileDoesNotExistException{
        String dstore = findDstore(fileName, dstores);

        String[] dstoreParts;
        String dstorePort = null;
        try {
            dstoreParts = dstore.split(" ");
            dstorePort = dstoreParts[1];
        }catch (Exception e){
            e.printStackTrace();
        }

        FileData fileData = index.get("dstore " + dstorePort).getFileData(fileName);
        int length = fileData.getLength();

        if(!dstore.equals(" ")){
            clientConnections.get(clientID).write("LOAD_FROM " + dstorePort + " " + length);
            dstores.add(dstore);
            return dstores;
        }

        return dstores;
    }

    public String findDstore(String fileName, ArrayList<String> excluded) throws FileDoesNotExistException {
        String[] keys = new String[]{};
        keys = index.keySet().toArray(keys);
        Set<String> indexDstores = new HashSet<>(Arrays.asList(keys));
        Set<String> differenceSet = new HashSet<>(indexDstores);
        differenceSet.removeAll(new HashSet<>(excluded));
        System.out.println("difference set" + differenceSet);

        for(String dstore : differenceSet){
            ConcurrentHashMap<String, FileData> files = index.get(dstore).getFiles();
            if(files.containsKey(fileName)){
                return dstore;
            }
        }

        throw new FileDoesNotExistException("ERROR_FILE_DOES_NOT_EXIST");
    }

    public String[] selectRdstores(){
        Iterator<String> it = dstoreConnections.keySet().iterator();

        String[] Rdstores = new String[R];

        for(int i = 0; i < R; i++){
            String dstoreI = it.next();
            Rdstores[i] = dstoreI;
        }

        return Rdstores;
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

    public void updateIndex(String filename, int filesize, String dstore){
        System.out.println("store in progress");
        index.get(dstore).addFile(filename, new FileData(filesize));
        System.out.println(index.get(dstore).getFiles());
    }
}

class FileDoesNotExistException extends Exception {
    public FileDoesNotExistException(String message){
        super(message);
    }
}
