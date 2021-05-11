import java.util.concurrent.ConcurrentHashMap;

public class FileIndex {

    ConcurrentHashMap<String, Integer> files;

    FileIndex(){
        files = new ConcurrentHashMap<>();
    }

    public void add(String filename, Integer filesize){
        files.put(filename, filesize);
    }

    public ConcurrentHashMap<String, Integer> getFiles() {
        return files;
    }
}
