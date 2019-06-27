/*
 *
 * Copyright (c) PLUX S.A., All Rights Reserved.
 * (www.plux.info)
 *
 * This software is the proprietary information of PLUX S.A.
 * Use is subject to license terms.
 *
 */

package info.plux.android.sample;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import info.plux.pluxapi.Constants;
import info.plux.pluxapi.PluxDevice;
import info.plux.pluxapi.States;
import info.plux.pluxapi.TypeOfCommunication;
import info.plux.pluxapi.bioplux.*;
import info.plux.pluxapi.bioplux.utils.*;
import info.plux.pluxapi.bitalino.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static info.plux.pluxapi.Constants.*;
import static info.plux.pluxapi.States.DISCONNECTED;
import static info.plux.pluxapi.bioplux.CommandDecoder.DisconnectEventType;
import static info.plux.pluxapi.bioplux.Event.*;
import static info.plux.pluxapi.bioplux.Event.ON_BODY_EVENT;

public class DeviceActivity extends AppCompatActivity implements OnBiopluxDataAvailable, OnBiopluxError, OnBITalinoDataAvailable, View.OnClickListener {
    private final String TAG = this.getClass().getSimpleName();

    public final static String EXTRA_DEVICE = "info.plux.pluxandroid.DeviceActivity.EXTRA_DEVICE";
    public final static String FRAME = "info.plux.pluxandroid.DeviceActivity.Frame";

    private int samplingRate = 1000;

    private BluetoothDevice bluetoothDevice;
    private boolean isBioplux = false;

    private BITalinoCommunication bitalino;
    private boolean isBITalino2 = false;

    private BiopluxCommunication bioplux;

    private Handler handler;

    private States currentState = DISCONNECTED;

    private boolean isUpdateReceiverRegistered = false;

    /*
     * UI elements
     */
    private TextView nameTextView;
    private TextView addressTextView;
    private TextView elapsedTextView;
    private TextView stateTextView;

    private Button connectButton;
    private Button disconnectButton;
    private Button startButton;
    private Button stopButton;

    private LinearLayout bitalinoLinearLayout;
    private Button stateButton;
    private RadioButton digital1RadioButton;
    private RadioButton digital2RadioButton;
    private RadioButton digital3RadioButton;
    private RadioButton digital4RadioButton;
    private Button triggerButton;
    private SeekBar batteryThresholdSeekBar;
    private Button batteryThresholdButton;
    private SeekBar pwmSeekBar;
    private Button pwmButton;
    private TextView resultsTextView;

    private LinearLayout biopluxLinearLayout;
    private Button versionButton;
    private Button descriptionButton;
    private Button batteryButton;
    private TextView biopluxResultsTextView;

