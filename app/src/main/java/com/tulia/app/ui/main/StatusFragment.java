package com.tulia.app.ui.main;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tulia.app.R;
import com.tulia.app.databinding.FragmentEmptyBinding;

public class StatusFragment extends Fragment {

    public StatusFragment() {
        super(R.layout.fragment_empty);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentEmptyBinding binding = FragmentEmptyBinding.bind(view);
        binding.screenTitle.setText(R.string.status_title);
        binding.emptyIcon.setImageResource(R.drawable.ic_status);
        binding.emptyTitle.setText(R.string.status_empty_title);
        binding.emptyBody.setText(R.string.status_empty_body);
    }
}
