import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.*;

public class Controller {
    static int cport;
    static int R;
    static int timeout;
    static int rebalancePeriod;
    static ConcurrentHashMap<Dstore, ConcurrentHashMap<String, Integer>> index;

    public static void main(String[] args){
        cport = Integer.parseInt(args[0]);
        R = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        rebalancePeriod = Integer.parseInt(args[3]);

        try{
            ServerSocket ss = new ServerSocket(cport);
            for(;;){
                System.out.println("Waiting for connection");
                System.out.println(cport + " " + R + " " + timeout + " " + rebalancePeriod);
                Socket client = ss.accept();
                System.out.println("connected");
                InputStream in = client.getInputStream();
                byte[] bytes = new byte[1000]; int bytelen;
                bytelen = in.read(bytes);

                ArrayList<String> clientArgs = getArguments(bytes, bytelen);

                handleCommand(clientArgs);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void handleCommand(ArrayList<String> clientArgs){
        String command = clientArgs.get(0);

        if(command.equals("STORE")){
            try {
                String filename = clientArgs.get(1);
                int filesize = Integer.parseInt(clientArgs.get(2));

                updateIndex(filename, filesize);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Method to extract arguments from bytes of client's
     * input stream
     * @param bytes
     * @return argumentList
     */
    public static ArrayList<String> getArguments(byte[] bytes, int bytelen){
        ArrayList<String> argumentList = new ArrayList<>();
        String firstBuffer = new String(bytes,0, bytelen);
        int firstSpace = firstBuffer.indexOf(" ");
        String command = firstBuffer.substring(0, firstSpace);
        argumentList.add(command);

        int secondSpace = firstBuffer.indexOf(" ", firstSpace + 1);
        System.out.println("secondSpace:" + secondSpace);
        String fileName = firstBuffer.substring(firstSpace + 1, secondSpace);
        System.out.println("fileName " + fileName);
        argumentList.add(fileName);

        int end = firstBuffer.length();
        String fileSize = firstBuffer.substring(secondSpace + 1, end);
        argumentList.add(fileSize);
        System.out.println("filesize:" + fileSize);

        return argumentList;
    }

    public static void updateIndex(String filename, int filesize){

    }
}
