package vd_server.helper.objects;

/**
 * Created by pouya on 1/24/17.
 */
public class FileUpload {
    final private int Object_ID = 1;
    private int _fileSize;
    private String _filename;
    private transient String _fullpath;


    public FileUpload(int _fileSize, String _filename, String _fullpath) {
        this._fileSize = _fileSize;
        this._filename = _filename;
        this._fullpath = _fullpath;
    }

    public String getFullpath() {
        return _fullpath;
    }


    public int getFileSize() {
        return _fileSize;
    }
}
