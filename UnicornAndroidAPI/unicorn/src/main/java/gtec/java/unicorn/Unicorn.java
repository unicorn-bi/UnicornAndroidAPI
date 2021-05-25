package gtec.java.unicorn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class Unicorn {

    /**
     * Public Members...
     */
    public final static int NumberOfAcquiredChannels = 17;
    public final static int SamplingRateInHz = 250;
    public final static byte NumberOfEEGChannels = 8;
    public final static byte NumberOfAccChannels = 3;
    public final static byte NumberOfGyrChannels = 3;
    public final static byte NumberOfCntChannels = 1;
    public final static byte NumberOfBatteryLevelChannels = 1;
    public final static byte NumberOfValidationIndicatorChannels = 1;

    /**
     * Constant Members...
     */
    private final static String UnicornSerialPrefix = "UN";
    private static final UUID SppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final static byte CmdStartAcquisition = 0x61;
    private final static int CmdStartAcquisitionAckLength = 3;
    private final static byte[] CmdStartAcquisitionAck = { 0, 0, 0 };
    private final static byte CmdStopAcquisition = 0x63;
    private final static int CmdStopAcquisitionAckLength = 3;
    private final static byte[] CmdStopAcquisitionAck = { 0, 0, 0 };
    private final static int BufferSizeInSeconds = 10;
    private final static byte TotalPayloadLengthInBytes = 45;
    private final static  byte[] HeaderStartSequence = { (byte)0xC0, (byte)0x00 };
    private final static  byte[] FooterStopSequence = { (byte)0x0D, (byte)0x0A };
    private final static float EegScale = (4500000.0f) / (50331642.0f);
    private final static float BatteryScale = (1.2f / 16.0f);
    private final static float BatteryOffset = 3.0f;
    private final static float BatteryPercentageFactor = 100.0f / 4.2f;
    private final static byte BatteryBitMask = 0x0F;
    private final static float AccelerometerScale = (1.0f / 4096.0f);
    private final static float GyroscopeScale = (1.0f / 32.8f);
    private final static byte HeaderLength = 2;
    private final static byte HeaderOffset = 0;
    private final static byte BytesPerBatteryLevelChannel = 1;
    private final static byte BatteryLevelLength = (byte)(NumberOfBatteryLevelChannels * BytesPerBatteryLevelChannel);
    private final static byte BatteryLevelOffset = (byte)(HeaderLength);
    private final static byte BytesPerEegChannel = 3;
    private final static byte EegLength = (byte)(NumberOfEEGChannels * BytesPerEegChannel);
    private final static byte EegOffset = (byte)(HeaderLength + BatteryLevelLength);
    private final static byte BytesPerAccChannel = 2;
    private final static byte AccLength = (byte)(NumberOfAccChannels * BytesPerAccChannel);
    private final static byte AccOffset = (byte)(HeaderLength + BatteryLevelLength + EegLength);
    private final static byte BytesPerGyrChannel = 2;
    private final static byte GyrLength = (byte)(NumberOfGyrChannels * BytesPerGyrChannel);
    private final static byte GyrOffset = (byte)(HeaderLength + BatteryLevelLength + EegLength + AccLength);
    private final static byte BytesPerCntChannel = 4;
    private final static byte CntLength = (byte)(NumberOfCntChannels * BytesPerCntChannel);
    private final static byte CntOffset = (byte)(HeaderLength + BatteryLevelLength + EegLength + AccLength + GyrLength);
    private final static byte NumberOfFooterChannels = 1;
    private final static byte BytesPerFooterChannel = 2;
    private final static byte FooterLength = (byte)(NumberOfFooterChannels * BytesPerFooterChannel);
    private final static byte FooterOffset = (byte)(HeaderLength + BatteryLevelLength + EegLength + AccLength + GyrLength + CntLength);
    private final static int WriteTimeoutMs = 1000;

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
    private Queue<Byte> _byteFifo = null;
    private Queue<Float> _floatFifo = null;
    private boolean _acquisitionRunning = false;
    private float[] _prevPayload = null;
    private long _prevWriteTimestamp = 0;

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
                _floatFifo = new ArrayDeque<Float>(SamplingRateInHz * NumberOfAcquiredChannels * BufferSizeInSeconds);
                _byteFifo = new ArrayDeque<Byte>(SamplingRateInHz * TotalPayloadLengthInBytes);

                _prevPayload = new float[NumberOfAcquiredChannels];
                _acquisitionRunning = false;
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

        byte[] response = new byte[CmdStartAcquisitionAckLength];
        int numberOfBytes = _inputStream.read(response, 0, CmdStartAcquisitionAckLength);

        if (numberOfBytes != CmdStartAcquisitionAckLength)
            throw new RuntimeException("Could not stop data acquisition. Could not read data.");

        if (!Arrays.equals(response,CmdStartAcquisitionAck))
            throw new RuntimeException("Could not start data acquisition. Invalid Acknowledge.");

        _acquisitionRunning = true;
    }

    public void StopAcquisition() throws Exception
    {
        //send sop acquisition command
        byte[] message = FormMessage(CmdStopAcquisition);
        _outputStream.write(message, 0, message.length);

        //wait for ack
        boolean ackReceived = false;
        int ackReceiveCnt = 0;
        do
        {
            byte data = (byte)_inputStream.read();
            if(data == CmdStopAcquisitionAck[ackReceiveCnt])
                ackReceiveCnt++;
            else
                ackReceiveCnt = 0;

            if(ackReceiveCnt == CmdStopAcquisitionAck.length)
                ackReceived = true;
        }
        while(!ackReceived);

        if (ackReceiveCnt != CmdStopAcquisitionAck.length)
            throw new RuntimeException("Could not stop data acquisition. Could not read data.");

        _acquisitionRunning = false;
    }

    public float[] GetData() throws Exception
    {
        //check bluetooth connection and device state
        if(!_acquisitionRunning)
            throw new Exception("Start data acquisition first.");
        if(_socket == null)
            throw new Exception("Initialize Bluetooth socket first.");
        if(_inputStream == null)
            throw new Exception("Initialize input stream first.");
        if(_outputStream == null)
            throw new Exception("Initialize output first.");

        //write dummy byte to keep acquisition alive (acquisition gets stuck on most android devices otherwise; max once per second)
        if(System.currentTimeMillis()-_prevWriteTimestamp > WriteTimeoutMs)
        {
            _prevWriteTimestamp = System.currentTimeMillis();
            byte[] message = new byte[1];
            _outputStream.write(message, 0, message.length);
        }

        //try to acquire data
        int acquisitionTimeoutMs = 1000;
        long start = System.currentTimeMillis();

        while (_floatFifo.size()<NumberOfAcquiredChannels && (System.currentTimeMillis()-start) < acquisitionTimeoutMs)
        {
            //read data
            ReadData();

            //sleep 1ms if data is not available yet
            if(_floatFifo.size()<NumberOfAcquiredChannels)
                Thread.sleep(1);
        }

        //check if acquisition timed out
        if (_floatFifo.size()<NumberOfAcquiredChannels)
            throw new Exception("Could not read data.");

        //get data from float fifo
        float[] dataOut = new float[NumberOfAcquiredChannels];
        for(int i=0;i<NumberOfAcquiredChannels;i++)
            dataOut[i]=_floatFifo.poll();

        //return scan
        return dataOut;
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
            if(device.getName().contains(UnicornSerialPrefix))
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
        byte[] crc = GetCRC16_CCITT(data, 0, data.length);

        //append crc to array and form message
        byte[] message = new byte[data.length + 2];
        System.arraycopy(data, 0, message, 0, data.length);
        message[data.length] = crc[0];
        message[data.length + 1] = crc[1];
        return message;
    }

    private static byte[] GetCRC16_CCITT(byte[] data, int arrayOffset, int dataSize)
    {
        short crcValue = 0;
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

    private void ReadData() throws Exception
    {
        int numberOfBytesAvailable = _inputStream.available();
        if (numberOfBytesAvailable > 0)
        {
            //read data
            int bytesRead = -1;
            byte[] rawData = new byte[numberOfBytesAvailable];
            bytesRead = _inputStream.read(rawData, 0, rawData.length);

            //enqueue in byte fifo
            if (bytesRead != -1)
            {
                for (int i = 0; i < bytesRead; i++)
                    _byteFifo.add(rawData[i]);//could throw exception
            }
        }

        //if at least one payload might be available
        if (_byteFifo.size() > TotalPayloadLengthInBytes)
        {
            do
            {
                //look for header start
                boolean headerFound = false;

                for (int i = 0; i < _byteFifo.size(); i++)
                {
                    if (_byteFifo.peek() == HeaderStartSequence[0])
                    {
                        headerFound = true;
                        break;
                    }
                    _byteFifo.poll();

                    if (i == _byteFifo.size() - 1)
                        headerFound = false;
                }

                //read payload
                if (headerFound && _byteFifo.size() > TotalPayloadLengthInBytes)
                {
                    //read payload
                    byte[] rawDataTmp = new byte[TotalPayloadLengthInBytes];
                    for (int i = 0; i < TotalPayloadLengthInBytes; i++)
                        rawDataTmp[i] = _byteFifo.poll();

                    //if valid payload was detected
                    if (rawDataTmp[0] == HeaderStartSequence[0] &&
                            rawDataTmp[1] == HeaderStartSequence[1] &&
                            rawDataTmp[rawDataTmp.length - 2] == FooterStopSequence[0] &&
                            rawDataTmp[rawDataTmp.length - 1] == FooterStopSequence[1])
                    {
                        //convert raw payload
                        float[] payload = ConvertRawPayload(rawDataTmp);

                        //validate payload
                        int numberOfSamplesLost = (int)(payload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels + NumberOfBatteryLevelChannels] - _prevPayload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels + NumberOfBatteryLevelChannels] - 1);

                        //interpolate lost payloads
                        if (numberOfSamplesLost > 0)
                        {
                            float cntTmp = _prevPayload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels + NumberOfBatteryLevelChannels];
                            for (int i = 0; i < numberOfSamplesLost; i++)
                            {
                                //counter
                                _prevPayload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels + NumberOfBatteryLevelChannels] = cntTmp + i + 1;

                                //validation indicator
                                _prevPayload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels + NumberOfBatteryLevelChannels + NumberOfCntChannels] = 0;

                                for (int j = 0; j < _prevPayload.length; j++)
                                    _floatFifo.add(_prevPayload[j]);
                            }
                        }

                        //validation indicator
                        payload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels + NumberOfBatteryLevelChannels + NumberOfCntChannels] = 1;

                        //fifo in
                        for (float val : payload)
                            _floatFifo.add(val);

                        //store last payload
                        System.arraycopy(payload,0,_prevPayload,0,payload.length);
                    }
                }
            }
            while (_byteFifo.size() > TotalPayloadLengthInBytes);
        }
    }

    private float[] ConvertRawPayload( byte[] rawPayload)
    {
        float[] payload = new float[NumberOfAcquiredChannels];

        //eeg
        for (int i = 0; i < NumberOfEEGChannels; i++)
        {
            int eegTemp = (((rawPayload[EegOffset + i * BytesPerEegChannel] & 0xFF) << 16) |
                    ((rawPayload[EegOffset + i * BytesPerEegChannel + 1] & 0xFF) << 8) |
                    (rawPayload[EegOffset + i * BytesPerEegChannel + 2] & 0xFF));

            //check if first bit is 1 (2s complement)
            if ((eegTemp & 0x00800000) == 0x00800000)
                eegTemp = (eegTemp | 0xFF000000);

            payload[i] = (float)eegTemp * EegScale;
        }

        //accelerometer
        for (byte i = 0; i < NumberOfAccChannels; i++)
        {
            short accTemp = (short)((rawPayload[AccOffset + i * BytesPerAccChannel] & 0xFF) |
                    ((rawPayload[AccOffset + i * BytesPerAccChannel + 1] & 0xFF) << 8));

            payload[i + NumberOfEEGChannels] = (float)accTemp * AccelerometerScale;
        }

        //gyroscope
        for (byte i = 0; i < NumberOfGyrChannels; i++)
        {
            short gyrTemp = (short)((rawPayload[GyrOffset + i * BytesPerGyrChannel] & 0xFF) |
                    ((rawPayload[GyrOffset + i * BytesPerGyrChannel + 1] & 0xFF) << 8));
            payload[i + NumberOfEEGChannels + NumberOfAccChannels] = (float)gyrTemp * GyroscopeScale;
        }

        //battery level
        payload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels] = ((rawPayload[BatteryLevelOffset] & BatteryBitMask) * BatteryScale + BatteryOffset) * BatteryPercentageFactor;

        //counter
        payload[NumberOfEEGChannels + NumberOfAccChannels + NumberOfGyrChannels + NumberOfBatteryLevelChannels] = ((rawPayload[CntOffset] & 0xFF) | (rawPayload[CntOffset + 1] & 0xFF) << 8 | (rawPayload[CntOffset + 2] & 0xFF) << 16 | (rawPayload[CntOffset + 3] & 0xFF) << 24);

        return payload;
    }
}
