package com.example.thesis;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.thesis.filters.ExponentialMovingAverageFilter;
import com.example.thesis.filters.KalmanFilter;
import com.example.thesis.filters.SimpleMovingAverageFilter;

import org.w3c.dom.Text;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TimeKeepingFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    /**
     * A simple {@link Fragment} subclass.
     * Use the {@link TimeKeepingFragment#newInstance} factory method to
     * create an instance of this fragment.
     */

    private KalmanFilter kmnFilter = new KalmanFilter();
    private ExponentialMovingAverageFilter emaFilter = new ExponentialMovingAverageFilter();
    private SimpleMovingAverageFilter smaFilter = new SimpleMovingAverageFilter();
    private ArrayList<Double> rssiResults = new ArrayList<Double>();
    private ArrayList<String> timestamps = new ArrayList<String>();

    private boolean isScanning = false;
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private MutableLiveData<String> rssiData = new MutableLiveData<>();

    public TimeKeepingFragment() {
        // Required empty public constructor
    }

    TrackManager tm;
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String deviceAddress = result.getDevice().getAddress();
            tm.addUnsortedRSSI(deviceAddress, result.getRssi());
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("BLE", "Scan failed with error code: " + errorCode);
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment TimeKeepingFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static TimeKeepingFragment newInstance(String param1, String param2) {
        TimeKeepingFragment fragment = new TimeKeepingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        tm = new TrackManager(requireContext());
        tm.initializeTracks();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_time_keeping, container, false);

        // Buttons for selection of the individual filtering algorithms
        TextView textScanning = v.findViewById(R.id.text_is_scanning);
        Button btnKalman = v.findViewById(R.id.button_kalman);
        Button btnEMA = v.findViewById(R.id.button_ema);
        Button btnSMA = v.findViewById(R.id.button_sma);
        Button btnLow = v.findViewById(R.id.button_low_latency);
        Button btnBal = v.findViewById(R.id.button_balanced);
        Button btnHigh = v.findViewById(R.id.button_low_power);
        TextView textTimestamps = v.findViewById(R.id.text_timestamps);
        TextView textTimestamps2 = v.findViewById(R.id.text_timestamps2);
        TextView textStartSignals = v.findViewById(R.id.text_start_signals);
        TextView textFinishSignals = v.findViewById(R.id.text_finish_signals);
        btnKalman.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isScanning) {
                    for (Track track : tm.getTracks()) {
                        ArrayList<Double> filteredStart = new ArrayList<>();
                        ArrayList<Double> filteredFinish = new ArrayList<>();

                        ArrayList<Double> startResults = tm.getTrackResult(track, "start");
                        ArrayList<Double> finishResults = tm.getTrackResult(track, "finish");

                        try {
                            filteredStart = kmnFilter.filterData(startResults, true);
                            filteredFinish = kmnFilter.filterData(finishResults, false);

                        } catch (Exception e) {
                            rssiData.setValue("Filtering failed");
                        }
                        Timestamp date = new Timestamp(System.currentTimeMillis());

                        try {
                            double closestStart = Collections.max(filteredStart);
                            double closestFinish = Collections.max(filteredFinish);
                            int closestValueStart = filteredStart.indexOf(closestStart);
                            int closestValueFinish = filteredFinish.indexOf(closestFinish);

                            ArrayList<String> startTimes = tm.getTrackTimestamps(track, "start");
                            ArrayList<String> finishTimes = tm.getTrackTimestamps(track, "finish");

                            Timestamp start = Timestamp.valueOf(startTimes.get(closestValueStart));
                            Timestamp finish = Timestamp.valueOf(finishTimes.get(closestValueFinish));
                            long elapsedTime = finish.getTime() - start.getTime();
                            textScanning.setText(String.valueOf(elapsedTime));
                            textTimestamps.setText(String.valueOf(start));
                            textTimestamps2.setText(String.valueOf(finish));
                            textStartSignals.setText(String.valueOf(filteredStart.size()));
                            textFinishSignals.setText(String.valueOf(filteredFinish.size()));
                        } catch (Exception e) {
                            //rssiData.setValue("Getting time failed" + filtered.toString());
                            textTimestamps.setText(timestamps.toString());
                        }
                    }


                }
            }
        });

        btnEMA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isScanning) {
                    for (Track track : tm.getTracks()) {
                        ArrayList<Double> filteredStart = new ArrayList<>();
                        ArrayList<Double> filteredFinish = new ArrayList<>();

                        ArrayList<Double> startResults = tm.getTrackResult(track, "start");
                        ArrayList<Double> finishResults = tm.getTrackResult(track, "finish");

                        try {
                            filteredStart = emaFilter.filterData(startResults, true);
                            filteredFinish = emaFilter.filterData(finishResults, false);

                        } catch (Exception e) {
                            rssiData.setValue("Filtering failed");
                        }
                        Timestamp date = new Timestamp(System.currentTimeMillis());
                        try {
                            double closestStart = Collections.max(filteredStart);
                            double closestFinish = Collections.max(filteredFinish);
                            int closestValueStart = filteredStart.indexOf(closestStart);
                            int closestValueFinish = filteredFinish.indexOf(closestFinish);

                            ArrayList<String> startTimes = tm.getTrackTimestamps(track, "start");
                            ArrayList<String> finishTimes = tm.getTrackTimestamps(track, "finish");

                            Timestamp start = Timestamp.valueOf(startTimes.get(closestValueStart));
                            Timestamp finish = Timestamp.valueOf(finishTimes.get(closestValueFinish));
                            long elapsedTime = finish.getTime() - start.getTime();
                            textScanning.setText(String.valueOf(elapsedTime));
                            textTimestamps.setText(String.valueOf(start));
                            textTimestamps2.setText(String.valueOf(finish));
                        } catch (Exception e) {
                            //rssiData.setValue("Getting time failed" + filtered.toString());
                            textTimestamps.setText(timestamps.toString());
                        }
                    }

                }
            }
        });

        Button btnSave = v.findViewById(R.id.button_save_results);
        btnSave.setOnClickListener(view -> {
            String filterUsed = "Kalman"; // Or "EMA", "SMA" — set this dynamically

            ResultsLabelFragment dialog = new ResultsLabelFragment(filterUsed, new ResultsLabelFragment.OnNameEnteredListener() {
                @Override
                public void onNameEntered(String label) {
                    // Example: fullName = "Kalman_MyLabel"
                    saveToDownloadsWithMediaStore(requireContext(), kmnFilter.getRecentResultsStart(), "kmn_values_start_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), kmnFilter.getRecentResultsFinish(), "kmn_values_finish_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), emaFilter.getRecentResultsStart(), "ema_values_start_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), emaFilter.getRecentResultsFinish(), "ema_values_finish_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), smaFilter.getRecentResultsStart(), "sma_values_start_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), smaFilter.getRecentResultsFinish(), "sma_values_finish_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), tm.getTrackResult(tm.getTracks().get(0), "start"), "unfiltered_values_start_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), tm.getTrackResult(tm.getTracks().get(0), "finish"), "unfiltered_values_finish_" + label);

                    saveToDownloadsWithMediaStore(requireContext(), tm.getTrackTimestamps(tm.getTracks().get(0), "start"), "rssi_values_start_times_" + label);
                    saveToDownloadsWithMediaStore(requireContext(), tm.getTrackTimestamps(tm.getTracks().get(0), "finish"), "rssi_values_finish_times_" + label);

                }
            });

            dialog.show(getParentFragmentManager(), "SaveNameDialog");
        });

        btnSMA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isScanning) {
                    for (Track track : tm.getTracks()) {
                        ArrayList<Double> filteredStart = new ArrayList<>();
                        ArrayList<Double> filteredFinish = new ArrayList<>();

                        ArrayList<Double> startResults = tm.getTrackResult(track, "start");
                        ArrayList<Double> finishResults = tm.getTrackResult(track, "finish");

                        try {
                            filteredStart = smaFilter.filterData(startResults, true);
                            filteredFinish = smaFilter.filterData(finishResults, false);

                        } catch (Exception e) {
                            rssiData.setValue("Filtering failed");
                        }
                        Timestamp date = new Timestamp(System.currentTimeMillis());
                        try {
                            double closestStart = Collections.max(filteredStart);
                            double closestFinish = Collections.max(filteredFinish);
                            int closestValueStart = filteredStart.indexOf(closestStart);
                            int closestValueFinish = filteredFinish.indexOf(closestFinish);

                            ArrayList<String> startTimes = tm.getTrackTimestamps(track, "start");
                            ArrayList<String> finishTimes = tm.getTrackTimestamps(track, "finish");

                            Timestamp start = Timestamp.valueOf(startTimes.get(closestValueStart));
                            Timestamp finish = Timestamp.valueOf(finishTimes.get(closestValueFinish));
                            long elapsedTime = finish.getTime() - start.getTime();
                            textScanning.setText(String.valueOf(elapsedTime));
                            textTimestamps.setText(String.valueOf(start));
                            textTimestamps2.setText(String.valueOf(finish));
                        } catch (Exception e) {
                            //rssiData.setValue("Getting time failed" + filtered.toString());
                            textTimestamps.setText(timestamps.toString());
                        }
                    }


                }
            }
        });
