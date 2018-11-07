package uav.gcs.network;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;
import com.MAVLink.common.*;
import com.MAVLink.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.rmi.runtime.Log;

import java.util.*;

public abstract class UAV {
// field --------------------------------------------------------------------------------------------------------------------------
    private static Logger Logger = LoggerFactory.getLogger(UAV.class);
    // MAVLinkMessage parser
    private Parser parser = new Parser();
    // connection status
    public boolean connected;
    // store drone's information
    public String type, autopilot, systemStatus, mode;
    public int modeInt;
    public boolean armed;
    public double roll, pitch, yaw;
    public double alt, heading;
    public double batteryVoltage, batteryCurrent, batteryRemaining;
    public boolean gpsFixed;
    public double airSpeed, groundSpeed;
    // home & current location
    public double homeLat, homeLng;
    public double currLat, currLng;

    public Map<Integer, msg_mission_item_int> missionItems;

// Connection event
    // interface
    public interface ConnectionListener{
        void connect(UAV uav);
        void disconnect(UAV uav);
    }
    // ConnectionListener storage field
    private static List<ConnectionListener> connectionListeners = new ArrayList<>();

    // store MAVLinkMessageListener
    public static void addConnectionListener(ConnectionListener listener){
        connectionListeners.add(listener);
    }

    // delete MAVLinkMessageListener
    public static void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }
// end of Connection event

// Message event
    // interface
    public interface MAVLinkMessageListener{
        void receive(MAVLinkMessage message);
    }
    // MAVLinkMessageListener storage field
    private static Map<Integer, List<MAVLinkMessageListener>> mavLinkMessageListeners = new HashMap<>();

    // store MAVLinkMessageListener into mavLinkMessageListeners
    public static void addMavLinkMessageListener(int msgID, MAVLinkMessageListener listener){
        if(mavLinkMessageListeners.containsKey(msgID)){
            List<MAVLinkMessageListener> list = mavLinkMessageListeners.get(msgID);
            list.add(listener);
        } else{
            List<MAVLinkMessageListener> list = new ArrayList<>();
            list.add(listener);
            mavLinkMessageListeners.put(msgID, list);
        }
    }

    // delete MAVLinkMessageListener from mavLinkMessageListeners
    public static void removeMavLinkMessageListener(int msgID, MAVLinkMessageListener listener){
        if(mavLinkMessageListeners.containsKey(msgID)){
            List<MAVLinkMessageListener> list = mavLinkMessageListeners.get(msgID);
            list.remove(listener);
        }
    }
// end of Message event

// Arm event
    // interface
    public interface ArmStatusListener{
        void statusChange(boolean armed);
    }
    // ArmStatusListener storage field
    private static List<ArmStatusListener> armStatusListeners = new ArrayList<>();

    // store ArmStatusListener
    public static void addArmStatusListener(ArmStatusListener listener){
        armStatusListeners.add(listener);
    }

    // delete ArmStatusListener
    public static void removeArmStatusListener(ArmStatusListener listener){
        armStatusListeners.remove(listener);
    }
// end of Arm event

// method --------------------------------------------------------------------------------------------------------------------------
    // execute after connection
    public void connect() {
        connected = true;

        List<ConnectionListener> copy = new ArrayList<>(connectionListeners);
        for(ConnectionListener listener : copy){
            listener.connect(this);
        }

        // send Heartbeat every 1 seconds
        sendHeartbeat();

        // request Information to Drone
        sendRequestDataStream();

        // request Home Position to Drone
        sendGetHomePosition();

        Thread thread = new Thread( () -> {
            try {
                receiveMessage();
            }catch (Exception e){
                disconnect();
            }
        });
        thread.setName("MAVLink Receive Thread");
        thread.setDaemon(true);
        thread.start();

        // get Information from Drone's MAVLink Message
        receiveHeartBeat();
        receiveAttitude();
        receiveGlobalPositionInt();
        receiveSysStatus();
        receiveGpsRawInt();
        receiveVfrHud();
        receiveHomePosition();
        receiveMissionRequest();
    }

    // execute before disconnection
    public void disconnect() {
        connected = false;

        List<ConnectionListener> copy = new ArrayList<>(connectionListeners);
        for(ConnectionListener listener : copy){
            listener.disconnect(this);
        }
    }

    // receive message
    public abstract void receiveMessage() throws Exception;

    // send message
    public abstract void sendMessage(byte[] bytes) throws Exception;

    // parse MAVLinkMessage
    public void parsingMAVLinkMessage(byte signed){
        int unsigned = signed & 0xFF;
        MAVLinkPacket packet = parser.mavlink_parse_char(unsigned);
        if( packet != null ) {
            MAVLinkMessage message = packet.unpack();
            List<MAVLinkMessageListener> list = mavLinkMessageListeners.get(message.msgid);
            if(list == null) return;

            // MAVLinkListener handle(receive) MAVLinkMessage
            List<MAVLinkMessageListener> copy = new ArrayList<>(list);
            for(MAVLinkMessageListener listener : copy){
                listener.receive(message);
            }
        }
    }

