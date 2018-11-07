package uav.util.mavlinkviewer;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class GCSServer {
    private static Logger logger = LoggerFactory.getLogger(GCSServer.class);
    private ServerSocket serverSocket;
    public List<GCS> listGCS = new ArrayList<GCS>();

    public static interface ConnectionListener {
        void connect(List<GCS> listGCS, GCS gcs);
        void disconnect(List<GCS> listGCS, GCS gcs);
    }
    private ConnectionListener connectionListener;
    public void setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    public static interface MAVLinkMessageListener {
        void receive(int gcsIndex, MAVLinkPacket mavLinkPacket, MAVLinkMessage mavLinkMessage);
    }
    private MAVLinkMessageListener mavLinkMessageListener;
    public void setMavLinkMessageListener(MAVLinkMessageListener mavLinkMessageListener) {
        this.mavLinkMessageListener = mavLinkMessageListener;
    }

    public void start(int port)  {
        try {
            serverSocket = new ServerSocket(port);
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        while(true) {
                            Socket socket = serverSocket.accept();
                            GCS gcs = new GCS(socket);
                            gcs.start();
                        }
                    } catch(Exception e) {
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
        } catch(Exception e) {
            logger.error(e.toString());
        }
    }

    public void stop() {
        try {
            serverSocket.close();
            for(GCS gcs : listGCS) {
                gcs.stop();
            }
        } catch(Exception e) {
        }
    }

    public void sendMessageAllGCS(byte[] bytes) {
        for(GCS gcs : listGCS) {
            gcs.sendMessage(bytes);
        }
    }

    public void addGCS(GCS gcs) {
        listGCS.add(gcs);
        connectionListener.connect(listGCS, gcs);
    }

    public void removeGCS(GCS gcs) {
        listGCS.remove(gcs);
        connectionListener.disconnect(listGCS, gcs);
    }

    public class GCS {
        private int gcsIndex;
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public GCS(Socket socket) {
            this.socket = socket;
            addGCS(this);
            this.gcsIndex = listGCS.size()-1;
        }

        public void start() {
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            receiveMessage();
                        } catch (Exception e) {
                        }
                        removeGCS(GCS.this);
                    }
                };
                thread.setDaemon(true);
                thread.start();
            } catch(Exception e) {
            }
        }

        public void stop() {
            try {
                inputStream.close();
                outputStream.close();
                socket.close();
            } catch(Exception e) {
            }
        }

        public void receiveMessage() throws Exception {
            byte[] buffer = new byte[263];
            int len = -1;
            Parser mavParser = new Parser();
            MAVLinkPacket mavLinkPacket = null;
            MAVLinkMessage mavLinkMessage = null;
            while (true) {
                len = inputStream.read(buffer);
                if(len == -1) throw new Exception();
                for(int i=0; i<len; i++) {
                    int unsignedByte = buffer[i] & 0xff;
                    mavLinkPacket = mavParser.mavlink_parse_char(unsignedByte);
                    if (mavLinkPacket != null) {
                        mavLinkMessage = mavLinkPacket.unpack();
                        if(mavLinkMessageListener != null) {
                            try {
                                mavLinkMessageListener.receive(gcsIndex, mavLinkPacket, mavLinkMessage);
                            } catch(Exception e) {
                                logger.error(e.toString());
                            }
                        }
                    }
                }
            }
        }

        public void sendMessage(byte[] bytes) {
            try {
                outputStream.write(bytes);
                outputStream.flush();
            } catch (Exception e) {
            }
        }
    }
}