//        btnLow.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                scanSettings = new ScanSettings.Builder()
//                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // or BALANCED, LOW_POWER, etc.
//                        .build();
//            }
//        });
//        btnBal.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                scanSettings = new ScanSettings.Builder()
//                        .setScanMode(ScanSettings.SCAN_MODE_BALANCED) // or BALANCED, LOW_POWER, etc.
//                        .build();
//            }
//        });
//        btnHigh.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                scanSettings = new ScanSettings.Builder()
//                        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // or BALANCED, LOW_POWER, etc.
//                        .build();
//            }
//        });
        Button btnTracks = v.findViewById(R.id.button_tracks);
        btnTracks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavController navController = Navigation.findNavController(view);
                navController.navigate(R.id.trackListFragment);
            }
        });

        Button btnStopwatch = v.findViewById(R.id.button_stopwatch);
        btnStopwatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavController navController = Navigation.findNavController(view);
                navController.navigate(R.id.stopwatchFragment);
            }
        });

        // Initialization of the Bluetooth classes
        BluetoothManager bluetoothManager = (BluetoothManager) requireContext().getSystemService(getContext().BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        Button btnStartScan = v.findViewById(R.id.button_start_scan);
        btnStartScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isScanning) {
                    startScan();
                } else {
                    stopScan();
                    tm.sortResults();
                    for (Track track : tm.getTracks()) {

                        ArrayList<Double> startResults = tm.getTrackResult(track, "start");
                        ArrayList<Double> finishResults = tm.getTrackResult(track, "finish");

//                        saveToDownloadsWithMediaStore(requireContext(), filteredStart, "sma_values_start" + date);
//                        saveToDownloadsWithMediaStore(requireContext(), filteredFinish, "sma_values_start" + date);
//                        saveToDownloadsWithMediaStore(requireContext(), tm.getTrackTimestamps(track, "start"), "rssi_values_start_times" + date);
//                        saveToDownloadsWithMediaStore(requireContext(), tm.getTrackTimestamps(track, "finish"), "rssi_values_finish_times" + date);
                        try {
                            double closestStart = Collections.max(startResults);
                            double closestFinish = Collections.max(finishResults);
                            int closestValueStart = startResults.indexOf(closestStart);
                            int closestValueFinish = finishResults.indexOf(closestFinish);

                            ArrayList<String> startTimes = tm.getTrackTimestamps(track, "start");
                            ArrayList<String> finishTimes = tm.getTrackTimestamps(track, "finish");

                            Timestamp start = Timestamp.valueOf(startTimes.get(closestValueStart));
                            Timestamp finish = Timestamp.valueOf(finishTimes.get(closestValueFinish));
                            long elapsedTime = finish.getTime() - start.getTime();
                            textScanning.setText(String.valueOf(elapsedTime));
                            textTimestamps.setText(String.valueOf(start));
                            textTimestamps2.setText(String.valueOf(finish));
                        } catch (Exception e) {
                            textTimestamps.setText(timestamps.toString());
                        }
                    }
                }

            }
        });

        // Observer for the RSSI values so that the text displaying the current measured RSSI can
        // be updated in real-time
        final Observer<String> rssiObserver = new Observer<String>() {
            @Override
            public void onChanged(String newRssi) {
                textScanning.setText(newRssi);
            }
        };
        rssiData.observe(getViewLifecycleOwner(), rssiObserver);
        // Inflate the layout for this fragment
        return v;
    }

    private void startScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            if (bluetoothLeScanner != null) {
                ScanSettings scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // or BALANCED, LOW_POWER, etc.
                        .build();
                ScanFilter filter = new ScanFilter.Builder()
                        .build();
                List<ScanFilter> filters = new ArrayList<>();
                filters.add(filter);
                // Starts the BLE scanner using the callback function to define wanted behaviour upon receiving a signal
                bluetoothLeScanner.startScan(filters, scanSettings, scanCallback);
                isScanning = true;
                Log.w("TAG", "Bluetooth scanning started.");
            } else {
                Log.e("TAG", "Bluetooth LE Scanner is null.");
            }
        } else {
            requestPermissions(new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void stopScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            if (bluetoothLeScanner != null) {
                // Starts the BLE scanner using the callback function to define wanted behaviour upon receiving a signal
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                Log.w("TAG", "Bluetooth scanning stopped.");
            } else {
                Log.e("TAG", "Bluetooth LE Scanner is null.");
            }
        } else {
            requestPermissions(new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_CODE_PERMISSIONS);
        }
    }

    public <T> void saveToDownloadsWithMediaStore(Context context, List<T> data, String filename) {
        String mimeType = "text/plain";
        String relativeLocation = Environment.DIRECTORY_DOWNLOADS;

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        contentValues.put(MediaStore.Downloads.RELATIVE_PATH, relativeLocation);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

        if (uri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                for (T item : data) {
                    String line = item.toString() + "\n";
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                }
                outputStream.flush();

                Log.d("MediaStore", "Saved to: " + uri.toString());
            } catch (IOException e) {
                Log.e("MediaStore", "Write failed: " + e.getMessage());
            }
        } else {
            Log.e("MediaStore", "Failed to create MediaStore entry");
        }
    }
}