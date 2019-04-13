package com.hush_project;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;


import java.security.Permission;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
,LocationListener{

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private Location mLastLocation;
    private FusedLocationProviderClient fusedLocationProviderClient;
    DatabaseReference database; // database
    GeoFire geoFire;            // database
    Marker locationMarker;

    private static final int PermissionCode = 1994;
    private static final int ServiceRequest = 1994;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Database reference
        database = FirebaseDatabase.getInstance().getReference("Location");
        geoFire = new GeoFire(database);

        setLocation();
    }

    private void setLocation() {

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        { // Runtime permission request

            ActivityCompat.requestPermissions(this,new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION }, PermissionCode);
        }
        else
        {
            buildApiClient();
           // createLocationCallback();
            createLocationRequest();
            displayLocation();
        }
    }

//    private void createLocationCallback() {
//        mLocationCallback = new LocationCallback() {
//            @Override
//            public void onLocationResult(LocationResult locationResult) {
//                super.onLocationResult(locationResult);
//
//                mCurrentLocation = locationResult.getLastLocation();
//                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
//                updateLocationUI();
//            }
//        };
//    }


    private void buildApiClient () {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000); // 5 seconds
        mLocationRequest.setFastestInterval(3000); // 3 seconds
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(10);
        Log.d("Test: ", "Test for location check");
    }

    private void displayLocation() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // Got last known location. In some rare situations, this can be null.
                if (location != null) {
                    // Logic to handle location object
                    final double lat = location.getLatitude();
                    final double lng = location.getLongitude();
                    Log.d("Update", "Location was updated:\nlat= " + lat + "\nlng= " + lng );

                    // Post/Update database
                    geoFire.setLocation("User", new GeoLocation(lat, lng), new GeoFire.CompletionListener() {
                        @Override
                        public void onComplete(String key, DatabaseError error) {
                            if (locationMarker != null)
                            {
                                locationMarker.remove();
                            }

                            locationMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(lat,lng))
                            .title("userLocation"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lng),13.0f)); // newLatLngZoom option available

                        }
                    });
                }
                else
                {
                    Log.d("Failed", "Could not get location");
                }
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Create radius area
        LatLng radiusArea = new LatLng(44.532,-87.912);
        mMap.addCircle(new CircleOptions().center(radiusArea)
                .radius(500) // meters
                .strokeColor(Color.GRAY)
                .fillColor(0X40ff3300));

        // Check area with GeoFire
        GeoQuery locationQuery = geoFire.queryAtLocation(new GeoLocation(radiusArea.latitude,radiusArea.longitude),0.5); // 100 meter radius
        locationQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                Toast.makeText(MapsActivity.this,"entered volume area", Toast.LENGTH_SHORT ).show();
                Log.d("Update:", key + " entered the radius ");
            }

            @Override
            public void onKeyExited(String key) {
                Toast.makeText(MapsActivity.this,"exited volume area", Toast.LENGTH_SHORT ).show();
                Log.d("Update:",  key + " exited the radius ");
                //Notificatio
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Toast.makeText(MapsActivity.this,"moved within the volume area", Toast.LENGTH_SHORT).show();
                Log.d("Update:", key + " within the radius ");
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.e("Error", " "+ error);
            }
        });

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
    displayLocation();
    startLocationUpdates();
    }

    private void startLocationUpdates() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
      //  fusedLocationProviderClient.requestLocationUpdates(mLocationRequest,);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest, this);
        Log.d("Fused", "request location updated used");
    }

    @Override
    public void onConnectionSuspended(int i) {
    mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
    mLastLocation = location;
    displayLocation();
    }
}
