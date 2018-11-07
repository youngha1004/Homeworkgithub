package uav.gcs.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

public class Network {
    private static Logger Logger = LoggerFactory.getLogger(Network.class);
    private static UAV uav;
    public static String networkType;
    public static String udpLocalPort;
    public static String tcpServerIP;
    public static String tcpServerPort;

    static {
        try {
            Properties properties = new Properties();
            FileReader reader = new FileReader(
                    Network.class.getResource("network.properties").getPath());
            properties.load(reader);

            networkType = properties.getProperty("networkType");
            udpLocalPort = properties.getProperty("udpLocalPort");
            tcpServerIP = properties.getProperty("tcpServerIP");
            tcpServerPort = properties.getProperty("tcpServerPort");
        } catch (IOException e) {
            Logger.error(e.toString());
        }
    }

    public static void save(){
        try {
            // properties files are in out folder (relative paths are depending on class file)
            PrintWriter writer = new PrintWriter(Network.class.getResource("network.properties").getPath());
            writer.println("networkType="+networkType);
            writer.println("udpLocalPort="+udpLocalPort);
            writer.println("tcpServerIP="+tcpServerIP);
            writer.println("tcpServerPort="+tcpServerPort);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static UAV createUAV(){
        if(networkType.equals("UDP")){
            uav = new UAVUDP(udpLocalPort);
        }else if(networkType.equals("TCP")){
            uav = new UAVTCP(tcpServerIP, tcpServerPort);
        }
        return uav;
    }

    public static UAV getUAV(){
        return uav;
    }

    public static void destroyUAV(){
        if(uav != null) {
            uav.disconnect();
            uav = null;
        }
    }
}
