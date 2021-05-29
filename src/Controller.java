import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Controller {
    int cport;
    int R;
    int timeout;
    int rebalancePeriod;

    AtomicInteger rebalanceResponses;
    AtomicInteger rebalanceCompleteResponses;
    AtomicBoolean rebalanceInProgress;

    ConcurrentHashMap<String, FileIndex> index;
    ConcurrentHashMap<String, Connection> clientConnections;
    ConcurrentHashMap<String, Connection> dstoreConnections;
    ConcurrentHashMap<String, ArrayList<String>> dstoreListedFiles;
    HashSet<String> listOfFiles;

    Rebalancer rebalancer;


    private Controller(String[] args){
        cport = Integer.parseInt(args[0]);
        R = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        rebalancePeriod = Integer.parseInt(args[3]);

        rebalanceResponses = new AtomicInteger(0);
        rebalanceCompleteResponses = new AtomicInteger(0);
        rebalanceInProgress = new AtomicBoolean(false);

        rebalancer = new Rebalancer(rebalancePeriod, this);

        clientConnections = new ConcurrentHashMap<>();
        dstoreConnections = new ConcurrentHashMap<>();
        index = new ConcurrentHashMap<>();

        listOfFiles = new HashSet<>();
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
     *
     * @param args
     */
    public static void main(String[] args){

        Controller controller = new Controller(args);
        controller.start();
    }

    public void start(){
        try {
            ControllerLogger.init(Logger.LoggingType.ON_FILE_AND_TERMINAL);
        } catch (IOException e){
            e.printStackTrace();
        }

        ServerSocket ss = null;
        try { ss = new ServerSocket(cport);}
        catch (Exception e) { e.printStackTrace();}

        new Thread(rebalancer).start();

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

    synchronized void rebalanceOperation() throws FileDoesNotExistException {
        rebalanceInProgress.set(true);

        dstoreListedFiles = new ConcurrentHashMap<>();

        ControllerLogger.getInstance().log("Send LIST command to every connected dstore");
        for (String dstore : dstoreConnections.keySet()) {
            dstoreConnections.get(dstore).write(Protocol.LIST_TOKEN);
            ControllerLogger.getInstance().log(Protocol.LIST_TOKEN);
        }

        ControllerLogger.getInstance().log("Check that each dstore has responded");
        long start = System.currentTimeMillis();
        while (rebalanceResponses.get() != dstoreConnections.size()) {
            if (System.currentTimeMillis() - start > timeout) {
                break;
            }
        }

        //file to dstores
        ConcurrentHashMap<String, HashSet<String>> fileAllocations =
                new ConcurrentHashMap<>();

        //list of files
        HashSet<String> fileNames = new HashSet<>();

        for (String dstore : index.keySet()) {
            fileNames.addAll(index.get(dstore).getFiles().keySet());
        }

        int counter = 0;

        for (String fileName : fileNames) {
            HashSet<String> Ports = new HashSet<>();
            String[] keys = new String[]{};
            keys = dstoreConnections.keySet().toArray(keys);

            for (int i = 0; i < R; ++i) {
                String newPort = keys[((counter * R) + i) % dstoreConnections.size()];
                Ports.add(newPort);
            }

            fileAllocations.put(fileName, Ports);
            ++counter;
        }

        ControllerLogger.getInstance().log("New file allocations " + fileAllocations);

        for (String dstore : dstoreListedFiles.keySet()) {
            StringBuilder message = new StringBuilder("REBALANCE ");

            ArrayList<String> files = dstoreListedFiles.get(dstore);
            message.append(files.size()).append(" ");

            for (String file : files) {
                message.append(file + " ").append(fileAllocations.get(file).size() + " ");

                for (String ds : fileAllocations.get(file)) {
                    message.append(ds + " ");
                }
            }

            ArrayList<String> filesToRemove = new ArrayList<>();

            for (String file : files) {
                if (!fileAllocations.get(file).contains(dstore)) {
                    filesToRemove.add(file);
                }
            }

            message.append(filesToRemove.size() + " ");

            for (String file : filesToRemove) {
                message.append(file + " ");
            }

            if(message.toString().split(" ").length == 3){
                rebalanceInProgress.set(false);
                ControllerLogger.getInstance().log("Rebalance Complete");
                return;
            }

            dstoreConnections.get(dstore).write(message.toString());
            ControllerLogger.getInstance().log(message.toString());
        }

        long begin = System.currentTimeMillis();
        while (rebalanceCompleteResponses.get() != dstoreConnections.size()) {
            if (System.currentTimeMillis() - begin > timeout) {
                ControllerLogger.getInstance().log("Rebalance complete responses " +
                        "timed out");
                break;
            }
        }


        for (String dstore : index.keySet()) {
            for (String file : fileAllocations.keySet()) {
                if (fileAllocations.get(file).contains(dstore)) {
                    String d = findDstore(file, new ArrayList<>());
                    FileData fd = index.get(d).getFileData(file);
                    index.get(dstore).addFile(file, fd);
                }
            }
        }

        ControllerLogger.getInstance().log("Rebalance Complete");
        rebalanceInProgress.set(false);
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
    synchronized void storeOperation(String clientID, String fileName, int filesize)
            throws IOException {

        if(checkFileExists(fileName)){
            clientConnections.get(clientID).write(Protocol.ERROR_FILE_ALREADY_EXISTS_TOKEN);
            ControllerLogger.getInstance().messageSent(clientConnections.get(clientID).getSocket(),
                    Protocol.ERROR_FILE_ALREADY_EXISTS_TOKEN);
            return;
        }

        String[] Rdstores = selectRdstores();

        StringBuilder portString = new StringBuilder();
        portString.append("STORE_TO");

        for(String dstore : Rdstores){
            portString.append(" " + dstore);
            updateIndex(fileName, filesize, dstore);
        }

        portString.append("\n");
        String response = portString.toString();

        Connection cc = clientConnections.get(clientID);
        cc.write(response);
        ControllerLogger.getInstance().messageSent(cc.getSocket(), response);

        long start = System.currentTimeMillis();
        while(!checkAllDstoreAcks(Rdstores, fileName)){
            if(System.currentTimeMillis() - start > timeout){
                ControllerLogger.getInstance().log("Store operation for " + fileName + " timed out");
                for(String dstore : Rdstores){
                    index.get(dstore).files.remove(fileName);
                }
                return;
            }
        }

        for(String dstore : Rdstores){
            FileData fd = index.get(dstore).getFileData(fileName);
            fd.setFalseStoreAck();
            fd.setState(State.store_complete);
        }

        listOfFiles.add(fileName);

        clientConnections.get(clientID).write(Protocol.STORE_COMPLETE_TOKEN);
        ControllerLogger.getInstance().messageSent(cc.getSocket(),
                Protocol.STORE_COMPLETE_TOKEN);
    }

    public ArrayList<String> loadOperation(String clientID, String fileName, ArrayList<String> dstores) throws FileDoesNotExistException{
        String dstorePort = findDstore(fileName, dstores);

        if(!dstorePort.equals(" ")){
            FileData fileData = index.get("dstore " + dstorePort).getFileData(fileName);
            int length = fileData.getLength();

            Connection cc = clientConnections.get(clientID);
            String response = Protocol.LOAD_FROM_TOKEN + " " + dstorePort + " " + length;
            cc.write(response);
            ControllerLogger.getInstance().messageSent(cc.getSocket(), response);
            dstores.add(dstorePort);
            return dstores;
        }

        return dstores;
    }

    synchronized void removeOperation(String filename){

        for(String dstore : index.keySet()){
            ConcurrentHashMap<String, FileData> files = index.get(dstore).getFiles();
            files.get(filename).setState(State.remove_in_progress);
            listOfFiles.remove(filename);
            files.remove(filename);
            System.out.println(files.toString());
        }
    }

     String findDstore(String fileName, ArrayList<String> excluded) throws FileDoesNotExistException {
        String[] keys = new String[]{};
        keys = index.keySet().toArray(keys);
        Set<String> indexDstores = new HashSet<>(Arrays.asList(keys));
        Set<String> differenceSet = new HashSet<>(indexDstores);
        differenceSet.removeAll(new HashSet<>(excluded));

        for(String dstore : differenceSet){
            ConcurrentHashMap<String, FileData> files = index.get(dstore).getFiles();
            if(files.containsKey(fileName)){
                return dstore;
            }
        }

        ControllerLogger.getInstance().log(Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
        throw new FileDoesNotExistException(Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
    }

     String[] selectRdstores(){
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
     boolean checkAllDstoreAcks(String[] Rdstores, String filename)
            throws IOException{

        int ackCount = 0;

        for(String dstorePort : Rdstores){
            FileData fileData = index.get(dstorePort).getFileData(filename);
            if(fileData.isStoreAck()){
                ackCount++;
            }
        }

        return ackCount == R;
    }

    boolean checkEnoughDstores(){
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
        index.get(dstore).addFile(filename, new FileData(filesize));
        System.out.println(index.get(dstore).getFiles());
    }
}

class FileDoesNotExistException extends Exception {
    FileDoesNotExistException(String message){
        super(message);
    }
}
