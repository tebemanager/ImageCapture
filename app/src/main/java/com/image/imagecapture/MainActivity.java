package com.image.imagecapture;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.image.imagecapture.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements CameraFragmentToActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding activityMainBinding = DataBindingUtil.setContentView(this,R.layout.activity_main);

        loadCameraFragment(getSupportFragmentManager(),activityMainBinding.btn1, activityMainBinding.frame);

    }

    public void loadCameraFragment(FragmentManager fragmentManager, Button button, FrameLayout frameLayout) {
        frameLayout.setVisibility(View.VISIBLE);
        button.setVisibility(View.GONE);
        Fragment fragment = new CameraFragment();

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(frameLayout.getId(), fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void sendUrl(String url) {

    }

    @Override
    public void fragmentBackPress() {

    }
}