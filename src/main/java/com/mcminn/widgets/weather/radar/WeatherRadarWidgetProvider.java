package com.mcminn.widgets.weather.radar;

import com.mcminn.R;

import android.widget.RemoteViews;
import java.net.URL;
import java.net.MalformedURLException;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.content.Context;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.os.IBinder;
import android.os.AsyncTask;
import android.app.Service;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.util.Log;

public class WeatherRadarWidgetProvider extends AppWidgetProvider
{
    private static final String TAG = WeatherRadarWidgetProvider.class.getSimpleName();
    private static final String LAUNCH_PACKAGE = "com.appdlab.radarexpress";
    private static final float DEFAULT_SCALE = 2.0f;
    private static final String DEFAULT_STATION = "DTX";
    private static final String UPDATE_DATE_FORMAT = "hh:mm a";

    private static final String BASE_URL = "http://radar.weather.gov/";
    private static final String BASE_RIDGE_URL = BASE_URL + "ridge/";
    private static final String BASE_OVERLAYS_URL = BASE_RIDGE_URL + "Overlays/";
    private static final String LEGEND = BASE_RIDGE_URL + "Legend/N0R/%s_N0R_Legend_0.gif";
    private static final String TOPO = BASE_OVERLAYS_URL + "Topo/Short/%s_Topo_Short.jpg";
    private static final String COUNTY = BASE_OVERLAYS_URL + "County/Short/%s_County_Short.gif";
    private static final String RIVERS = BASE_OVERLAYS_URL + "Rivers/Short/%s_Rivers_Short.gif";
    private static final String HIGHWAYS = BASE_OVERLAYS_URL + "Highways/Short/%s_Highways_Short.gif";
    private static final String CITIES = BASE_OVERLAYS_URL + "Cities/Short/%s_City_Short.gif";
    private static final String WARNINGS = BASE_RIDGE_URL + "Warnings/Short/%s_Warnings_0.gif";
    private static final String BASE_REFLECTIVITY = BASE_RIDGE_URL + "RadarImg/N0R/%s_N0R_0.gif";

    private static SimpleDateFormat sdf = new SimpleDateFormat(UPDATE_DATE_FORMAT);
    private static Bitmap[] bmps = new Bitmap[8];

    private static Bitmap getStationBmp(String format, String station) throws MalformedURLException, IOException
    {
        String formattedURL = String.format(format, station);
        URL url = new URL(formattedURL);

        // Get the bytes of the image rather than just decoding the stream due to a GIF transparency
        // bug in KitKat.  decodeStream strips out the transparency.
        InputStream in = url.openConnection().getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            byte[] buf = new byte[10240];
            int len = 0;
            while((len = in.read(buf)) != -1)
            {
                baos.write(buf, 0, len);
            }
        }
        finally
        {
            in.close();
            baos.close();
        }

        byte[] arr = baos.toByteArray();
        Bitmap rtrn = null;
        // If we have a GIF, decode it using the GifDecoder to get around a broken decode function
        // in KitKat.  Frustrating.
        if(format.toLowerCase().endsWith(".gif"))
        {
            GifDecoder d = new GifDecoder();
            d.read(arr);
            d.advance();
            rtrn = d.getNextFrame();
        }
        else
        {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            rtrn = BitmapFactory.decodeByteArray(arr, 0, arr.length, opts);
        }

        return rtrn;
    }

    private static void getImages(String station) throws MalformedURLException, IOException
    {
        bmps[0] = getStationBmp(TOPO, station);
        bmps[1] = getStationBmp(BASE_REFLECTIVITY, station);
        bmps[2] = getStationBmp(COUNTY, station);
        bmps[3] = getStationBmp(HIGHWAYS, station);
//        bmps[4] = getStationBmp(RIVERS, station);
        bmps[5] = getStationBmp(CITIES, station);
        bmps[6] = getStationBmp(WARNINGS, station);
//        bmps[7] = getStationBmp(LEGEND, station);
    }

    private static Bitmap combineImages(float scale)
    {
        if(scale < 1.0f)
        {
            scale = 1.0f;
        }

        int w = bmps[0].getWidth();
        int h = bmps[0].getHeight();

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);

        Matrix matrix = new Matrix();
        if(scale > 1.0f)
        {
            float scaledW = scale * w;
            float scaledH = scale * h;
            matrix.setScale(scale, scale);
            matrix.postTranslate((w - scaledW) / 2.0f, (h - scaledH) / 2.0f);
        }

        for(int x = 0;x < bmps.length;x++)
        {
            if(bmps[x] == null)
            {
                continue;
            }

            // Skip the legend bitmap scaling.
            if(x == bmps.length - 1)
            {
                canvas.drawBitmap(bmps[x], new Matrix(), p);
            }
            else
            {
                canvas.drawBitmap(bmps[x], matrix, p);
            }
        }

        return bmp;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
    {
        context.startService(new Intent(context, UpdateService.class));
    }

    public static class UpdateService extends Service
    {
        @Override
        public void onStart(Intent intent, int startId)
        {
            new GetImageAsyncTask(this, DEFAULT_SCALE).execute(DEFAULT_STATION);
        }

        @Override
        public IBinder onBind(Intent intent)
        {
            return null;
        }
    }

    private static class GetImageAsyncTask extends AsyncTask<String, Void, RemoteViews>
    {
        private Context context;
        private float scale;

        public GetImageAsyncTask(Context context, float scale)
        {
            this.context = context;
            this.scale = scale;
        }

        protected RemoteViews doInBackground(String... codes)
        {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.weather_radar_widget);
            try
            {
                getImages(codes[0]);
                rv.setImageViewBitmap(R.id.img, combineImages(scale));
                rv.setTextViewText(R.id.text, sdf.format(new Date()));

                Intent intent = context.getPackageManager().getLaunchIntentForPackage(LAUNCH_PACKAGE);
                PendingIntent pIntent = PendingIntent.getActivity(context, 0, intent, 0);
                rv.setOnClickPendingIntent(R.id.container, pIntent);
            }
            catch(Exception e)
            {
                // On error the remote view is left unchanged.
                Log.e(TAG, "Could not load image view.", e);
            }

            return rv;
        }

        protected void onPostExecute(RemoteViews rv)
        {
            ComponentName thisWidget = new ComponentName(context, WeatherRadarWidgetProvider.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(thisWidget, rv);
        }
    }
}