    private boolean isDigital1RadioButtonChecked = false;
    private boolean isDigital2RadioButtonChecked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().hasExtra(EXTRA_DEVICE)) {
            bluetoothDevice = getIntent().getParcelableExtra(EXTRA_DEVICE);
        }

        setContentView(R.layout.activity_device);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setTitle(bluetoothDevice.getAddress());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        initView();
        setUIElements();

        handler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Bundle bundle = msg.getData();
                Parcelable frame = bundle.getParcelable(FRAME);

                if (frame.getClass().equals(BiopluxFrame.class)) { //biosignalsplux
                    biopluxResultsTextView.setText(frame.toString());
                } else if (frame.getClass().equals(BITalinoFrame.class)) { //BITalino
                    resultsTextView.setText(frame.toString());
                }
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.acquisition, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_finish:
            case android.R.id.home:
                finish();
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(updateReceiver, makeUpdateIntentFilter());
        isUpdateReceiverRegistered = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isUpdateReceiverRegistered) {
            unregisterReceiver(updateReceiver);
        }

        if (bioplux != null) {
            bioplux.unregisterReceivers();
        }

        if (bitalino != null) {
            bitalino.unregisterReceivers();
        }
    }

    /*
     * UI elements
     */
    private void initView() {
        nameTextView = findViewById(R.id.device_name_text_view);
        addressTextView = findViewById(R.id.mac_address_text_view);
        elapsedTextView = findViewById(R.id.elapsed_time_Text_view);
        stateTextView = findViewById(R.id.state_text_view);

        connectButton = findViewById(R.id.connect_button);
        disconnectButton = findViewById(R.id.disconnect_button);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        //BITalino UI elements
        bitalinoLinearLayout = findViewById(R.id.bitalino_linear_layout);
        stateButton = findViewById(R.id.state_button);
        digital1RadioButton = findViewById(R.id.digital_1_radio_button);
        digital2RadioButton = findViewById(R.id.digital_2_radio_button);
        digital3RadioButton = findViewById(R.id.digital_3_radio_button);
        digital4RadioButton = findViewById(R.id.digital_4_radio_button);
        triggerButton = findViewById(R.id.trigger_button);
        batteryThresholdSeekBar = findViewById(R.id.battery_threshold_seek_bar);
        batteryThresholdButton = findViewById(R.id.battery_threshold_button);
        pwmSeekBar = findViewById(R.id.pwm_seek_bar);
        pwmButton = findViewById(R.id.pwm_button);
        resultsTextView = findViewById(R.id.results_text_view);

        //biosignalsplux UI elements
        biopluxLinearLayout = findViewById(R.id.bioplux_linear_layout);
        versionButton = findViewById(R.id.version_button);
        descriptionButton = findViewById(R.id.description_button);
        batteryButton = findViewById(R.id.battery_button);
        biopluxResultsTextView = findViewById(R.id.bioplux_results_text_view);
    }

    private void setUIElements() {
        nameTextView.setText(bluetoothDevice.getName());
        addressTextView.setText(bluetoothDevice.getAddress());
        stateTextView.setText(currentState.name());

        if (bluetoothDevice.getName() != null) {
            isBioplux = (bluetoothDevice.getName().toLowerCase().contains("bitalino")) ? false : true;
        } else {
            isBioplux = false;
        }

        TypeOfCommunication communication = TypeOfCommunication.getById(bluetoothDevice.getType());
        if (communication.equals(TypeOfCommunication.DUAL)) {
            communication = TypeOfCommunication.BTH;
        }

        if (isBioplux) {
            bioplux = new BiopluxCommunicationFactory().getCommunication(communication, this, this, this);
            bioplux.setConnectionControllerEnabled(false);
            bioplux.setDataStreamControllerEnabled(false);
            //uncomment to receive the data as an array instead of a object [only available for BTH]
            //bioplux.setBiopluxFrameEnabled(false);
        } else {
            bitalino = new BITalinoCommunicationFactory().getCommunication(communication, this, this);
            bitalino.setConnectionControllerEnabled(false);
            bitalino.setDataStreamControllerEnabled(true);
        }

        connectButton.setOnClickListener(this);
        disconnectButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        stateButton.setOnClickListener(this);
        digital1RadioButton.setOnClickListener(this);
        digital2RadioButton.setOnClickListener(this);
        digital3RadioButton.setOnClickListener(this);
        digital4RadioButton.setOnClickListener(this);
        triggerButton.setOnClickListener(this);
        batteryThresholdButton.setOnClickListener(this);
        pwmButton.setOnClickListener(this);

        versionButton.setOnClickListener(this);
        descriptionButton.setOnClickListener(this);
        batteryButton.setOnClickListener(this);

        bitalinoLinearLayout.setVisibility(isBioplux ? View.GONE : View.VISIBLE);
        biopluxLinearLayout.setVisibility(isBioplux ? View.VISIBLE : View.GONE);
    }

    /*
     * Local Broadcast
     */
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_STATE_CHANGED.equals(action)) {
                final String identifier = intent.getStringExtra(IDENTIFIER);
                final States state = (States) intent.getSerializableExtra(EXTRA_STATE_CHANGED);

                stateTextView.setText(state.name());

                if (state.equals(DISCONNECTED)) {
                    biopluxResultsTextView.setText("");
                }
            } else if (ACTION_DATA_AVAILABLE.equals(action)) {
                if (intent.hasExtra(EXTRA_DATA)) {
                    Parcelable parcelable = intent.getParcelableExtra(EXTRA_DATA);
                    if (parcelable.getClass().equals(BiopluxFrame.class)) { //biosignals
                        biopluxResultsTextView.setText(parcelable.toString());
                    } else if (parcelable.getClass().equals(BITalinoFrame.class)) { //BITalino
                        resultsTextView.setText(parcelable.toString());
                    }
                }
            } else if (ACTION_DEVICE_READY.equals(action)) {
                final String identifier = intent.getStringExtra(IDENTIFIER);
                final PluxDevice pluxDevice = intent.getParcelableExtra(PLUX_DEVICE);

                biopluxResultsTextView.setText(pluxDevice.toString());
            } else if (ACTION_COMMAND_REPLY.equals(action)) {
                final String identifier = intent.getStringExtra(IDENTIFIER);

                if (intent.hasExtra(EXTRA_COMMAND_REPLY) && (intent.getParcelableExtra(EXTRA_COMMAND_REPLY) != null)) {
                    final Parcelable parcelable = intent.getParcelableExtra(EXTRA_COMMAND_REPLY);

                    if (parcelable instanceof PluxDevice) { //biosignals
                        final PluxDevice pluxDevice = intent.getParcelableExtra(EXTRA_COMMAND_REPLY);

                        final Intent readyIntent = new Intent(ACTION_DEVICE_READY);
                        readyIntent.putExtra(IDENTIFIER, identifier);
                        readyIntent.putExtra(PLUX_DEVICE, pluxDevice);
                        sendBroadcast(readyIntent);
                    }
                    else if (parcelable instanceof EventData) { //biosignals
                        final EventData eventData = intent.getParcelableExtra(Constants.EXTRA_COMMAND_REPLY);

                        if (eventData.getEventDescription().equals(Constants.BATTERY_EVENT)) {

                        }
                        else if(eventData.getEventDescription().equals(DISCONNECT_EVENT)){
                            final DisconnectEventType disconnectEventType = (DisconnectEventType) intent.getSerializableExtra(EXTRA_EVENT_DATA);

                            Log.d(TAG, "Disconnect event: " + disconnectEventType.name());
                        }
                    }
                    else if (parcelable instanceof CommandReplyString) { //biosignals
                        biopluxResultsTextView.setText(((CommandReplyString) parcelable).getCommandReply());
                    }
                    else if (parcelable instanceof Schedules) { //biosignals

                    }
                    else if (parcelable instanceof BITalinoState) { //BITalino
                        Log.d(TAG, ((BITalinoState) parcelable).toString());
                        resultsTextView.setText(parcelable.toString());
                    }
                    else if (parcelable instanceof BITalinoDescription) { //BITalino
                        isBITalino2 = ((BITalinoDescription) parcelable).isBITalino2();
                        resultsTextView.setText(String.format("isBITalino2: %b; FwVersion: %.1f", isBITalino2, ((BITalinoDescription) parcelable).getFwVersion()));
                    }
                }
            } else if (ACTION_EVENT_AVAILABLE.equals(action)) {
                final String identifier = intent.getStringExtra(IDENTIFIER);

                if (!intent.hasExtra(EXTRA_EVENT) || intent.getSerializableExtra(EXTRA_EVENT) == null) {
                    return;
                }

                final Event event = (Event) intent.getSerializableExtra(EXTRA_EVENT);

                if (event.equals(SENSOR_ID_CHANGE)) {

                }
                else if (event.equals(DIGITAL_INPUT_CHANGE)) {
                    final DigitalInputChange digitalInputChange = intent.getParcelableExtra(EXTRA_EVENT_DATA);
                    Log.d(TAG, digitalInputChange.toString());
                }
                else if (event.equals(SCHEDULE_CHANGE)) {

                }
                else if (event.equals(CLOCK_SYNCHRONIZATION)) {

                }
                else if (event.equals(I_2_C_EVENT)) {

                }
                else if (event.equals(GESTURE_FEATURES_EVENT)) {

                }
                else if (event.equals(DISCONNECT)) {
                    final DisconnectEventType disconnectEventType = (DisconnectEventType) intent.getSerializableExtra(EXTRA_EVENT_DATA);
                    Log.d(TAG, disconnectEventType.name());
                }
                else if (event.equals(ON_BODY_EVENT)) {

                }
                else if (event.equals(BATTERY_LEVEL_EVENT)) {

                }
                else {
                    Log.e(TAG, "Unknown event");
                }
            }
        }
    };

    private IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_STATE_CHANGED);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        intentFilter.addAction(ACTION_EVENT_AVAILABLE);
        intentFilter.addAction(ACTION_DEVICE_READY);
        intentFilter.addAction(ACTION_COMMAND_REPLY);
        return intentFilter;
    }

    /*
     * Callbacks
     */
    @Override
    public void onBiopluxDataAvailable(BiopluxFrame biopluxFrame) {
        if (biopluxFrame.getSequence() % samplingRate == 0) {
            Log.d(TAG, biopluxFrame.toString());

            Message message = handler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putParcelable(FRAME, biopluxFrame);
            message.setData(bundle);
            handler.sendMessage(message);
        }
    }

    @Override
    public void onBiopluxDataAvailable(String identifier, int seqNumber, int[] biopluxFrame, int digitalInput) {
        Message message = handler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putParcelable(FRAME, new BiopluxFrame(identifier, seqNumber, biopluxFrame, digitalInput, null));
        message.setData(bundle);
        handler.sendMessage(message);
    }

    @Override
    public void onBiopluxError(CommandError error) {
        Log.e(TAG, "onBiopluxError: " + error.name());
    }

    @Override
    public void onDataLost(String identifier, int count) {
        Log.e(TAG, "onDataLost: " + identifier + " -> " + count);
    }

    @Override
    public void onBITalinoDataAvailable(BITalinoFrame bitalinoFrame) {
        Message message = handler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putParcelable(FRAME, bitalinoFrame);
        message.setData(bundle);
        handler.sendMessage(message);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.connect_button:
                if (isBioplux) {
                    try {
                        bioplux.connect(bluetoothDevice.getAddress());
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        bitalino.connect(bluetoothDevice.getAddress());
                    } catch (BITalinoException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.disconnect_button:
                stopTimer();

                if (isBioplux) {
                    try {
                        bioplux.disconnect();
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        bitalino.disconnect();
                    } catch (BITalinoException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.start_button:
                startTimer();

                if (isBioplux) {
                    List<Source> sources = new ArrayList<>();
                    sources.add(new Source(1, 16, (byte)0x01, 1));
                    sources.add(new Source(2, 16, (byte)0x01, 1));
                    sources.add(new Source(3, 16, (byte)0x01, 1));
                    sources.add(new Source(4, 16, (byte)0x01, 1));
                    sources.add(new Source(5, 16, (byte)0x01, 1));
                    sources.add(new Source(6, 16, (byte)0x01, 1));
                    sources.add(new Source(7, 16, (byte)0x01, 1));
                    sources.add(new Source(8, 16, (byte)0x01, 1));


                    try {
                        bioplux.start(samplingRate, sources);
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        bitalino.start(new int[]{0, 1, 2, 3, 4, 5}, samplingRate);
                    } catch (BITalinoException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.stop_button:
                stopTimer();

                if (isBioplux) {
                    try {
                        bioplux.stop();
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        bitalino.stop();
                    } catch (BITalinoException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.state_button:
                if (!isBioplux) {
                    try {
                        bitalino.state();
                    } catch (BITalinoException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.trigger_button:
                if (!isBioplux) {
                    int[] digitalChannels = new int[isBITalino2 ? 2 : 4];

                    digitalChannels[0] = (digital1RadioButton.isChecked()) ? 1 : 0;
                    digitalChannels[1] = (digital2RadioButton.isChecked()) ? 1 : 0;

                    if (!isBITalino2) {
                        digitalChannels[2] = (digital3RadioButton.isChecked()) ? 1 : 0;
                        digitalChannels[3] = (digital4RadioButton.isChecked()) ? 1 : 0;
                    }

                    try {
                        bitalino.trigger(digitalChannels);
                    } catch (BITalinoException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.digital_1_radio_button:
                if (isDigital1RadioButtonChecked) {
                    digital1RadioButton.setChecked(false);
                } else {
                    digital1RadioButton.setChecked(true);
                }
                isDigital1RadioButtonChecked = digital1RadioButton.isChecked();
                break;
            case R.id.digital_2_radio_button:
                if (isDigital2RadioButtonChecked) {
                    digital2RadioButton.setChecked(false);
                } else {
                    digital2RadioButton.setChecked(true);
                }
                isDigital2RadioButtonChecked = digital2RadioButton.isChecked();
                break;
            case R.id.digital_3_radio_button:
                if (digital3RadioButton.isChecked()) {
                    digital3RadioButton.setChecked(false);
                } else {
                    digital3RadioButton.setChecked(true);
                }
                break;
            case R.id.digital_4_radio_button:
                if (digital4RadioButton.isChecked()) {
                    digital4RadioButton.setChecked(false);
                } else {
                    digital4RadioButton.setChecked(true);
                }
                break;
            case R.id.battery_threshold_button:
                try {
                    bitalino.battery(batteryThresholdSeekBar.getProgress());
                } catch (BITalinoException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.pwm_button:
                try {
                    bitalino.pwm(pwmSeekBar.getProgress());
                } catch (BITalinoException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.version_button:
                try {
                    bioplux.getVersion();
                } catch (BiopluxException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.description_button:
                try {
                    bioplux.getDescription();
                } catch (BiopluxException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.battery_button:
                try {
                    bioplux.getBattery();
                } catch (BiopluxException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    /*
     * Timer
     */
    private Timer timer;
    private long elapsedTime = 0;
    private long TIME_1_SECOND = 1000;

    private void startTimer() {
        elapsedTime = 0;

        stopTimer();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                elapsedTime += TIME_1_SECOND;
                elapsedTextView.setText(getTimeString(elapsedTime));
            }

        }, 0, TIME_1_SECOND);
    }

    private void stopTimer() {
        if (timer == null) {
            return;
        }

        timer.cancel();
        timer = null;
    }

    private String getTimeString(long time) {
        return String.format("%02d:%02d:%02d.%02d",
                TimeUnit.MILLISECONDS.toHours(time),
                TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
                TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)),
                TimeUnit.MILLISECONDS.toMillis(time) - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(time)));
    }

}