// Request to Drone : Send message to UAV ---------------------------------------------------------------------------------------------
    // Request to send all kinds of messages
    public void sendRequestDataStream(){
        try {
            msg_request_data_stream msg = new msg_request_data_stream();
            msg.target_system = 0;
            msg.target_component = MAV_COMPONENT.MAV_COMP_ID_ALL;
            msg.req_message_rate = 2;
            msg.req_stream_id = 0;
            msg.start_stop = 1;
            MAVLinkPacket packet = msg.pack();
            sendMessage(packet.encodePacket());

            msg.target_system = 1;
            msg.target_component = MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
            packet = msg.pack();
            sendMessage(packet.encodePacket());

        }catch (Exception e){
            Logger.error(e.toString());
        }
    }

    // Request to arm & disarm
    public void sendCmdComponentArmDisarm(boolean armed){
    try {
        // change flight mode to GUIDED
        sendSetMode(COPTER_MODE.COPTER_MODE_GUIDED);

        msg_command_long msg = new msg_command_long();
        msg.target_system = 1;
        msg.target_component = MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
        msg.command = MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
        msg.param1 = armed ? 1 : 0;

        MAVLinkPacket packet = msg.pack();
        byte[] bytes = packet.encodePacket();
        sendMessage(bytes);

    } catch (Exception e) {
        Logger.error(e.toString());
    }
}

    // Request to take off
    public void sendCmdNavTakeoff(float alt){
        try {
            // change flight mode to GUIDED
            sendSetMode(COPTER_MODE.COPTER_MODE_GUIDED);

            msg_command_long msg = new msg_command_long();
            msg.target_system = 1;
            msg.target_component = MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
            msg.command = MAV_CMD.MAV_CMD_NAV_TAKEOFF;
            msg.param7 = alt;

            MAVLinkPacket packet = msg.pack();
            byte[] bytes = packet.encodePacket();
            sendMessage(bytes);

        } catch (Exception e) {
            Logger.info(e.toString());
        }
    }

    // Request to change flight mode
    public void sendSetMode(int mode){
        try{
            msg_set_mode msg = new msg_set_mode();
            msg.target_system = 1;
            msg.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED;
            msg.custom_mode = mode;

            MAVLinkPacket packet = msg.pack();
            byte[] bytes = packet.encodePacket();
            sendMessage(bytes);

        } catch (Exception e) {
            Logger.info(e.toString());
        }
    }

    // Request to get Home Position
    public void sendGetHomePosition(){
        try{
            msg_command_long msg = new msg_command_long();
            msg.target_system = 1;
            msg.target_component = MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
            msg.command = MAV_CMD.MAV_CMD_GET_HOME_POSITION;

            MAVLinkPacket packet = msg.pack();
            byte[] bytes = packet.encodePacket();
            sendMessage(bytes);

        } catch (Exception e) {
            Logger.info(e.toString());
        }
    }

    // Request to set Position
    public void sendSetPositionTargetGlobalInt(double lat, double lng, double alt){
        try{
            sendSetMode(COPTER_MODE.COPTER_MODE_GUIDED);

            msg_set_position_target_global_int msg = new msg_set_position_target_global_int();
            msg.target_system = 1;
            msg.target_component = MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
            msg.lat_int = (int) (lat * Math.pow(10, 7));
            msg.lon_int = (int) (lng * Math.pow(10, 7));
            msg.alt = (float) alt;
            msg.coordinate_frame = MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT;
            msg.type_mask = 65528;

            MAVLinkPacket packet = msg.pack();
            byte[] bytes = packet.encodePacket();
            sendMessage(bytes);

        } catch (Exception err){
            Logger.error(err.toString());
        }
    }

    // Send Heartbeat message to UAV > inform GCS is alive
    public void sendHeartbeat(){
        Thread thread = new Thread(){
            @Override
            public void run() {
                    msg_heartbeat msg = new msg_heartbeat();
                    msg.type = MAV_TYPE.MAV_TYPE_GCS;
                    msg.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_INVALID;
                    msg.mavlink_version = 3;

                    MAVLinkPacket packet = msg.pack();
                    byte[] bytes = packet.encodePacket();

                    while(Network.getUAV() != null && Network.getUAV().connected) {
                        try {
                            sendMessage(bytes);
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            Logger.error(e.toString());
                        }
                    }
            }
        };
        thread.setName("HeartbeatThread");
        thread.setDaemon(true);
        thread.start();
    }

    // Send Mission message to UAV
    public void sendMissionCount(Map<Integer, msg_mission_item_int> items){
        try {
            missionItems = items;

            // Send Mission count to UAV
            msg_mission_count msg = new msg_mission_count();
            msg.count = items.size();
            msg.target_system = 1;
            msg.target_component = MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;

            MAVLinkPacket packet = msg.pack();
            byte[] bytes = packet.encodePacket();
            sendMessage(bytes);

        } catch (Exception e) { Logger.error(e.toString()); }
    }


