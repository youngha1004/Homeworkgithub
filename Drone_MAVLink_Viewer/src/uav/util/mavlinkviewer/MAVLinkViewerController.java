package uav.util.mavlinkviewer;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_param_set;
import com.MAVLink.common.msg_param_value;
import com.MAVLink.common.msg_statustext;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class MAVLinkViewerController implements Initializable {
    private static Logger logger = LoggerFactory.getLogger(MAVLinkViewerController.class);
    public static MAVLinkViewerController instance;
    private UAVClient uavClient;
    private GCSServer gcsServer;

    @FXML private Button btnUavClientStart;
    @FXML private Button btnUavClientStop;
    @FXML private TextField txtUavClientHost;
    @FXML private TextField txtUavClientPort;
    @FXML private TextField txtGcsServerPort;
    @FXML private Label txtGCSConnectionNumber;
    @FXML private ListView<String> listViewUavMavlink;
    @FXML private ListView<String> listViewGcsMavlink;
    @FXML private TextField txtUavMsgId;
    @FXML private TextField txtGcsMsgId;

    private String[] filterUAVMsgIds = {};
    private String filterGCSIndex;
    private String[] filterGCSMsgIds = {};

    private boolean uavMavlinkLog = true;
    private boolean gcsMavlinkLog = true;

    private String uavMavlinkType = "Char";
    private String gcsMavlinkType = "Char";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        MAVLinkViewerController.instance = this;

        btnUavClientStop.setDisable(true);

        uavClient = new UAVClient();
        uavClient.setConnectionListener(new UAVClient.ConnectionListener() {
            @Override
            public void connect() {
                btnUavClientStart.setDisable(true);
                btnUavClientStop.setDisable(false);
            }

            @Override
            public void disconnect() {
                btnUavClientStart.setDisable(false);
                btnUavClientStop.setDisable(true);
            }
        });
        uavClient.setMavLinkMessageListener(new UAVClient.MAVLinkMessageListener() {
            @Override
            public void receive(MAVLinkPacket mavLinkPacket, MAVLinkMessage mavLinkMessage) {
                if(uavMavlinkLog) {
                    Platform.runLater(() -> {
                        try {
                            String attachText = "";
                            if(mavLinkMessage.msgid == 253) {
                                msg_statustext msg = (msg_statustext) mavLinkMessage;
                                attachText = " (" + new String(msg.text).trim() + ")";
                            } else if(mavLinkMessage.msgid == 22) {
                                msg_param_value msg = (msg_param_value) mavLinkMessage;
                                attachText = " (" + new String(msg.param_id).trim() + ")";
                            }
                            if (filterUAVMsgIds.length != 0) {
                                for (int i = 0; i < filterUAVMsgIds.length; i++) {
                                    if (Integer.parseInt(filterUAVMsgIds[i]) == mavLinkMessage.msgid) {
                                        if(uavMavlinkType.equals("Char")) {
                                            listViewUavMavlink.getItems().add("[" + mavLinkMessage.msgid + "]  " + mavLinkMessage.toString() + attachText);
                                        } else {
                                            listViewUavMavlink.getItems().add("[" + mavLinkMessage.msgid + "]  " + Arrays.toString(toUnsignedArray(mavLinkPacket.encodePacket())));
                                        }
                                        break;
                                    }
                                }
                            } else {
                                if(uavMavlinkType.equals("Char")) {
                                    listViewUavMavlink.getItems().add("[" + mavLinkMessage.msgid + "]  " + mavLinkMessage.toString() + attachText);
                                } else {
                                    listViewUavMavlink.getItems().add("[" + mavLinkMessage.msgid + "]  " + Arrays.toString(toUnsignedArray(mavLinkPacket.encodePacket())));
                                }
                            }
                            listViewUavMavlink.scrollTo(listViewUavMavlink.getItems().size() - 1);
                        } catch (Exception e) {
                        }
                    });
                }
                gcsServer.sendMessageAllGCS(mavLinkPacket.encodePacket());
            }
        });

        gcsServer = new GCSServer();
        gcsServer.setConnectionListener(new GCSServer.ConnectionListener() {
            @Override
            public void connect(List<GCSServer.GCS> listGCS, GCSServer.GCS gcs) {
                Platform.runLater(()->{
                    txtGCSConnectionNumber.setText("GCS Connection Number: " + listGCS.size());
                });
            }

            @Override
            public void disconnect(List<GCSServer.GCS> listGCS, GCSServer.GCS gcs) {
                Platform.runLater(()->{
                    txtGCSConnectionNumber.setText("GCS Connection Number: " + listGCS.size());
                });
            }
        });
        gcsServer.setMavLinkMessageListener(new GCSServer.MAVLinkMessageListener() {
            @Override
            public void receive(int gcsIndex, MAVLinkPacket mavLinkPacket, MAVLinkMessage mavLinkMessage) {
                if(gcsMavlinkLog) {
                    Platform.runLater(() -> {
                        try {
                            String attachText = "";
                            if(mavLinkMessage.msgid == 253) {
                                msg_statustext msg = (msg_statustext) mavLinkMessage;
                                attachText = "(" + new String(msg.text).trim() + ")";
                            } else if(mavLinkMessage.msgid == 23) {
                                msg_param_set msg = (msg_param_set) mavLinkMessage;
                                attachText = "(" + new String(msg.param_id).trim() + ")";
                            }

                            if (filterGCSMsgIds.length != 0) {
                                for (int i = 0; i < filterGCSMsgIds.length; i++) {
                                    if (Integer.parseInt(filterGCSMsgIds[i]) == mavLinkMessage.msgid) {
                                        if(filterGCSIndex == null || filterGCSIndex.equals("GCS"+gcsIndex)) {
                                            if(gcsMavlinkType.equals("Char")) {
                                                listViewGcsMavlink.getItems().add("[GCS" + gcsIndex + "]  [" + mavLinkMessage.msgid + "]  " + mavLinkMessage.toString() + attachText);
                                            } else {
                                                listViewGcsMavlink.getItems().add("[GCS" + gcsIndex + "]  [" + mavLinkMessage.msgid + "]  " + Arrays.toString(toUnsignedArray(mavLinkPacket.encodePacket())));
                                            }
                                        }
                                        break;
                                    }
                                }
                            } else {
                                if(filterGCSIndex == null || filterGCSIndex.equals("GCS"+gcsIndex)) {
                                    if(gcsMavlinkType.equals("Char")) {
                                        listViewGcsMavlink.getItems().add("[GCS" + gcsIndex + "]  [" + mavLinkMessage.msgid + "]  " + mavLinkMessage.toString() + attachText);
                                    } else {
                                        listViewGcsMavlink.getItems().add("[GCS" + gcsIndex + "]  [" + mavLinkMessage.msgid + "]  " + Arrays.toString(toUnsignedArray(mavLinkPacket.encodePacket())));
                                    }
                                }
                            }
                            listViewGcsMavlink.scrollTo(listViewGcsMavlink.getItems().size() - 1);
                        } catch (Exception e) {
                        }
                    });
                }
                if(btnUavClientStop.isDisable() == false) {
                    uavClient.sendMessage(mavLinkPacket.encodePacket());
                }
            }
        });
    }

    public void handleBtnUavClientStart(ActionEvent e) {
        handleBtnUavClientStop(null);
        uavClient.start(txtUavClientHost.getText(), Integer.parseInt(txtUavClientPort.getText()));
        gcsServer.start(Integer.parseInt(txtGcsServerPort.getText()));
    }

    public void handleBtnUavClientStop(ActionEvent e) {
        uavClient.stop();
        gcsServer.stop();
    }

    public void handleBtnUavFilter(ActionEvent e) {
        if(txtUavMsgId.getText().trim().equals("")) {
            filterUAVMsgIds = new String[] {};
        } else {
            filterUAVMsgIds = txtUavMsgId.getText().split(",");
        }
    }

    public void handleBtnGcsFilter(ActionEvent e) {
        String strFilter = txtGcsMsgId.getText();
        String strFilterGCSMsgId;
        if(strFilter.contains(":")) {
            String[] arr = strFilter.split(":");
            filterGCSIndex = arr[0];
            if(arr.length == 1) {
                strFilterGCSMsgId = "";
            } else {
                strFilterGCSMsgId = arr[1];
            }
        } else {
            filterGCSIndex = null;
            strFilterGCSMsgId = strFilter;
        }

        if(strFilterGCSMsgId.trim().equals("")) {
            filterGCSMsgIds = new String[] {};
        } else {
            filterGCSMsgIds = strFilterGCSMsgId.split(",");
        }
    }

    public void handleBtnUavMavlinkLog(ActionEvent e) {
        Button btnUavMavlinkLog = (Button) e.getSource();
        if(btnUavMavlinkLog.getText().equals("Hide")) {
            uavMavlinkLog = false;
            btnUavMavlinkLog.setText("Show");
        } else {
            uavMavlinkLog = true;
            btnUavMavlinkLog.setText("Hide");
        }
    }

    public void handleBtnGcsMavlinkLog(ActionEvent e) {
        Button btnGcsMavlinkLog = (Button) e.getSource();
        if(btnGcsMavlinkLog.getText().equals("Hide")) {
            gcsMavlinkLog = false;
            btnGcsMavlinkLog.setText("Show");
        } else {
            gcsMavlinkLog = true;
            btnGcsMavlinkLog.setText("Hide");
        }
    }

    public void handleTxtUavMsgIdKeyPressed(KeyEvent e) {
        if (e.getCode().equals(KeyCode.ENTER)) {
            handleBtnUavFilter(null);
        }
    }

    public void handleTxtGcsMsgIdKeyPressed(KeyEvent e) {
        if (e.getCode().equals(KeyCode.ENTER)) {
            handleBtnGcsFilter(null);
        }
    }

    public void handleBtnUavMavlinkType(ActionEvent e) {
        Button btnUavMavlinkType = (Button) e.getSource();
        if(btnUavMavlinkType.getText().equals("Char")) {
            uavMavlinkType = "Char";
            btnUavMavlinkType.setText("Byte");
        } else {
            uavMavlinkType = "Byte";
            btnUavMavlinkType.setText("Char");
        }
    }

    public void handleBtnGcsMavlinkType(ActionEvent e) {
        Button btnGcsMavlinkType = (Button) e.getSource();
        if(btnGcsMavlinkType.getText().equals("Char")) {
            gcsMavlinkType = "Char";
            btnGcsMavlinkType.setText("Byte");
        } else {
            gcsMavlinkType = "Byte";
            btnGcsMavlinkType.setText("Char");
        }
    }

    public void handleBtnUavMavlinkClear(ActionEvent e) {
        listViewUavMavlink.getItems().clear();
    }

    public void handleBtnGcsMavlinkClear(ActionEvent e) {
        listViewGcsMavlink.getItems().clear();
    }

    public int[] toUnsignedArray(byte[] signedArray) {
        int[] unsignedArray = new int[signedArray.length];
        for(int i=0; i<signedArray.length; i++) {
            unsignedArray[i] = signedArray[i] & 0xff;
        }
        return unsignedArray;
    }
}
