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

import static info.plux.api.bioplux.enums.Event.BATTERY_LEVEL_EVENT;
import static info.plux.api.bioplux.enums.Event.CLOCK_SYNCHRONIZATION;
import static info.plux.api.bioplux.enums.Event.DIGITAL_INPUT_CHANGE;
import static info.plux.api.bioplux.enums.Event.DISCONNECT;
import static info.plux.api.bioplux.enums.Event.GESTURE_FEATURES_EVENT;
import static info.plux.api.bioplux.enums.Event.I_2_C_EVENT;
import static info.plux.api.bioplux.enums.Event.ON_BODY_EVENT;
import static info.plux.api.bioplux.enums.Event.SCHEDULE_CHANGE;
import static info.plux.api.bioplux.enums.Event.SENSOR_ID_CHANGE;
import static info.plux.api.enums.States.DISCONNECTED;
import static info.plux.api.interfaces.Constants.ACTION_COMMAND_REPLY;
import static info.plux.api.interfaces.Constants.ACTION_EVENT_AVAILABLE;
import static info.plux.api.interfaces.Constants.ACTION_STATE_CHANGED;
import static info.plux.api.interfaces.Constants.DISCONNECT_EVENT;
import static info.plux.api.interfaces.Constants.EXTRA_COMMAND_REPLY;
import static info.plux.api.interfaces.Constants.EXTRA_EVENT;
import static info.plux.api.interfaces.Constants.EXTRA_EVENT_DATA;
import static info.plux.api.interfaces.Constants.EXTRA_STATE_CHANGED;
import static info.plux.api.interfaces.Constants.IDENTIFIER;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import info.plux.api.DeviceProperties;
import info.plux.api.PLUXDevice;
import info.plux.api.PLUXException;
import info.plux.api.bioplux.BiopluxCommunication;
import info.plux.api.bioplux.BiopluxCommunicationFactory;
import info.plux.api.bioplux.OnBiopluxError;
import info.plux.api.bioplux.enums.CommandError;
import info.plux.api.bioplux.enums.DisconnectEventType;
import info.plux.api.bioplux.enums.Event;
import info.plux.api.bioplux.objects.BiopluxFrame;
import info.plux.api.bioplux.objects.CommandReplyString;
import info.plux.api.bioplux.objects.DigitalInputChange;
import info.plux.api.bioplux.objects.EventData;
import info.plux.api.bioplux.objects.Source;
import info.plux.api.bioplux.objects.parameters.SetParameter;
import info.plux.api.bitalino.BITalinoCommunication;
import info.plux.api.bitalino.BITalinoCommunicationFactory;
import info.plux.api.bitalino.BITalinoDescription;
import info.plux.api.bitalino.BITalinoFrame;
import info.plux.api.bitalino.BITalinoState;
import info.plux.api.enums.States;
import info.plux.api.enums.TypeOfCommunication;
import info.plux.api.interfaces.Constants;
import info.plux.api.interfaces.OnDataAvailable;

public class DeviceActivity extends AppCompatActivity implements OnDataAvailable, OnBiopluxError, View.OnClickListener {
    private final String TAG = this.getClass().getSimpleName();

    public final static String EXTRA_DEVICE = "info.plux.android.sample.DeviceActivity.EXTRA_DEVICE";
    public final static String FRAME = "info.plux.android.sample.DeviceActivity.FRAME";
    public final static String ELAPSED_TIME_EVENT = "info.plux.android.sample.DeviceActivity.ELAPSED_TIME_EVENT";

    private int samplingRate = 1000;
    private float vcc = 3f; //change to the appropriate one according to device

    //Sources
    private boolean settingParameter = false;//fNIRS sensor
    private final List<Source> sources = new ArrayList<>();

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

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(bluetoothDevice.getAddress());
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        initView();
        setUIElements();

        handler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                final Bundle bundle = msg.getData();

