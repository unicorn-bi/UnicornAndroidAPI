package gtec.java.unicornandroidapi;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import java.util.List;

import gtec.java.unicorn.Unicorn;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        List<String> devices = Unicorn.GetAvailableDevices();
    }
}