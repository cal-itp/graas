package com.example.gpstest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

@RequiresApi(api = Build.VERSION_CODES.O)
public class GraasService extends Service{
    private GraasBinder binder = new GraasBinder();
    private GPSTracker gps;

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }

    public String getPositionString() {
        return gps.getPositionString();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("gostest", "GraasService.onStartCommand()");

        /*Notification notification = new Notification(R.drawable.icon, getText(R.string.ticker_text),
                System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, GraasService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.notification_title),
                getText(R.string.notification_message), pendingIntent);*/

        String channelID = createNotificationChannel("graas", "GRaas Foreground Service");
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, channelID);
        Notification notification = nb.build();
        notification.icon = R.drawable.ic_launcher_foreground;
        int id = 1;
        startForeground(id, notification);

        gps = new GPSTracker(this);

        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("gpstest", "GraasService.onBind()");
        return binder;
    }

    public class GraasBinder extends Binder {
        GraasService getService() {
            return GraasService.this;
        }
    }
}
