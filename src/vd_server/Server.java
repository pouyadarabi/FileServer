package vd_server;

/**
 * Created by pouya on 1/15/17.
 */

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import vd_server.helper.Writer;
import vd_server.helper.common;
import vd_server.helper.globalVars;
import vd_server.helper.objects.FileDownload;
import vd_server.helper.objects.FileUpload;
import vd_server.helper.objects.User;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Server {

    final int BUFFER_SIZE = 512000;

    Properties props;
    Connection db = null;

    public Server(String bindAddr, int bindPort, Properties props, Connection connection) throws IOException {
        this.props = props;
        this.db = connection;

        InetSocketAddress sockAddr = new InetSocketAddress(bindAddr, bindPort);
        AsynchronousServerSocketChannel serverSock = AsynchronousServerSocketChannel.open().bind(sockAddr);
        serverSock.accept(serverSock, new CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel>() {

            @Override
            public void completed(AsynchronousSocketChannel sockChannel, AsynchronousServerSocketChannel serverSock) {
                serverSock.accept(serverSock, this);
                FileDownload currentState = new FileDownload();
                boolean is_public = Boolean.parseBoolean(props.getProperty("Public"));
                User user = new User(connection);
                if (is_public) {
                    user.PublicLogin();
                }
                startRead(sockChannel, user, currentState);

            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
                System.out.println("fail to accept a connection");
            }

        });

    }

    public static void main(String[] args) {

        Connection c = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:config/db/drivedb.db");
        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
        System.out.println("Database opened successfully");


        File configFile = new File("config/config.properties");
        Properties props = new Properties();

        try {
            FileReader reader = new FileReader(configFile);
            props.load(reader);
            reader.close();
            System.out.println("Config file loaded successfully! " + configFile.getAbsolutePath());
        } catch (FileNotFoundException ex) {
            System.out.println("config file does not exist");
            System.exit(0);
        } catch (IOException ex) {
            System.exit(0);
        }

        try {

            String ServerIP = props.getProperty("ServerIP");
            Integer ServerPort = Integer.parseInt(props.getProperty("ServerPort"));


            new Server(ServerIP, ServerPort, props, c);
            System.out.println("Server Listening on : " + ServerIP + ":" + ServerPort);
            for (; ; ) {
                Thread.sleep(10000);
            }
        } catch (Exception ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void startRead(AsynchronousSocketChannel sockChannel, User userObj, FileDownload currentState) {
        final ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);

        sockChannel.read(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
                if (result != -1) {

                    buf.flip();
                    int limits = buf.limit();
                    byte bytes[] = new byte[limits];
                    buf.get(bytes, 0, limits);

                    boolean flag = false;


                    try {
                        Charset cs = Charset.forName("UTF-8");
                        String msg = new String(bytes, cs);
                        JsonObject jsobj = new Gson().fromJson(msg, JsonObject.class);
                        int object_id = jsobj.get("Object_ID").getAsInt();

                        System.out.println(msg);

                        flag = true;
                        ByteBuffer bf = ByteBuffer.allocate(8192);

                        if (object_id == globalVars.FILE_HEADER) {

                            if (!userObj.isLogined()) {
                                return;
                            }
                            String file_name = jsobj.get("_filename").getAsString();
                            Integer file_size = jsobj.get("_fileSize").getAsInt();


                            Integer sizeInMb = file_size / (1024 * 1024);

                            String err = "";

                            if (sizeInMb > userObj.getQuota()) {
                                err = common.responseBuilder(globalVars.FILE_HEADER, false, "Quota has been reached");
                                bf.put(err.getBytes());
                            } else {


                                String userFolder = props.getProperty("FilePath") + File.separator + userObj.getID() + File.separator;
                                Path path = Paths.get(userFolder + file_name);
                                String selectedDir = path.normalize().toAbsolutePath().toString();
                                userFolder = Paths.get(userFolder).toAbsolutePath().toString();
                                System.out.println(selectedDir);

                                if (!selectedDir.startsWith(userFolder)) {
                                    err = common.responseBuilder(globalVars.FILE_HEADER, false, "Permission Error!");
                                } else {

                                    currentState.clear();
                                    currentState.set_filename(file_name);
                                    currentState.set_fileSize(file_size);

                                    err = common.responseBuilder(globalVars.FILE_HEADER, true, "Accepted");
                                }


                            }
                            bf.put(err.getBytes());


                        } else if (object_id == globalVars.USER_REGISTER) {
                            boolean Allow_signup = Boolean.parseBoolean(props.getProperty("AllowSignup"));
                            String err;
                            if (Allow_signup) {


                                String username = jsobj.get("username").getAsString();
                                String password = jsobj.get("password").getAsString();


                                if (username.matches("[A-Za-z0-9]+")) {
                                    boolean status = userObj.Register(username, password);

                                    err = common.responseBuilder(globalVars.USER_REGISTER, false, "Username already exist");

                                    if (status) {

                                        err = common.responseBuilder(globalVars.USER_REGISTER, true, "User Created");
                                    }
                                    bf.put(err.getBytes());

                                } else {
                                    err = common.responseBuilder(globalVars.USER_REGISTER, false, "Username cannot contain non-Alphanumeric characters!");
                                }

                            } else {
                                err = common.responseBuilder(globalVars.USER_REGISTER, false, "Register in not allowed on this server!");

                            }

                            bf.put(err.getBytes());

                        } else if (object_id == globalVars.USER_LOGIN) {
                            String username = jsobj.get("username").getAsString();
                            String password = jsobj.get("password").getAsString();

                            boolean status = userObj.Login(username, password);

                            String err = common.responseBuilder(globalVars.USER_LOGIN, false, "Username or Password is wrong");

                            if (status) {
                                err = common.responseBuilder(globalVars.USER_LOGIN, true, "Logined Successfully");
                            }
                            bf.put(err.getBytes());


                        } else if (object_id == globalVars.USER_INFO) {

                            String userFolder = props.getProperty("FilePath") + File.separator + userObj.getID() + File.separator;

                            JsonArray files = common.getfileList(userFolder);

                            userObj.getNewInfo();
                            JsonObject jsObj = new JsonObject();

                            jsObj.addProperty("quota", userObj.getQuota());
                            jsObj.addProperty("total_quota", userObj.getTotalQuota());
                            jsObj.add("files", files);
                            String json = jsObj.toString();

                            System.out.println(json);

                            String response = common.responseBuilder(globalVars.USER_INFO, true, json);

                            bf.put(response.getBytes());


                        } else if (object_id == globalVars.Auth_Settings) {
                            boolean is_public = Boolean.parseBoolean(props.getProperty("Public"));
                            boolean Allow_signup = Boolean.parseBoolean(props.getProperty("AllowSignup"));

                            JsonObject jsObj = new JsonObject();

                            jsObj.addProperty("is_public", is_public);
                            jsObj.addProperty("Allow_signup", Allow_signup);
                            String json = jsObj.toString();

                            String response = common.responseBuilder(globalVars.Auth_Settings, true, json);

                            bf.put(response.getBytes());


                        } else if (object_id == globalVars.Download_File) {
                            String file_name = jsobj.get("File_name").getAsString();


                            String userFolder = props.getProperty("FilePath") + File.separator + userObj.getID() + File.separator;
                            String err = "";
                            Path path = Paths.get(userFolder);
                            if (!Files.exists(path)) {
                                err = common.responseBuilder(globalVars.Download_File, false, "File not exist!");

                            } else {

                                path = Paths.get(userFolder + file_name);
                                String selectedDir = path.normalize().toAbsolutePath().toString();
                                userFolder = Paths.get(userFolder).toAbsolutePath().toString();
                                System.out.println(selectedDir);

                                if (!selectedDir.startsWith(userFolder)) {
                                    err = common.responseBuilder(globalVars.Download_File, false, "Permission Error!");
                                } else {
                                    final File myFile = new File(selectedDir);
                                    JsonObject jsObj = new JsonObject();
                                    FileUpload flh = new FileUpload((int) myFile.length(), myFile.getName(), selectedDir);
                                    Gson gson = new Gson();
                                    String objectInGson = gson.toJson(flh);

                                    err = common.responseBuilder(globalVars.Download_File, true, objectInGson);

                                    bf.put(err.getBytes());
                                    bf.flip();
                                    startWriteFile(channel, bf, flh);
                                    return;


                                }

                                bf.put(err.getBytes());
                            }


                        } else if (object_id == globalVars.Delete_File) {
                            String file_name = jsobj.get("File_name").getAsString();


                            String userFolder = props.getProperty("FilePath") + File.separator + userObj.getID() + File.separator;
                            String err = "";
                            Path path = Paths.get(userFolder);
                            if (!Files.exists(path)) {
                                err = common.responseBuilder(globalVars.Delete_File, false, "File not exist!");

                            } else {

                                path = Paths.get(userFolder + file_name);
                                String selectedDir = path.normalize().toAbsolutePath().toString();
                                userFolder = Paths.get(userFolder).toAbsolutePath().toString();
                                System.out.println(selectedDir);

                                if (!selectedDir.startsWith(userFolder) || !Files.exists(path)) {
                                    err = common.responseBuilder(globalVars.Delete_File, false, "Permission Error!");
                                } else {

                                    int size = (int) Files.size(path);

                                    float sizeInMb = size / (1024 * 1024);
                                    userObj.incQuota(sizeInMb);
                                    Files.deleteIfExists(path);
                                    err = common.responseBuilder(globalVars.Delete_File, true, "Removed!");


                                }


                                bf.put(err.getBytes());
                            }


                        }

                        bf.flip();
                        startWrite(channel, bf);


                    } catch (Exception e) {
                        // Skip
//                        throw  e;

                    }


                    if (flag == false && currentState.get_fileSize() > 0) {


                        currentState.append_byte(bytes);
                        int current_size = currentState.get_currentSize();
                        if (current_size == currentState.get_fileSize()) {

                            String userFolder = props.getProperty("FilePath") + File.separator + userObj.getID() + File.separator;

                            Path path = Paths.get(userFolder);
                            if (!Files.exists(path)) {
                                try {
                                    Files.createDirectories(path);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            path = Paths.get(userFolder + currentState.get_filename());
                            if (Files.exists(path)) {
                                path = Paths.get(userFolder + System.nanoTime() + "_" + currentState.get_filename());
                            }
                            try {
                                Files.write(path, currentState.get_filebyte());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            float sizeInMb = currentState.get_fileSize() / (1024 * 1024);
                            userObj.decQuota(sizeInMb);
                            currentState.clear();
                        }

                    }


                    buf.clear();

                    //    ByteBuffer bf = ByteBuffer.wrap(bytes);
                    //  startWrite(channel, bf);
                    startRead(channel, userObj, currentState);
                } else {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println("fail to read message from client");
            }
        });
    }

    private void startWrite(AsynchronousSocketChannel sockChannel, final ByteBuffer buf) {
        sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
                //finish to write message to client, nothing to do
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                //fail to write message to client
                System.out.println("Fail to write message to client");
            }

        });
    }

    private void startWriteFile(AsynchronousSocketChannel sockChannel, final ByteBuffer buf, final FileUpload flh) {
        Writer write_worker = new Writer();
        sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
                //finish to write message to client, nothing to do
                write_worker.doit(flh, channel);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                //fail to write message to client
                System.out.println("Fail to write message to client");
            }

        });
    }
}
