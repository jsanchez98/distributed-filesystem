import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class Dstore {
    int port;
    int cport;
    int timeout;
    String file_folder;

    ConcurrentHashMap<String, Integer> files;

    Dstore(String[] args){
        this.port = Integer.parseInt(args[0]);
        this.cport = Integer.parseInt(args[1]);
        this.timeout = Integer.parseInt(args[2]);
        this.file_folder = args[3];

        files = new ConcurrentHashMap<>();
    }

    public static void main(String[] args) {
        Dstore d = new Dstore(args);
        d.start();
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public void start(){
        try {
            DstoreLogger.init(Logger.LoggingType.ON_FILE_AND_TERMINAL, port);
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, cport);

            new Thread(new DstoreControllerHandler(socket, this)).start();

            ServerSocket ss = new ServerSocket(port);

            for(;;){
                Socket clientSocket = ss.accept();
                new Thread(new DstoreConnectionHandler(clientSocket, socket, this)).start();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public int getPort(){
        return port;
    }

    synchronized void storeToFile(byte[] contents, String fileName){
        try {
            File file = new File(System.getProperty("user.dir") + "/" + file_folder + "/" + fileName);

            file.getParentFile().mkdirs();

            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(contents);
            fos.flush();

            files.put(fileName, contents.length);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void loadFile(String filename, Connection connection) throws IOException {
        try {
            File requestedFile = new File(file_folder + "/" + filename);
            InputStream in = new FileInputStream(requestedFile);
            byte[] fileContent = in.readAllBytes();
            connection.writeBytes(fileContent);
            connection.close();
        } catch (Exception e){
            connection.close();
        }
    }

    public boolean removeFile(String filename){
        File fileToDelete = new File(file_folder + "/" + filename);

        if(fileToDelete.exists()){
            files.remove(filename);
            return fileToDelete.delete();
        }
        return false;
    }
}