// Handle request from Drone : Add Listener for the message containing UAV information ---------------------------------------------------------------------------------------------
    // Drone type, Firmware type, Flight mode, Armed or not Armed information
    public void receiveHeartBeat(){
        addMavLinkMessageListener(
                msg_heartbeat.MAVLINK_MSG_ID_HEARTBEAT,
                (MAVLinkMessage message) -> {
                    msg_heartbeat msg_heartbeat = (msg_heartbeat)message;
                    // Drone Type
                    switch (msg_heartbeat.type){
                        case MAV_TYPE.MAV_TYPE_QUADROTOR: type = "QUADROTOR";
                            break;
                        case MAV_TYPE.MAV_TYPE_HELICOPTER: type = "HELICOPTER";
                            break;
                        case MAV_TYPE.MAV_TYPE_OCTOROTOR: type = "OCTOROTOR";
                            break;
                    }
                    // Firmware Type
                    switch (msg_heartbeat.autopilot){
                        case MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA: autopilot = "ARDUPILOTMEGA";
                            break;
                        case MAV_AUTOPILOT.MAV_AUTOPILOT_PX4: autopilot = "PX4";
                            break;
                        case MAV_AUTOPILOT.MAV_AUTOPILOT_GENERIC: autopilot = "GENERIC";
                            break;
                    }
                    // FC status
                    switch (msg_heartbeat.system_status){
                        case MAV_STATE.MAV_STATE_UNINIT: systemStatus = "UNINIT";
                            break;
                        case MAV_STATE.MAV_STATE_BOOT: systemStatus = "BOOT";
                            break;
                        case MAV_STATE.MAV_STATE_CALIBRATING: systemStatus = "CALIBRATING";
                            break;
                        case MAV_STATE.MAV_STATE_STANDBY: systemStatus = "STANDBY";
                            break;
                        case MAV_STATE.MAV_STATE_ACTIVE: systemStatus = "ACTIVE";
                            break;
                        case MAV_STATE.MAV_STATE_CRITICAL: systemStatus = "CRITICAL";
                            break;
                        case MAV_STATE.MAV_STATE_EMERGENCY: systemStatus = "EMERGENCY";
                            break;
                        case MAV_STATE.MAV_STATE_POWEROFF: systemStatus = "POWEROFF";
                            break;
                        case MAV_STATE.MAV_STATE_FLIGHT_TERMINATION: systemStatus = "TERMINATION";
                            break;
                    }
                    // Flight Mode
                    modeInt = (int)msg_heartbeat.custom_mode;
                    switch (modeInt){
                        case COPTER_MODE.COPTER_MODE_STABILIZE: mode = "STABILIZE";
                            break;
                        case COPTER_MODE.COPTER_MODE_ALT_HOLD: mode = "ALT HOlD";
                            break;
                        case COPTER_MODE.COPTER_MODE_LOITER: mode = "LOITER";
                            break;
                        case COPTER_MODE.COPTER_MODE_POSHOLD: mode = "POSHOLD";
                            break;
                        case COPTER_MODE.COPTER_MODE_LAND: mode = "LAND";
                            break;
                        case COPTER_MODE.COPTER_MODE_RTL: mode = "RTL";
                            break;
                        case COPTER_MODE.COPTER_MODE_AUTO: mode = "AUTO";
                            break;
                        case COPTER_MODE.COPTER_MODE_GUIDED: mode = "GUIDED";
                            break;
                    }
                    // Armed or Not Armed : use Heartbeat's 'base_mode' to calculate if the UAV is armed safely or not
                    boolean currArmed = ((msg_heartbeat.base_mode & MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED) != 0 ) ? true : false;
                    if( armed != currArmed ){
                        armed = currArmed;
                        List<ArmStatusListener> copy = new ArrayList<>(armStatusListeners);
                        for(ArmStatusListener listener : copy){
                            listener.statusChange(armed);
                        }
                    }
                }
        );
    }

    // Roll, Pitch, Yaw information
    public void receiveAttitude(){
        addMavLinkMessageListener(
                msg_attitude.MAVLINK_MSG_ID_ATTITUDE,
                (MAVLinkMessage message) -> {
                    msg_attitude msg = (msg_attitude) message;
                    // msg_attitude's roll, pitch, yaw is radian -> convert into degree (degree = (radian * 180) / PI )
                    roll = msg.roll * 180 / Math.PI;
                    pitch = msg.pitch * 180 / Math.PI;
                    yaw = msg.yaw * 180 / Math.PI;
                }
        );
    }

    // Altitude, Latitude, Longitude, Heading information
    public void receiveGlobalPositionInt(){
        addMavLinkMessageListener(
                msg_global_position_int.MAVLINK_MSG_ID_GLOBAL_POSITION_INT,
                (MAVLinkMessage message) -> {
                    msg_global_position_int msg = (msg_global_position_int) message;
                    heading = msg.hdg / 100;
                    // msg_global_position_int's 'relative_alt' is millimeter -> convert into meter
                    alt = msg.relative_alt / 1000.0;
                    // msg_global_position_int's 'lat'&'lon' is integer -> convert into double
                    currLat = msg.lat / Math.pow(10, 7);
                    currLng = msg.lon / Math.pow(10, 7);
                }
        );
    }

    // Battery information
    public void receiveSysStatus(){
        addMavLinkMessageListener(
                msg_sys_status.MAVLINK_MSG_ID_SYS_STATUS,
                (MAVLinkMessage message) -> {
                    msg_sys_status msg = (msg_sys_status) message;
                    // round battery voltage, current to tenths place value
                    batteryVoltage = ((msg.voltage_battery / 1000) * 10) / 10.0;
                    batteryCurrent = ((msg.current_battery / 100) * 10) / 10.0;
                    batteryRemaining = ((msg.battery_remaining / 1000) * 10) / 10.0;
                }
        );
    }

    // GPS Fixed or not Fixed information
    public void receiveGpsRawInt(){
        addMavLinkMessageListener(
                msg_gps_raw_int.MAVLINK_MSG_ID_GPS_RAW_INT,
                (MAVLinkMessage message) -> {
                    msg_gps_raw_int msg = (msg_gps_raw_int) message;
                    if( msg.fix_type == 6 ){
                        gpsFixed = true;
                    } else{
                        gpsFixed = false;
                    }
                }
        );
    }

    // Airspeed, GroundSpeed information
    public void receiveVfrHud(){
        addMavLinkMessageListener(
                msg_vfr_hud.MAVLINK_MSG_ID_VFR_HUD,
                (MAVLinkMessage message) -> {
                    msg_vfr_hud msg = (msg_vfr_hud) message;
                    airSpeed = (int)(msg.airspeed * 10) / 10.0;
                    groundSpeed = (int)(msg.groundspeed * 10) / 10.0;
                }
        );
    }

    // Home Position: Home Latitude, Longitude information
    public void receiveHomePosition(){
        addMavLinkMessageListener(
                msg_home_position.MAVLINK_MSG_ID_HOME_POSITION,
                (MAVLinkMessage message) -> {
                    msg_home_position msg = (msg_home_position) message;
                    homeLat = msg.latitude / Math.pow(10, 7);
                    homeLng = msg.longitude / Math.pow(10, 7);
                }
        );
    }

    // Receive Mission request from UAV
    public void receiveMissionRequest(){
        // Add one-time listener of receiving MISSION_REQUEST from UAV
        addMavLinkMessageListener(
                msg_mission_request.MAVLINK_MSG_ID_MISSION_REQUEST,
                (MAVLinkMessage message) -> {
                    try {
                        // msg_mission_request contains the sequence of mission UAV requests
                        msg_mission_request msg = (msg_mission_request) message;
                        MAVLinkPacket packet = missionItems.get(msg.seq).pack();
                        byte[] bytes = packet.encodePacket();
                        sendMessage(bytes);

                    } catch (Exception e) {
                        Logger.error(e.toString());
                    }
                }
        );
    }

}