                if (bundle.containsKey(FRAME)) {
                    final Parcelable frame = bundle.getParcelable(FRAME);

                    if (frame instanceof BiopluxFrame) { //biosignalsplux
                        biopluxResultsTextView.setText(frame.toString());

                    } else if (frame instanceof BITalinoFrame) { //BITalino
                        resultsTextView.setText(frame.toString());
                    }

                } else if (bundle.containsKey(ELAPSED_TIME_EVENT)) {
                    final long elapsedTime = bundle.getLong(ELAPSED_TIME_EVENT);

                    if (elapsedTextView == null) {
                        return;
                    }

                    elapsedTextView.setText(getTimeString(elapsedTime));
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
        if (item.getItemId() == R.id.menu_finish || item.getItemId() == android.R.id.home) {
            finish();
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
            isBioplux = !bluetoothDevice.getName().toLowerCase().contains("bitalino");
        } else {
            isBioplux = false;
        }

        TypeOfCommunication communication = TypeOfCommunication.getById(bluetoothDevice.getType());
        if (communication.equals(TypeOfCommunication.DUAL)) {
            communication = TypeOfCommunication.BTH;
        }

        if (isBioplux) {
            try {
                bioplux = BiopluxCommunicationFactory.getCommunication(bluetoothDevice,
                        this, this, this);
                bioplux.setConnectionControllerEnabled(false);
                bioplux.setDataStreamControllerEnabled(false);
                //uncomment to receive the data as an array instead of a object [only available for BTH]
                //bioplux.setBiopluxFrameEnabled(false);
            } catch (PLUXException e) {
                e.printStackTrace();
            }

        } else {
            try {
                bitalino = BITalinoCommunicationFactory.getCommunication(communication,
                        this, this);
                bitalino.setConnectionControllerEnabled(false);
                bitalino.setDataStreamControllerEnabled(true);
            } catch (PLUXException e) {
                e.printStackTrace();
            }

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

            final String identifier = intent.getStringExtra(IDENTIFIER);
            if (ACTION_STATE_CHANGED.equals(action)) {

                currentState = (States) intent.getSerializableExtra(EXTRA_STATE_CHANGED);

                stateTextView.setText(currentState.name());

                if (currentState.equals(DISCONNECTED)) {
                    biopluxResultsTextView.setText("");
                }
            } else if (ACTION_COMMAND_REPLY.equals(action)) {

                if (intent.hasExtra(EXTRA_COMMAND_REPLY) && (intent.getParcelableExtra(EXTRA_COMMAND_REPLY) != null)) {
                    final Parcelable parcelable = intent.getParcelableExtra(EXTRA_COMMAND_REPLY);

                    Log.d(TAG, "COMMAND REPLY " + parcelable.getClass());

                    if (parcelable instanceof DeviceProperties) {
                        final DeviceProperties deviceProperties = intent.getParcelableExtra(EXTRA_COMMAND_REPLY);
                        biopluxResultsTextView.setText(deviceProperties.toString());

                    } else if (parcelable instanceof PLUXDevice) {
                        final PLUXDevice pluxDevice = intent.getParcelableExtra(EXTRA_COMMAND_REPLY);
                        biopluxResultsTextView.setText(pluxDevice.toString());

                    } else if (parcelable instanceof EventData) { //biosignals
                        final EventData eventData = intent.getParcelableExtra(Constants.EXTRA_COMMAND_REPLY);

                        if (eventData.getEventDescription().equals(Constants.BATTERY_EVENT)) {

                            final float R1 = 69.8f, R2 = 30.0f;
                            final float batteryValue = (float) (((R1 + R2) / R2)
                                    * ((eventData.getBatteryLevel() * vcc ) / ((Math.pow(2, 11)))));

                            final float batteryLevel = convertBatteryVtoPercent(batteryValue);

                            biopluxResultsTextView.setText(String.valueOf(batteryLevel));

                        } else if (eventData.getEventDescription().equals(DISCONNECT_EVENT)) {
                            final DisconnectEventType disconnectEventType = (DisconnectEventType)
                                    intent.getSerializableExtra(EXTRA_EVENT_DATA);

                            Log.d(TAG, "Disconnect event: " + disconnectEventType.name());
                        }
                    } else if (parcelable instanceof CommandReplyString) { //biosignals
                        biopluxResultsTextView.setText(((CommandReplyString) parcelable).getCommandReply());

                    } else if (parcelable instanceof SetParameter) { //biosignals
                        if (settingParameter) {//fNIRS
                            settingParameter = false;
                            try {
                                bioplux.start(samplingRate, sources);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (parcelable instanceof BITalinoState) { //BITalino
                        Log.d(TAG, ((BITalinoState) parcelable).toString());
                        resultsTextView.setText(parcelable.toString());

                    } else if (parcelable instanceof BITalinoDescription) { //BITalino
                        isBITalino2 = ((BITalinoDescription) parcelable).isBITalino2();
                        resultsTextView.setText(String.format("isBITalino2: %b; FwVersion: %.1f",
                                isBITalino2, ((BITalinoDescription) parcelable).getFwVersion()));

                    }
                }
            } else if (ACTION_EVENT_AVAILABLE.equals(action)) {

                if (!intent.hasExtra(EXTRA_EVENT) || intent.getSerializableExtra(EXTRA_EVENT) == null) {
                    return;
                }

                final Event event = (Event) intent.getSerializableExtra(EXTRA_EVENT);

                if (event.equals(SENSOR_ID_CHANGE)) {

                } else if (event.equals(DIGITAL_INPUT_CHANGE)) {
                    final DigitalInputChange digitalInputChange = intent.getParcelableExtra(EXTRA_EVENT_DATA);
                    Log.d(TAG, digitalInputChange.toString());

                } else if (event.equals(SCHEDULE_CHANGE)) {

                } else if (event.equals(CLOCK_SYNCHRONIZATION)) {

                } else if (event.equals(I_2_C_EVENT)) {

                } else if (event.equals(GESTURE_FEATURES_EVENT)) {

                } else if (event.equals(DISCONNECT)) {
                    final DisconnectEventType disconnectEventType
                            = (DisconnectEventType) intent.getSerializableExtra(EXTRA_EVENT_DATA);
                    Log.d(TAG, disconnectEventType.name());
                } else if (event.equals(ON_BODY_EVENT)) {

                } else if (event.equals(BATTERY_LEVEL_EVENT)) {

                } else {
                    Log.e(TAG, "Unknown event");
                }
            }
        }
    };

    private IntentFilter makeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_STATE_CHANGED);
        intentFilter.addAction(ACTION_EVENT_AVAILABLE);
        intentFilter.addAction(ACTION_COMMAND_REPLY);
        return intentFilter;
    }

    /*
     * Callbacks
     */


    @Override
    public void onDataAvailable(Parcelable frame) {
        if (frame instanceof BiopluxFrame) {
            final BiopluxFrame biopluxFrame = (BiopluxFrame) frame;

            if (biopluxFrame.getSequence() % samplingRate == 0) {
                Log.d(TAG, biopluxFrame.toString());

                Message message = handler.obtainMessage();
                Bundle bundle = new Bundle();
                bundle.putParcelable(FRAME, biopluxFrame);
                message.setData(bundle);
                handler.sendMessage(message);
            }
        } else { //BITalinoFrame
            Message message = handler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putParcelable(FRAME, frame);
            message.setData(bundle);
            handler.sendMessage(message);
        }
    }

    @Override
    public void onDataAvailable(String identifier, int sequence, int[] data, int digitalInput) {

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
    public void onClick(View view) {
        int viewId = view.getId();

        if (viewId == R.id.connect_button) {
            connectDevice();

        } else if (viewId == R.id.disconnect_button) {
            disconnectDevice();

        } else if (viewId == R.id.start_button) {
            startAcquisition();

        } else if (viewId == R.id.stop_button) {
            stopAcquisition();

        } else if (viewId == R.id.state_button) {
            checkState();

        } else if (viewId == R.id.trigger_button) {
            triggerDevice();

        } else if (viewId == R.id.digital_1_radio_button) {
            digital1RadioButton.setChecked(!isDigital1RadioButtonChecked);
            isDigital1RadioButtonChecked = digital1RadioButton.isChecked();

        } else if (viewId == R.id.digital_2_radio_button) {
            digital2RadioButton.setChecked(!isDigital2RadioButtonChecked);
            isDigital2RadioButtonChecked = digital2RadioButton.isChecked();

        } else if (viewId == R.id.digital_3_radio_button) {
            digital3RadioButton.setChecked(!digital3RadioButton.isChecked());

        } else if (viewId == R.id.digital_4_radio_button) {
            digital4RadioButton.setChecked(!digital4RadioButton.isChecked());

        } else if (viewId == R.id.battery_threshold_button) {
            try {
                bitalino.battery(batteryThresholdSeekBar.getProgress());
            } catch (PLUXException e) {
                e.printStackTrace();
            }

        } else if (viewId == R.id.pwm_button) {
            try {
                bitalino.pwm(pwmSeekBar.getProgress());
            } catch (PLUXException e) {
                e.printStackTrace();
            }

        } else if (viewId == R.id.version_button) {
            try {
                bioplux.getVersion();
            } catch (PLUXException e) {
                e.printStackTrace();
            }

        } else if (viewId == R.id.description_button) {
            try {
                bioplux.getDescription();
            } catch (PLUXException e) {
                e.printStackTrace();
            }

        } else if (viewId == R.id.battery_button) {
            try {
                bioplux.getBattery();
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        }
    }

    private void triggerDevice() {
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
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkState() {
        if (!isBioplux) {
            try {
                bitalino.state();
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopAcquisition() {
        stopTimer();

        if (isBioplux) {
            sources.clear();
            try {
                bioplux.stop();
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        } else {
            try {
                bitalino.stop();
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        }
    }

    private void startAcquisition() {
        startTimer();

        if (isBioplux) {
            startBioplux();

        } else {
            try {
                bitalino.start(samplingRate, new int[]{0, 1, 2, 3, 4, 5});
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        }
    }

    private void connectDevice() {
        if (isBioplux) {
            try {
                bioplux.connect(bluetoothDevice.getAddress());
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        } else {
            try {
                bitalino.connect(bluetoothDevice.getAddress());
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        }
    }

    private void disconnectDevice() {
        stopTimer();

        if (isBioplux) {
            try {
                bioplux.disconnect();
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        } else {
            try {
                bitalino.disconnect();
            } catch (PLUXException e) {
                e.printStackTrace();
            }
        }
    }


    private void startBioplux() {
        /*
         *------------------------------SOURCES CONFIGURATION---------------------------------------
         *
         * BIOSIGNALSPLUX:
         * * Add as many sources as needed (1 to 8 sources).
         * * To initialize a source indicate the correspondent hub's port [1-8].
         *
         * =========================================================================================
         * For the next devices add one source per port that you intend to use.
         * Take special care for ports with more than one channel - check example below.
         *
         * ----------------------channelMask - sensors to acquire in a bitmask--------------------
         * Example for biosignalspluxSolo in which port 11 corresponds to 6 channels - 3 acc + 3 mag
         * 000111- 0x07 - to acquire just from all acc channels
         * 111000- 0x38 - to acquire just from all mag channels
         * 111111- 0x3F - to acquire from all 6 channels
         * =========================================================================================
         *
         *
         * BIOSIGNALSPLUXSOLO:
         * * port 1- micro; port 2- analog channel; port 11 - acc/mag (3 channels/3 channels).
         *
         * MUSCLEBAN
         * * port 1 - emg; port 2 - acc/mag (3 channels/3 channels).
         * * If intended use freqDivisor - size of the window used for the envelope calculation.
         *
         * FNIRS
         * *port 9 - infrared/red (4 channels-R1,IR1,R2,IR2) ; port 11 - acc (3 channels).
         * *It is necessary to set LED's intensity. To do so use:
         * *setParameter(int port, int paramAdd, byte[] paramArray),
         * *where paramAdd=3 and paramArray its a byte array indicating LED's intensity
         * *paramArray[0] = RED LED intensity
         * *paramArray[1] = IR LED's intensity
         *
         * ---------------------------------------------------------------------------------------*/

        //add the necessary sources following the instructions above
        sources.add(new Source(1, 16, (byte) 0x01, 1));
        sources.add(new Source(2, 16, (byte) 0x01, 1));
        sources.add(new Source(3, 16, (byte) 0x01, 1));
        sources.add(new Source(4, 16, (byte) 0x01, 1));
        sources.add(new Source(9, 16, (byte) 0x03, 1));

        //Comment this try-catch block for fNIRS and set the flag settingParameter to true
        try {
            bioplux.start(samplingRate, sources);
        } catch (PLUXException e) {
            e.printStackTrace();
        }

        //Uncomment this try-catch block for fNIRS!
//        try {
//            int paramAdd=3;
//            byte[] paramArray = new byte[]{(byte) 0x50, (byte) 0x28};
//            bioplux.setParameter(9, paramAdd, paramArray);
//            settingParameter = true;
//        } catch (PLUXException e) {
//            e.printStackTrace();
//        }
    }

    /*
     * Timer
     */
    private Timer timer;
    private long elapsedTime = 0;
    private final long TIME_1_SECOND = 1000;

    private void startTimer() {
        elapsedTime = 0;

        stopTimer();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                elapsedTime += TIME_1_SECOND;

                Message message = handler.obtainMessage();
                Bundle bundle = new Bundle();
                bundle.putLong(ELAPSED_TIME_EVENT, elapsedTime);
                message.setData(bundle);
                handler.sendMessage(message);
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

    /*
     * Battery
     */

    private float convertBatteryVtoPercent(float batteryValue) {
        // convert battery voltage to charge percentage (LiPo batteries)
        float[] voltage = new float[]{4.2f, 4.04f, 3.8f, 3.68f, 3.6f, 3.2f};
        float[] dischargePercentage = new float[]{0, 10, 50, 90, 94, 100};

        float b, m;

        if (batteryValue > voltage[0]) {
            m = 1;
            b = batteryValue;
        } else if (batteryValue > voltage[1]) {
            m = (voltage[1] - voltage[0]) / (dischargePercentage[1] - dischargePercentage[0]);
            b = voltage[1] - (m * dischargePercentage[1]);
        } else if (batteryValue > voltage[2]) {
            m = (voltage[2] - voltage[1]) / (dischargePercentage[2] - dischargePercentage[1]);
            b = voltage[2] - (m * dischargePercentage[2]);
        } else if (batteryValue > voltage[3]) {
            m = (voltage[3] - voltage[2]) / (dischargePercentage[3] - dischargePercentage[2]);
            b = voltage[3] - (m * dischargePercentage[3]);
        } else if (batteryValue > voltage[4]) {
            m = (voltage[4] - voltage[3]) / (dischargePercentage[4] - dischargePercentage[3]);
            b = voltage[4] - (m * dischargePercentage[4]);
        } else if (batteryValue > voltage[5]) {
            m = (voltage[5] - voltage[4]) / (dischargePercentage[5] - dischargePercentage[4]);
            b = voltage[5] - (m * dischargePercentage[5]);
        } else {
            m = 1;
            b = batteryValue - 100;
        }

        return 100 - (batteryValue - b) / m;
    }

}
