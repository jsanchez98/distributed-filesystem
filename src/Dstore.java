import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Dstore {
    int port;
    int cport;
    int timeout;
    String file_folder;

    Dstore(String[] args){
        this.port = Integer.parseInt(args[0]);
        this.cport = Integer.parseInt(args[1]);
        this.timeout = Integer.parseInt(args[2]);
        this.file_folder = args[3];
    }

    public static void main(String[] args) {
        Dstore d = new Dstore(args);
        d.start();
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public void start(){
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            Socket socket = new Socket(localhost, 4323);

            new Thread(new DstoreControllerHandler(socket, this)).start();

            ServerSocket ss = new ServerSocket(port);

            for(;;){
                Socket clientSocket = ss.accept();
                new Thread(new DstoreClientHandler(clientSocket, socket, this)).start();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public int getPort(){
        return port;
    }

    public void storeToFile(byte[] contents, String folderName){
        try {
            File file = new File(System.getProperty("user.dir") + "/" + file_folder + "/" + folderName);

            file.getParentFile().mkdirs();

            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(contents);
            fos.flush();
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
        } catch (Exception e){
            connection.close();
        }
    }

    public boolean removeFile(String filename){
        File fileToDelete = new File(file_folder + "/" + filename);
        if(fileToDelete.exists()){
            System.out.println("entered");
            return fileToDelete.delete();
        }
        return false;
    }
}
