package gtec.java.unicorn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Unicorn {

    /**
     * Constant Members...
     */
    private final static String UnicornSerialPrefix = "UN";
    private static final UUID SppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final static byte CmdStartAcquisition = 0x61;
    private final static int CmdStartAcquisitionAckLength = 3;
    private final static byte[] CmdStartAcquisitionAck = { 0, 0, 0 };

    /**
     * Static Members...
     */
    private static BluetoothAdapter _btAdapter = null;
    private static Set<BluetoothDevice> _devices = null;

    /**
     * Private Members...
     */
    private BluetoothSocket _socket = null;
    private OutputStream _outputStream = null;
    private InputStream _inputStream = null;


    public static List<String> GetAvailableDevices(Context context)throws Exception
    {
        //acquire paired devices
        _devices = GetBondUnicornDevices();
        List<String> deviceSerials = new ArrayList<>();
        for (BluetoothDevice device: _devices)
            deviceSerials.add(device.getName());

        //TODO: DISCOVER UNPAIRED DEVICES

        return deviceSerials;
    }

    public static List<String> GetAvailableDevices() throws Exception
    {
        //acquire paired devices
        _devices = GetBondUnicornDevices();
        List<String> deviceSerials = new ArrayList<>();
        for (BluetoothDevice device: _devices)
            deviceSerials.add(device.getName());

        return deviceSerials;
    }

    public Unicorn(String serial) throws Exception
    {
        //check if bluetooth adapter was ini
        InitializeAndCheckBluetoothAdapter();

        //acquire paired devices if get available devices was not called
        if(_devices == null)
            _devices = GetBondUnicornDevices();

        for (BluetoothDevice device : _devices)
        {
            if (device.getName().equals(serial))
            {
                //try to pair device for max 10s
                device.createBond();
                int connectionTimeoutMs = 10000;
                long start = System.currentTimeMillis();
                while (device.getBondState() != BluetoothDevice.BOND_BONDED && (System.currentTimeMillis()-start) < connectionTimeoutMs)
                    Thread.sleep(10);

                if ((System.currentTimeMillis()-start) > connectionTimeoutMs)
                    throw new Exception("Connection attempt timed out.");

                //open device
                _socket = device.createInsecureRfcommSocketToServiceRecord(SppUUID);
                _socket.connect();
                _outputStream = _socket.getOutputStream();
                _inputStream =  _socket.getInputStream();
            }
        }
    }

    protected void finalize() {
        if (_inputStream != null)
        {
            try
            {
                _inputStream.close();
            }
            catch(Exception e)
            {
                //DO NOTHING
            }
            _inputStream = null;
        }

        if (_outputStream != null)
        {
            try
            {
                _outputStream.close();
            }
            catch (Exception e)
            {
                //DO NOTHING
            }
            _outputStream = null;
        }

        if (_socket != null)
        {
            try
            {
                _socket.close();
            }
            catch (Exception e)
            {
                //DO NOTHING
            }
            _socket = null;
        }
    }

    public void StartAcquisition() throws Exception
    {
        //send start acquisition command
        byte[] message = FormMessage(CmdStartAcquisition);
        _outputStream.write(message, 0, message.length);
        _outputStream.flush();

        byte[] response = new byte[CmdStartAcquisitionAckLength];
        int numberOfBytes = _inputStream.read(response, 0, CmdStartAcquisitionAckLength);

        if (numberOfBytes != CmdStartAcquisitionAckLength)
            throw new RuntimeException("Could not stop data acquisition. Could not read data.");

        if (!Arrays.equals(response,CmdStartAcquisitionAck))
            throw new RuntimeException("Could not start data acquisition. Invalid Acknowledge.");
    }

    public void StopAcquisition()
    {

    }

    public float[] GetData()
    {
        return null;
    }

    private static void InitializeAndCheckBluetoothAdapter() throws Exception
    {
        if (_btAdapter == null)
            _btAdapter = BluetoothAdapter.getDefaultAdapter();

        if(_btAdapter==null)
            throw new Exception("No Bluetooth adapter found.");

        if(!_btAdapter.isEnabled())
        {
            try
            {
                _btAdapter.enable();
                if(!_btAdapter.isEnabled())
                {
                    throw new Exception("Could not enable Bluetooth adapter.");
                }
            }
            catch(Exception ex)
            {
                throw ex;
            }
        }
    }

    private static Set<BluetoothDevice> GetBondUnicornDevices() throws Exception
    {
        InitializeAndCheckBluetoothAdapter();
        Set<BluetoothDevice> devices = _btAdapter.getBondedDevices();
        Set<BluetoothDevice> unicornDevices = new ArraySet<>();
        for (BluetoothDevice device:devices)
        {
            if(device.getName().contains(UnicornSerialPrefix));
                unicornDevices.add(device);
        }
        return unicornDevices;
    }

    private static byte[] FormMessage(byte cmd)
    {
        //Shift cmd and payload in array
        byte[] data = new byte[1];
        data[0] = cmd;

        //calculate crc
        byte[] crc = GetCRC16_CCITT(data, 0, data.length, (short)0);

        //append crc to array and form message
        byte[] message = new byte[data.length + 2];
        System.arraycopy(data, 0, message, 0, data.length);
        message[data.length] = crc[0];
        message[data.length + 1] = crc[1];
        return message;
    }

    private static byte[] GetCRC16_CCITT(byte[] data, int arrayOffset, int dataSize, short preset)
    {
        short crcValue = preset;
        byte[] crcBuffer = new byte[2];
        for (int i = arrayOffset; i < arrayOffset + dataSize; i++)
        {
            crcValue = (short)(((crcValue >> 8) & 0xFF) | (crcValue << 8));
            if (data[i] >= 0)
                crcValue ^= data[i];
            else
                crcValue ^= (short)(data[i] + 256);
            crcValue ^= (short)((crcValue & 0xFF) >> 4);
            crcValue ^= (short)((crcValue << 8) << 4);
            crcValue ^= (short)(((crcValue & 0xFF) << 4) << 1);
        }
        crcBuffer[0] = (byte)((crcValue >> 8));
        crcBuffer[1] = (byte)(crcValue);
        return crcBuffer;
    }
}
