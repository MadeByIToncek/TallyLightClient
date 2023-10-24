package space.itoncek.tallylight;

import static java.lang.Thread.sleep;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.Scanner;
import java.util.StringJoiner;

public class MainActivity extends AppCompatActivity {
    WebSocketClient client = null;
    String target = "";
    String ip = "";
    Handler act;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        act = new Handler(Looper.getMainLooper());

        updateValues(State.DISCONNECTED);
        findViewById(R.id.submit).setOnClickListener(this::reload);
    }

    private void updateValues(State state) {
        act.post(()-> {
            CardView infoBg = findViewById(R.id.tally_bg);
            TextView infoFg = findViewById(R.id.tally_txt);
            Button button1 = findViewById(R.id.submit);

            infoFg.setTextColor(getColor(state.textColor));
            infoFg.setText(state.text);
            infoBg.setCardBackgroundColor(getColor(state.cardColor));
            button1.setBackgroundColor(getColor(state.cardColor));
            button1.setTextColor(getColor(state.textColor));
        });
    }

    private void writeToLog(String reason) {
        TextView log = findViewById(R.id.log);
        StringJoiner js = new StringJoiner("\n");
        Scanner sc = new Scanner(log.getText().toString());
        js.add(reason);
        while (sc.hasNextLine()) {
            js.add(sc.nextLine());
        }
        sc.close();
        log.setText(js.toString());
    }

    private void reload(View l) {
        EditText ip = findViewById(R.id.ip);
        EditText target = findViewById(R.id.source);

        updateValues(State.DISCONNECTED);
        if (client != null) {
            try {
                client.closeBlocking();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                client = null;
            }
        }

        this.ip = ip.getText().toString();
        this.target = target.getText().toString();

        updateValues(State.CONNECTING);
        client = new WebSocketClient(URI.create(this.ip), new Draft_6455()) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                writeToLog("[WS] Opened connection");
                updateValues(State.STANDBY);
            }

            @Override
            public void onMessage(String message) {
                //TODO comment this
//                writeToLog("[WS DEBUG] " + message);
                try {
                    JSONObject o = new JSONObject(message);
                    if (target.getText().toString().equals(o.getString("sourceIdent"))) {
                        System.out.println(o.getInt("active"));
                        switch (o.getInt("active")) {
                            case 0:
                                updateValues(State.STANDBY);
                                break;
                            case 1:
                                updateValues(State.ACTIVE);
                                break;
                            case 2:
                                updateValues(State.ERROR);
                                break;
                            default:
                                updateValues(State.CONNECTING);
                                break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    updateValues(State.ERROR);
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                writeToLog("[WS] Closed connection, reason: " + reason + ", code: " + code + ", isRemote? " + remote);
                updateValues(State.DISCONNECTED);
            }

            @Override
            public void onError(Exception ex) {
                writeToLog("[WS ERROR] " + ex.getMessage());
                updateValues(State.ERROR);
                new Thread(()-> {
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    updateValues(State.DISCONNECTED);
                }).start();
            }
        };
        try {
            client.connectBlocking();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}

enum State {
    DISCONNECTED(R.color.tally_col_disconnected_fg,R.color.tally_col_disconnected_bg,R.string.tally_txt_disconnected),
    CONNECTING(R.color.tally_col_connecting_fg,R.color.tally_col_connecting_bg, R.string.tally_txt_connecting),
    ERROR(R.color.tally_col_error_fg,R.color.tally_col_error_bg, R.string.tally_txt_error),
    STANDBY(R.color.tally_col_standby_fg,R.color.tally_col_standby_bg, R.string.tally_txt_standby),
    ACTIVE(R.color.tally_col_active_fg,R.color.tally_col_active_bg, R.string.tally_txt_active);

    public final int textColor;
    public final int cardColor;
    public final int text;

    State(int textColor, int cardColor, int text) {
        this.textColor = textColor;
        this.cardColor = cardColor;
        this.text = text;
    }
}