package gtec.java.unicornandroidapi;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import gtec.java.unicorn.Unicorn;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private String _btnConStr = "Connect";
    private String _btnDisconStr = "Disconnect";
    private Button _btnConnect = null;
    private Spinner _spnDevices = null;
    private TextView _tvState = null;
    private Unicorn _unicorn = null;
    private Thread _receiver;
    private boolean _receiverRunning = false;
    private Context _context = null;
    private  int _cnt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try
        {
            //get ui elements
            _context = this.getApplicationContext();
            _spnDevices = findViewById(R.id.spnDevices);
            _btnConnect = findViewById(R.id.btnConnect);
            _tvState = findViewById(R.id.textView);

            _btnConnect.setText(_btnConStr);
            _btnConnect.setOnClickListener(this);

            //get available devices
            List<String> devices = Unicorn.GetAvailableDevices();

            //update ui
            ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,devices);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            _spnDevices.setAdapter(adapter);
        }
        catch(Exception ex)
        {
            Toast t = Toast.makeText(_context,String.format("Could not detect available devices. %s", ex.getMessage()) ,Toast.LENGTH_SHORT);
        }
    }

    private void StartReceiver()
    {
        _receiverRunning = true;
        _receiver = new Thread(_doReceive);
        _receiver.setPriority(Thread.MIN_PRIORITY);
        _receiver.setDaemon(false);
        _receiver.start();
    }

    private void StopReceiver() throws Exception
    {
        _receiverRunning = false;
        _receiver.join(500);
    }

    private Runnable _doReceive = new Runnable()
    {
        @Override
        public void run()
        {
            while(_receiverRunning)
            {
                try
                {
                    float[] data = _unicorn.GetData();

                    //update main ui
                    _cnt++;
                    if(_cnt%Unicorn.SamplingRateInHz == 0)
                    {
                        Handler mainHandler = new Handler( _context.getMainLooper());
                        Runnable myRunnable = new Runnable() {
                            @Override
                            public void run()
                            {
                                String message = _tvState.getText().toString();
                                message += ".";
                                _tvState.setText(message);
                            }
                        };
                        mainHandler.post(myRunnable);
                    }
                }
                catch (Exception ex)
                {
                    //disconnect and update main ui
                    Handler mainHandler = new Handler( _context.getMainLooper());
                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run()
                        {
                            String message = _tvState.getText().toString();
                            message += String.format("Acquisition failed. %s\n", ex.getMessage());
                            _tvState.setText(message);
                            Disconnect();
                        }
                    };
                    mainHandler.post(myRunnable);
                }
            }
        }
    };

    private void Connect()
    {
        _btnConnect.setEnabled(false);
        _spnDevices.setEnabled(false);
        String device = (String)_spnDevices.getSelectedItem();
        String message = "";
        try
        {
            //update ui message
            message += String.format("Connecting to %s...\n", device);
            _tvState.setText(message);

            //connect to device
            _unicorn = new Unicorn(device);
            _btnConnect.setText(_btnDisconStr);

            //update ui message
            message += "Connected.\n";
            message += "Starting data acquisition...\n";
            _tvState.setText(message);

            //start acquisition
            _unicorn.StartAcquisition();

            message += "Acquisition running.\n";
            _tvState.setText(message);
            //start receiving thread
            StartReceiver();
        }
        catch (Exception ex)
        {
            //close device
            _unicorn = null;
            System.gc();
            System.runFinalization();

            _btnConnect.setText(_btnConStr);
            _spnDevices.setEnabled(true);

            //update ui message
            message += String.format("Could not start acquisition. %s", ex.getMessage());
            _tvState.setText(message);
        }
        _btnConnect.setEnabled(true);
    }

    private void Disconnect()
    {
        _btnConnect.setEnabled(false);
        String device = (String)_spnDevices.getSelectedItem();
        String message = _tvState.getText().toString();
        try
        {
            //update ui message
            message += "\nStopping data acquisition...\n";
            _tvState.setText(message);

            //stop acquisition
            _unicorn.StopAcquisition();

            //stop receiving thread
            StopReceiver();

            //update ui message
            message += String.format("Disconnecting from %s...\n", device);
            _tvState.setText(message);

            //close device
            _unicorn = null;
            System.gc();
            System.runFinalization();

            message += "Disconnected";
            _tvState.setText(message);

            _btnConnect.setText(_btnConStr);
        }
        catch (Exception ex)
        {
            //close device
            _unicorn = null;
            System.gc();
            System.runFinalization();

            _btnConnect.setText(_btnConStr);

            message += String.format("Could not stop acquisition. %s", ex.getMessage());
            _tvState.setText(message);
        }
        _spnDevices.setEnabled(true);
        _btnConnect.setEnabled(true);
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.btnConnect:
            {
                if(_btnConnect.getText().equals(_btnConStr))
                {
                    Connect();
                }
                else
                {
                    Disconnect();
                }
                break;
            }
        }
    }
}