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

        try
        {
            //get available devices
            List<String> devices = Unicorn.GetAvailableDevices();

            //connect to device
            Unicorn unicorn = new Unicorn(devices.get(0));

            //start acquisition
            unicorn.StartAcquisition();

            //acquisition loop
            float[] data = unicorn.GetData();

            //stop acquisition
            unicorn.StopAcquisition();

            //close device
            unicorn = null;
            System.gc();
            System.runFinalization();
        }
        catch(Exception ex)
        {
            int i = 0;
        }
    }
}