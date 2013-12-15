package com.pedalportland.routetracker;

import com.pedalportland.routetracker.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class MainActivity extends Activity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    private LocationManager locationManager;                            // gives location data
    private Location previousLocation;                                  // previous reported location
    private boolean tracking;                                           // whether app is currently tracking
    private long startTime;                                             // time (in milliseconds) when tracking starts
    private long distanceTraveled;                                      // total distance the user traveled
    private static final double MILLISECONDS_PER_HOUR = 1000 * 60 * 60; //
    private static final double MILES_PER_KILOMETER = 0.621371192;
    private static final int MAP_ZOOM = 18; // Google Maps supports 1-21
    private PowerManager.WakeLock wakeLock; // used to prevent device sleep
    private boolean gpsFix; // whether we have a GPS fix for accurate data



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final View contentView = findViewById(R.id.fullscreen_content);

        // ------------------------------------------------------------------------------
        // Set up an instance of SystemUiHider to control the system UI for this activity
        // ------------------------------------------------------------------------------

        mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider.setOnVisibilityChangeListener(
                new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate().translationY(visible ? 0 : mControlsHeight).setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        ToggleButton trackingToggleButton = (ToggleButton) findViewById(R.id.trackingToggleButton);

        trackingToggleButton.setOnTouchListener(mDelayHideTouchListener);

        // register listener for trackingToggleButton
        trackingToggleButton.setOnCheckedChangeListener(trackingToggleButtonListener);
    }

    // listener for trackingToggleButton's events
    CompoundButton.OnCheckedChangeListener trackingToggleButtonListener =
        new CompoundButton.OnCheckedChangeListener()
        {
            // called when user toggles tracking state

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                // if app is currently tracking
                if (!isChecked)
                {
                    tracking = false; // just stopped tracking locations

                    // compute the total time we were tracking
                    long milliseconds = System.currentTimeMillis() - startTime;
                    double totalHours = milliseconds / MILLISECONDS_PER_HOUR;

                    // create a dialog displaying the results
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
                    dialogBuilder.setTitle(R.string.results);

                    double distanceKM = distanceTraveled / 1000.0;
                    double speedKM = distanceKM / totalHours;
                    double distanceMI = distanceKM * MILES_PER_KILOMETER;
                    double speedMI = distanceMI / totalHours;

                    // display distanceTraveled traveled and average speed
                    dialogBuilder.setMessage(String.format(getResources().getString(R.string.results_format), distanceKM, distanceMI, speedKM, speedMI));
                    dialogBuilder.setPositiveButton(R.string.button_ok, null);
                    dialogBuilder.show(); // display the dialog
                } // end if
                else
                {
                    tracking = true; // app is now tracking
                    startTime = System.currentTimeMillis(); // get current time
                    // routeOverlay.reset(); // reset for new route
                    // bearingFrameLayout.invalidate(); // clear the route
                    // previousLocation = null; // starting a new route
                } // end else
            } // end method onCheckChanged
        }; // end anonymous inner class


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }


    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    // ***************************************************************
    // * Function: onStart
    // * Description: called when Activity becoming visible to the user
    // ***************************************************************
    @Override
    public void onStart()
    {
        super.onStart(); // call super's onStart method

        // create Criteria object to specify location provider's settings
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE); // fine location data
        criteria.setBearingRequired(true); // need bearing to rotate map
        criteria.setCostAllowed(true); // OK to incur monetary cost
        criteria.setPowerRequirement(Criteria.POWER_LOW); // try to conserve
        criteria.setAltitudeRequired(false); // don't need altitude data

        // get the LocationManager
        locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        // register listener to determine whether we have a GPS fix
        locationManager.addGpsStatusListener(gpsStatusListener);

        // get the best provider based on our Criteria
        String provider = locationManager.getBestProvider(criteria, true);

        // listen for changes in location as often as possible
        locationManager.requestLocationUpdates(provider, 0, 0, locationListener);

        // get the app's power manager
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // get a wakelock preventing the device from sleeping
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "No sleep");
        wakeLock.acquire(); // acquire the wake lock

        //bearingFrameLayout.invalidate(); // redraw the BearingFrameLayout
    } // end method onStart

    // ********************************************************************
    // * Function: onStop
    // * Description: called when Activity is no longer visible to the user
    // ********************************************************************

    @Override
    public void onStop()
    {
        super.onStop(); // call the super method
        wakeLock.release(); // release the wakelock
    } // end method onStop

    // ********************************************************************
    // * Function: updateLocation
    // * Description: update location on map
    // ********************************************************************

    public void updateLocation(Location location)
    {
        if (location != null && gpsFix) // location not null; have GPS fix
        {
            // add the given Location to the route
            //routeOverlay.addPoint(location);

            // if there is a previous location
            if (previousLocation != null)
            {
                // add to the total distanceTraveled
                distanceTraveled += location.distanceTo(previousLocation);
            } // end if

            // get the latitude and longitude
            Double latitude = location.getLatitude() * 1E6;
            Double longitude = location.getLongitude() * 1E6;

            // create GeoPoint representing the given Locations
            // GeoPoint point = new GeoPoint(latitude.intValue(), longitude.intValue());

            // move the map to the current location
            // mapController.animateTo(point);

            // update the compass bearing
            // bearingFrameLayout.setBearing(location.getBearing());
            // bearingFrameLayout.invalidate(); // redraw based on bearing
        } // end if

        previousLocation = location;
    } // end method updateLocation

    // ********************************************************************
    // * Function: GpsStatus.Listener (anonymous inner class)
    // * Description: determine whether we have GPS fix
    // ********************************************************************

    GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener()
    {
        public void onGpsStatusChanged(int event)
        {
            if (event == GpsStatus.GPS_EVENT_FIRST_FIX)
            {
                gpsFix = true;
                Toast results = Toast.makeText(MainActivity.this, getResources().getString(R.string.toast_signal_acquired), Toast.LENGTH_SHORT);

                // center the Toast in the screen
                results.setGravity(Gravity.CENTER, results.getXOffset() / 2, results.getYOffset() / 2);
                results.show(); // display the results
            }
        }
    };        // end anonymous inner class

    // responds to events from the LocationManager
    private final LocationListener locationListener =
        new LocationListener()
        {
            // when the location is changed
            public void onLocationChanged(Location location)
            {
                gpsFix = true; // if getting Locations, then we have a GPS fix

                if (tracking) // if we're currently tracking
                    updateLocation(location); // update the location
            } // end onLocationChanged

            public void onProviderDisabled(String provider)
            {
            } // end onProviderDisabled

            public void onProviderEnabled(String provider)
            {
            } // end onProviderEnabled

            public void onStatusChanged(String provider,
                                        int status, Bundle extras)
            {
            } // end onStatusChanged
        }; // end locationListener

}
