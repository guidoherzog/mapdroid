/*
 * This file is part of Mapdroid.
 * Copyright 2009 Ted Percival <ted@tedp.id.au>.
 *
 * Mapdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Mapdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Mapdroid. If not, see <http://www.gnu.org/licenses/>.
 */
package au.id.tedp.mapdroid;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.String;
import java.net.URL;
import org.apache.http.impl.client.DefaultHttpClient;

public class Picker extends Activity
{
    private ArrayAdapter<RoutePoint> raa;

    private void setMapLocation(float latitude, float longitude) {
        OSMMapView map = (OSMMapView) findViewById(R.id.mapView);
        map.setCenterCoords(latitude, longitude);
    }

    public static final String SAVED_LATITUDE_KEY = "map_center_latitude";
    public static final String SAVED_LONGITUDE_KEY = "map_center_longitude";
    public static final String SAVED_ZOOM_KEY = "map_zoom";

    /* XXX: This should really be project-wide and all location-related code
     * modified to adopt it. */
    private class MapLocation extends Location {
        private int mZoom;

        public MapLocation(Location l, int zoom) {
            super(l);
            mZoom = zoom;
        }

        public int getZoom() {
            return mZoom;
        }

        public void setZoom(int zoom) {
            mZoom = zoom;
        }

        public float getLatitudeAsFloat() {
            return (float)getLatitude();
        }

        public float getLongitudeAsFloat() {
            return (float)getLongitude();
        }
    }

    private MapLocation getInitialLocation(Bundle savedState) {
        double newLat, newLong;
        int newZoom;

        // Default location: Germany, from a high zoom level.
        // OpenStreetMap is big in Germany.
        newLat = 52.5;
        newLong = 13.4;
        newZoom = 2;

        SharedPreferences settings = getPreferences(MODE_PRIVATE);

        if (savedState != null) {
            newLat = savedState.getFloat(SAVED_LATITUDE_KEY, (float)newLat);
            newLong = savedState.getFloat(SAVED_LONGITUDE_KEY, (float)newLong);
            newZoom = savedState.getInt(SAVED_ZOOM_KEY, newZoom);
        } else if (settings != null) {
            newLat = settings.getFloat(SAVED_LATITUDE_KEY, (float)newLat);
            newLong = settings.getFloat(SAVED_LONGITUDE_KEY, (float)newLong);
            newZoom = settings.getInt(SAVED_ZOOM_KEY, newZoom);
        }

        MapLocation l = new MapLocation(new Location("default"), newZoom);
        l.setLatitude(newLat);
        l.setLongitude(newLong);
        return l;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedState)
    {
        super.onCreate(savedState);
        setContentView(R.layout.main);

        MapLocation initloc = getInitialLocation(savedState);

        OSMMapView map = (OSMMapView) findViewById(R.id.mapView);
        /* TODO: Combine these two set* calls */
        map.setCenterCoords((float)initloc.getLatitude(), (float)initloc.getLongitude());
        map.setZoom(initloc.getZoom());
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = settings.edit();

        OSMMapView map = (OSMMapView) findViewById(R.id.mapView);

        ed.putFloat(SAVED_LATITUDE_KEY, map.getLatitude());
        ed.putFloat(SAVED_LONGITUDE_KEY, map.getLongitude());
        ed.putInt(SAVED_ZOOM_KEY, map.getZoom());
        ed.commit();

        map.stopLocationUpdates(this);
    }

    // XXX: Not sure whether this should be onSaveInstanceState or onPause
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        OSMMapView map = (OSMMapView) findViewById(R.id.mapView);
        if (map == null)
            return;

        outState.putFloat(SAVED_LATITUDE_KEY, map.getLatitude());
        outState.putFloat(SAVED_LONGITUDE_KEY, map.getLongitude());
    }

    @Override
    public void onDestroy() {
        //Debug.stopMethodTracing();
        super.onDestroy();
    }

    // FIXME: Replace this with overlay handling like the Maps app
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        OSMMapView map = (OSMMapView) findViewById(R.id.mapView);
        int change = 0;

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            change = -1;
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            change = +1;

        if (change != 0) {
            map.setZoom(map.getZoom() + change);
            return true;
        }

        // Propagate to the default implementation to get regular back-button
        // behavior.
        return super.onKeyDown(keyCode, event);
    }

    public Location getLastLocation(Context ctx) {
        LocationManager locmgr = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        Location l = locmgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (l != null)
            return l;
        return locmgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    }

    // We probably want to require high-res (GPS) location rather than low-res
    // but this will do for now.
    public String getLastLocationAsString(Context ctx) {
        Location loc = getLastLocation(ctx);

        if (loc == null)
            return null;

        return String.format("%f,%f", loc.getLatitude(), loc.getLongitude());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater= getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.my_location:
            Location loc = getLastLocation((Context)this);
            if (loc == null) {
                Log.d("Mapdroid", "Location is null");
            } else {
                Log.d("Mapdroid", "Location is not null");
            }
            // FIXME: Notify user if there is no location source
            // or turn GPS on for them
            if (loc != null) {
                OSMMapView map = (OSMMapView) findViewById(R.id.mapView);
                Log.d("MapView", map.toString());
                map.setCenterCoords((float)loc.getLatitude(), (float)loc.getLongitude());
                map.setZoom(15);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();

        OSMMapView map = (OSMMapView) findViewById(R.id.mapView);
        map.startLocationUpdates(this);
    }
}

/* vim: set ts=4 sw=4 et ai :*/
