package com.javainstitute.parkfinder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

public class MainHomeFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main_home, container, false);

        // Load the map/booking fragment initially
        getChildFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new MainHomeBooking())
                .commit();

        return rootView;
    }
}
