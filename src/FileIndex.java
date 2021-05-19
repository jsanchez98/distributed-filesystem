import java.util.concurrent.ConcurrentHashMap;

public class FileIndex {

    private int numberOfAcks;
    ConcurrentHashMap<String, FileData> files;

    FileIndex(){
        files = new ConcurrentHashMap<>();
    }



    public void addFile(String filename, FileData filedata){
        files.put(filename, filedata);
    }

    public ConcurrentHashMap<String, FileData> getFiles() {
        return files;
    }

    public FileData getFileData(String filename){
        return files.get(filename);
    }
}

class FileData {
    private int length;
    private int numberOfAcks;

    FileData(int length){
        this.length = length;
        numberOfAcks = 0;
    }

    public void incrementAcks(){
        numberOfAcks++;
    }

    public int getNumberOfAcks() {
        return numberOfAcks;
    }

    public int getLength() {
        return length;
    }
}
