package uav.util.mavlinkviewer;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class UAVClient {
    private static Logger logger = LoggerFactory.getLogger(UAVClient.class);
    private UAV uav;

    public static interface ConnectionListener {
        void connect();
        void disconnect();
    }
    private ConnectionListener connectionListener;
    public void setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    public static interface MAVLinkMessageListener {
        void receive(MAVLinkPacket mavLinkPacket, MAVLinkMessage mavLinkMessage);
    }
    private MAVLinkMessageListener mavLinkMessageListener;
    public void setMavLinkMessageListener(MAVLinkMessageListener mavLinkMessageListener) {
        this.mavLinkMessageListener = mavLinkMessageListener;
    }

    public void start(String host, int port) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(host, port);
                    if(uav != null) {
                        //하나의 UAV만 접속 허용
                        uav.stop();
                    }
                    uav = new UAV(socket);
                    uav.start();
                } catch (Exception e) {
                    logger.error(e.toString());
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        try { uav.stop(); } catch(Exception e) {}
    }

    public void sendMessage(byte[] bytes) {
        uav.sendMessage(bytes);
    }

    public class UAV {
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public UAV(Socket socket) {
            this.socket = socket;
        }

        public void start() {
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                if(connectionListener != null) {
                    try {
                        connectionListener.connect();
                    } catch(Exception e) {
                        logger.error(e.toString());
                    }
                }

                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            receiveMessage();
                        } catch (Exception e) {
                        }
                        UAV.this.stop();
                    }
                };
                thread.setDaemon(true);
                thread.start();
            } catch(Exception e) {
            }
        }

        public void stop() {
            try {
                if(connectionListener != null) {
                    try {
                        connectionListener.disconnect();
                    } catch(Exception e) {
                        logger.error(e.toString());
                    }
                }
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
                                mavLinkMessageListener.receive(mavLinkPacket, mavLinkMessage);
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
