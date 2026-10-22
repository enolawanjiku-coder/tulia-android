package com.tulia.app.ui.auth;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.R;

public class AuthGateFragment extends Fragment {

    private static final String TAG = "AuthGateFragment";

    public AuthGateFragment() {
        super(R.layout.fragment_gate);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavController navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() != R.id.authGateFragment) {
            return;
        }

        FirebaseUser user = null;
        try {
            user = FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
        }

        if (user != null) {
            ProfileGate.route(this, user);
        } else {
            navController.navigate(R.id.action_authGate_to_welcome);
        }
    }
}
