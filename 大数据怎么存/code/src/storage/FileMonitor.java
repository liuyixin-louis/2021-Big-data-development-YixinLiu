package storage;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

public class FileMonitor {
    private String path;
    private long interval;
    private static final long DEFAULT_INTERVAL = 1000;
    private FileAlterationListenerAdaptor listener;

    public FileMonitor(String path, FileAlterationListenerAdaptor listener){
        this(path, DEFAULT_INTERVAL, listener);
    }

    public FileMonitor(String path, long interval, FileAlterationListenerAdaptor listener){
        this.path = path;
        this.interval = interval;
        this.listener = listener;
    }

    public void start(){
        if(path==null) {
            throw new IllegalStateException("Listen path must not be null");
        }
        if(listener==null) {
            throw new IllegalStateException("Listener must not be null");
        }
        FileAlterationObserver observer = new FileAlterationObserver(path);
        observer.addListener(listener);
        FileAlterationMonitor monitor = new FileAlterationMonitor(interval);
        monitor.addObserver(observer);

        try {
            monitor.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
