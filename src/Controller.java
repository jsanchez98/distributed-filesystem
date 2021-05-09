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
    static ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> index;
    static ConcurrentHashMap<String, Connection> clientConnections;
    static ConcurrentHashMap<String, Connection> dstoreConnections;

    public static void main(String[] args){
        System.out.println("changed");
        cport = Integer.parseInt(args[0]);
        R = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        rebalancePeriod = Integer.parseInt(args[3]);

        clientConnections = new ConcurrentHashMap<>();
        dstoreConnections = new ConcurrentHashMap<>();
        index = new ConcurrentHashMap<>();

        for(int i = 0; i < R; i++){
            ConcurrentHashMap<String, Integer> file = new ConcurrentHashMap<>();
            file.put("filename", 3);
            index.put(Integer.toString(i), file);
        }

        try{
            ServerSocket ss = new ServerSocket(cport);

            for(;;){
                System.out.println("Waiting for connection");
                System.out.println(cport + " " + R + " " + timeout + " " + rebalancePeriod);
                Socket client = ss.accept();
                System.out.println("connected");

                Connection connection  = new Connection(client);
                String identifier = connection.readLine();

                if(identifier.equals("client")) {
                    int numberOfConnections = clientConnections.size() + 1;
                    String clientID = "client " + numberOfConnections;
                    clientConnections.put(clientID, connection);

                    System.out.println("Client ID: " + clientID);

                    //byte[] bytes = new byte[1000]; int bytelen;
                    String input = connection.readLine();

                    ArrayList<String> clientArgs = getClientArguments(input);

                    handleCommand(clientArgs, clientID);

                } else if (identifier.startsWith("dstore")){
                    dstoreConnections.put(identifier, connection);
                    index.put(identifier, new ConcurrentHashMap<>());
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
    public static void handleCommand(ArrayList<String> clientArgs, String clientID){
        String command = clientArgs.get(0);

        if(command.equals("STORE")){
            try {
                String filename = clientArgs.get(1);
                int filesize = Integer.parseInt(clientArgs.get(2));

                updateIndex(filename, filesize, clientID);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Method to extract arguments from bytes of client's
     * input stream
     * @param input
     * @return argumentList
     */
    public static ArrayList<String> getClientArguments(String input){
        ArrayList<String> argumentList = new ArrayList<>();
        int firstSpace = input.indexOf(" ");
        String command = input.substring(0, firstSpace);
        argumentList.add(command);

        int secondSpace = input.indexOf(" ", firstSpace + 1);
        String fileName = input.substring(firstSpace + 1, secondSpace);
        System.out.println("fileName: " + fileName);
        argumentList.add(fileName);

        int end = input.length();
        String fileSize = input.substring(secondSpace + 1, end);
        System.out.println("fileSize: " + fileSize);
        argumentList.add(fileSize);

        return argumentList;
    }

    public static void updateIndex(String filename, int filesize, String clientID){
        System.out.println("store in progress");

        Iterator<String> it = index.keySet().iterator();
        StringBuilder portString = new StringBuilder();

        portString.append("STORE_TO");

        for(int i = 0; i < R; i++){
            portString.append(" " + it.next());
        }

        portString.append("\n");

        clientConnections.get(clientID).write(portString.toString());

        System.out.println("R ports: " + portString);
    }
}
