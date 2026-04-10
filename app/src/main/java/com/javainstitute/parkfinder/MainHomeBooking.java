package com.javainstitute.parkfinder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainHomeBooking extends Fragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;
    private AutoCompleteTextView searchBar;
    private ImageButton searchButton;

    // ── Location permission launcher ──────────────────

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            enableMyLocation();
                        } else {
                            Toast.makeText(getContext(),
                                    "Location permission denied. Your position won't appear on the map.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
            );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_home_booking, container, false);

        mapView     = view.findViewById(R.id.fragmentContainer_map);
        searchBar   = view.findViewById(R.id.search_bar);
        searchButton = view.findViewById(R.id.search_button);

        List<String> locations = SriLankaLocationUtil.getAllSriLankaLocations(getContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, locations);
        searchBar.setAdapter(adapter);
        searchBar.setThreshold(1);

        searchButton.setOnClickListener(v -> searchAndMoveToLocation());
        searchBar.setOnItemClickListener((parent, view1, position, id) -> searchAndMoveToLocation());

        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }

        return view;
    }

    // ── Map ready ──────────────────

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;

        LatLng defaultLocation = new LatLng(6.9271, 79.8612);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f));

        loadLocationsFromFirestore();

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        googleMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag != null && tag.equals("yellow")) {
                String name = marker.getTitle();
                LatLng pos  = marker.getPosition();
                openBookingProcessFragment(name, pos.latitude, pos.longitude);
                return true;
            }
            return false;
        });
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (googleMap != null) {
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }

    // ── Firestore markers ──────────────────────────
    private void loadLocationsFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference locationsRef = db.collection("register_locations");

        locationsRef.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString("name");
                        Double lat  = doc.getDouble("latitude");
                        Double lng  = doc.getDouble("longitude");

                        if (name != null && lat != null && lng != null) {
                            LatLng position = new LatLng(lat, lng);
                            Marker marker = googleMap.addMarker(new MarkerOptions()
                                    .position(position)
                                    .title(name)
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                            BitmapDescriptorFactory.HUE_YELLOW)));
                            if (marker != null) marker.setTag("yellow");
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to load locations", Toast.LENGTH_SHORT).show());
    }

    private void openBookingProcessFragment(String name, double latitude, double longitude) {
        Bundle bundle = new Bundle();
        bundle.putString("location_name", name);
        bundle.putDouble("latitude", latitude);
        bundle.putDouble("longitude", longitude);

        Booking_Process_Fragment bookingFragment = new Booking_Process_Fragment();
        bookingFragment.setArguments(bundle);

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, bookingFragment)
                .addToBackStack(null)
                .commit();
    }

    // ── Search / Geocoding ────────────
    private void searchAndMoveToLocation() {
        String locationName = searchBar.getText().toString();
        if (locationName.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a location", Toast.LENGTH_SHORT).show();
            return;
        }
        Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(locationName + ", Sri Lanka", 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                if (googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
                }
            } else {
                Toast.makeText(getContext(), "Location not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(getContext(), "Geocoding failed", Toast.LENGTH_SHORT).show();
        }
    }

    // ── MapView lifecycle ─────────────────────────

    @Override public void onResume()   { super.onResume();   if (mapView != null) mapView.onResume(); }
    @Override public void onPause()    { if (mapView != null) mapView.onPause();   super.onPause(); }
    @Override public void onDestroy()  { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory(){ super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
}