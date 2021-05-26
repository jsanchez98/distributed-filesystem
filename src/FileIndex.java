import java.util.concurrent.ConcurrentHashMap;

public class FileIndex {

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
    private boolean storeAck;
    private boolean removeAck;

    FileData(int length){
        this.length = length;
        storeAck = false;
        removeAck = false;
    }

    public boolean isRemoveAck() {
        return removeAck;
    }

    public boolean isStoreAck() {
        return storeAck;
    }

    public void setTrueRemoveAck(){ this.removeAck = true; }

    public void setFalseRemoveAck(){ this.removeAck = false; }

    public void setTrueStoreAck(){ this.storeAck = true; }

    public void setFalseStoreAck(){ this.storeAck = false; }

    public int getLength() {
        return length;
    }
}
