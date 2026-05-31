public class FTPException extends Exception {

    private final int code;

    public FTPException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "FTP Error " + code + ": " + getMessage();
    }
}
