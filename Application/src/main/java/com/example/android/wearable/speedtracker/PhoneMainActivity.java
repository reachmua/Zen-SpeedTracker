/*
 * Copyright (C) 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wearable.speedtracker;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import java.text.DateFormat;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.TimePicker;
import android.R.mipmap;


import com.example.android.wearable.speedtracker.common.LocationEntry;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// Import Zendrive SDK classes
import com.zendrive.sdk.Zendrive;
import com.zendrive.sdk.ZendriveConfiguration;
import com.zendrive.sdk.ZendriveDriveDetectionMode;
import com.zendrive.sdk.ZendriveDriverAttributes;
import com.zendrive.sdk.ZendriveOperationResult;
import com.zendrive.sdk.ZendriveOperationCallback;
import com.zendrive.sdk.ZendriveAccidentConfidence;

/**
 * The main activity for the handset application. When a watch device reconnects to the handset
 * app, the collected GPS data on the watch, if any, is synced up and user can see his/her track on
 * a map. This data is then saved into an internal database and the corresponding data items are
 * deleted.
 */
public class PhoneMainActivity extends AppCompatActivity implements
        DatePickerDialog.OnDateSetListener, OnMapReadyCallback {

    private static final String TAG = "PhoneMainActivity";
    private static final int BOUNDING_BOX_PADDING_PX = 50;
    private TextView mSelectedDateText;
    private GoogleMap mMap;
    private SupportMapFragment mMapFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        mSelectedDateText = (TextView) findViewById(R.id.selected_date);


        // Initialize the Zendrive SDK

        // Zendrive SDK setup
        String zendriveApplicationKey = "F4HzAXv8cj3fYrUFz0HuVS2erDaUzZ1r";   // Your Zendrive SDK Key

        ZendriveDriverAttributes driverAttributes = new ZendriveDriverAttributes();
        driverAttributes.setFirstName("Homer");
        driverAttributes.setLastName("Simpson");
        driverAttributes.setEmail("homer@springfield.com");
        driverAttributes.setPhoneNumber("14155557334");

        ZendriveConfiguration zendriveConfiguration = new ZendriveConfiguration(
                zendriveApplicationKey, "Maggie");   // A unique id of the driver specific to your application
        zendriveConfiguration.setDriverAttributes(driverAttributes);

        Zendrive.setup(
                this.getApplicationContext(),
                zendriveConfiguration,
                null,        // can be null.
                new ZendriveOperationCallback() {
                    @Override
                    public void onCompletion(ZendriveOperationResult result) {
                        if (result.isSuccess()) {  System.out.println("True");  }
                        else { System.out.println("UnTrue"); }
                    }
                }
        );
    }

    // Select a Date - No defaults, not particualrly relevant to exercise.


    public void onClick(View view) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        new DatePickerDialog(PhoneMainActivity.this, PhoneMainActivity.this,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    public void stopDrive(View view){
        //Turn off automatic drive detection in the SDK.
        Zendrive.setZendriveDriveDetectionMode(ZendriveDriveDetectionMode.AUTO_OFF,
                new ZendriveOperationCallback() {
                    @Override
                    public void onCompletion(ZendriveOperationResult result) {
                        if (result.isSuccess()) {  System.out.println("True");  }
                        else { System.out.println("UnTrue"); }
                    }
                }
        );

        stopStatusLabel=(TextView)findViewById(R.id.stop_status);
        stopStatusLabel.setText("SDK Stopped");

    }

    Calendar dateAndTime=Calendar.getInstance();
    DateFormat fmtDateAndTime=DateFormat.getDateTimeInstance();
    TextView dateAndTimeLabel;
    TextView stopStatusLabel;

    // Time and Date Picker Input

    // Notification Button.
     Button btn;

    private TimePickerDialog.OnTimeSetListener timeListener =new TimePickerDialog.OnTimeSetListener() {
        public void onTimeSet(TimePicker view, int hourOfDay,
                              int minute) {
            dateAndTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            dateAndTime.set(Calendar.MINUTE, minute);
            dateAndTime.set(Calendar.SECOND, 0);

            dateAndTimeLabel=(TextView)findViewById(R.id.selected_time);

            if (!isFutureTime(dateAndTime)){
                dateAndTimeLabel.setText("Please select a future time");
                return;
            }

            if (!isWithinHour(dateAndTime)) { // If user does not select a time within the next hour, inform user to select a different time.
                dateAndTimeLabel.setText("Please select a valid time within the next hour");
            } else {
                dateAndTimeLabel.setText(fmtDateAndTime.format(dateAndTime.getTime()));

                // Notifications

                // Turn on automatic drive detection in the SDK.
                int timeDifference = returnCalculateTimeDifferenceInSeconds(dateAndTime);
                System.out.println("====== Time Difference: " + timeDifference + "s");

                scheduleNotification(getNotification("SDK Started"), timeDifference);
                // scheduleNotification(getNotification("SDK Started"), 5000);


            }
        }
    };

    public int returnCalculateTimeDifferenceInSeconds(Calendar selectedDateTimeObject){
        long currentTime, selectedTime;
        selectedTime = selectedDateTimeObject.getTime().getTime() / 1000;
        currentTime = new java.util.Date().getTime() / 1000;
        return (int) Math.abs(currentTime - selectedTime);
    }

    public boolean isFutureTime(Calendar selectedDateTimeObject){
        long currentTime, selectedTime;
        selectedTime = selectedDateTimeObject.getTime().getTime() / 1000;
        currentTime = new java.util.Date().getTime() / 1000;
        if (selectedTime > currentTime) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isWithinHour(Calendar selectedDateTimeObject){
        // Convert Time Objects to Long
        long currentTime, selectedTime;
        selectedTime = selectedDateTimeObject.getTime().getTime() / 1000;
        currentTime = new java.util.Date().getTime() / 1000;
        //dateAndTimeLabel.setText("Time diff: " + (currentTime - selectedTime));
        if (Math.abs(currentTime - selectedTime) <= 3600){ // Number of seconds in an hour
            return true;
        } else {
            return false;
        }
        //dateAndTimeLabel.setText("Current Time: " + currentTime + " selected Time: " + selectedTime);

    }

    public void onTimeClick(View view) {

        new TimePickerDialog(
                PhoneMainActivity.this,
                timeListener,
                dateAndTime.get(Calendar.HOUR_OF_DAY),
                dateAndTime.get(Calendar.MINUTE),
                true).show();
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        // the following if-clause is to get around a bug that causes this callback to be called
        // twice
        if (view.isShown()) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            String date = DateUtils.formatDateTime(this, calendar.getTimeInMillis(),
                    DateUtils.FORMAT_SHOW_DATE);
            mSelectedDateText.setText(getString(R.string.showing_for_date, date));
            //showTrack(calendar);
        }
    }

    private void scheduleNotification(Notification notification, int delayInSeconds) {

        Intent notificationIntent = new Intent(this, NotificationPublisher.class);
        notificationIntent.putExtra(NotificationPublisher.NOTIFICATION_ID, 1);
        notificationIntent.putExtra(NotificationPublisher.NOTIFICATION, notification);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        long futureInMillis = SystemClock.elapsedRealtime() + (delayInSeconds * 1000);
        AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, futureInMillis, pendingIntent);
    }

    private Notification getNotification(String content) {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle("Scheduled Notification");
        builder.setContentText(content);
        builder.setSmallIcon(R.drawable.ic_launcher);
        return builder.build();
    }


    /**
     * An {@link android.os.AsyncTask} that is responsible for getting a list of {@link
     * com.example.android.wearable.speedtracker.common.LocationEntry} objects for a given day and
     * building a track from those points. In addition, it sets the smallest bounding box for the
     * map that covers all the points on the track.
     */
    private void showTrack(Calendar calendar) {
        new AsyncTask<Calendar, Void, Void>() {

            private List<LatLng> coordinates;
            private LatLngBounds bounds;

            @Override
            protected Void doInBackground(Calendar... params) {
                LocationDataManager dataManager = ((PhoneApplication) getApplicationContext())
                        .getDataManager();
                List<LocationEntry> entries = dataManager.getPoints(params[0]);
                if (entries != null && !entries.isEmpty()) {
                    coordinates = new ArrayList<LatLng>();
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    for (LocationEntry entry : entries) {
                        LatLng latLng = new LatLng(entry.latitude, entry.longitude);
                        builder.include(latLng);
                        coordinates.add(latLng);
                    }
                    bounds = builder.build();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mMap.clear();
                if (coordinates == null || coordinates.isEmpty()) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "No Entries found for that date");
                    }
                } else {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds,
                            BOUNDING_BOX_PADDING_PX));
                    mMap.addPolyline(new PolylineOptions().geodesic(true).addAll(coordinates));
                }
            }

        }.execute(calendar);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }
}