package com.example.segan_application;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Random;

import org.tensorflow.lite.Interpreter;

import cafe.adriel.androidaudioconverter.AndroidAudioConverter;
import cafe.adriel.androidaudioconverter.callback.IConvertCallback;
import cafe.adriel.androidaudioconverter.callback.ILoadCallback;
import cafe.adriel.androidaudioconverter.model.AudioFormat;

public class MainActivity extends AppCompatActivity {
    private Button play_original, play_enhanced, stop, record, convert;
    private MediaRecorder audioRecorder;
    private String recordFile, outputFile, wavFile;

    //Tensorflow interpreter Object
    private Interpreter tfLite;
    private MediaPlayer mediaPlayer;

    //SeGAN model path
    private static final String MODEL_FILENAME = "file:///android_asset/segan.tflite";

    //Load the tflite model into bytebuffer
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //Read audio file into byte array function
    public byte[] ReadAudio(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[1024];

        for (int readNum; (readNum = fis.read(b)) != -1;) {
            bos.write(b, 0, readNum);
        }

        return bos.toByteArray();
    }

    //Convert Audio Function
    public boolean convertAudio(String path){
        Log.i("infoTag", "Converting 3GPP into WAV!");

        File flacFile = new File(path);
        IConvertCallback callback = new IConvertCallback() {
            @Override
            public void onSuccess(File convertedFile) {
            }
            @Override
            public void onFailure(Exception error) {
            }
        };

        //Convert the audio
        AndroidAudioConverter.with(this)
                // Your current audio file
                .setFile(flacFile)
                // Your desired audio format
                .setFormat(AudioFormat.WAV)
                // An callback to know when conversion is finished
                .setCallback(callback)
                // Start conversion
                .convert();

        Log.i("infoTag", "Audio Converted!");

        return true;
    }

    //Convert little endian byte array into short
    short GetShortFromBytes(byte[] data, int startIndex, int param)
    {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        if(param == 0){
            buffer.order(ByteOrder.BIG_ENDIAN);
        }
        else{
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        buffer.put(data[startIndex]);
        buffer.put(data[startIndex+1]);
        return buffer.getShort(0);
    }

    //Convert short into little endian byte array
    byte[] GetBytesFromShort(short data, int param)
    {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        if(param == 0){
            buffer.order(ByteOrder.BIG_ENDIAN);
        }
        else{
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        buffer.putShort(data);
        return buffer.array();
    }

    //Stop mediaPlayer function
    private void stopMediaPlayer() {
        if(mediaPlayer != null){
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer=null;
        }
    }

    //On create function
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Layout button
        play_original = findViewById(R.id.play_original);
        play_enhanced = findViewById(R.id.play_enhanced);
        stop = findViewById(R.id.stop);
        record = findViewById(R.id.record);
        convert = findViewById(R.id.convert);

        //Set button
        record.setEnabled(true);
        stop.setEnabled(false);
        convert.setEnabled(true);
        play_original.setEnabled(false);
        play_enhanced.setEnabled(false);

        //Audio file path
        recordFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recording.3gp";
        outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/enhanced.3gp";
        wavFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recording.wav";

        //Get Audio Permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    BuildDev.VALUE);
        } else {
            Toast.makeText(getApplicationContext(), "Audio Record Permission Denied", Toast.LENGTH_LONG).show();
        }

        //Get Storage Permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    BuildDev.VALUE);
        } else {
            Toast.makeText(getApplicationContext(), "Write SD Permission Denied", Toast.LENGTH_LONG).show();
        }

        //Load Audio Converter
        AndroidAudioConverter.load(this, new ILoadCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Audio Converter Ready!", Toast.LENGTH_LONG).show();
            }
            @Override
            public void onFailure(Exception error) {
                //FFMPEG is not supported
                Toast.makeText(getApplicationContext(), "Device isn't Support Converter", Toast.LENGTH_LONG).show();
            }
        });

        //Record button function
        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Set the Audio Recorder
                audioRecorder = new MediaRecorder();
                audioRecorder.reset();
                audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
                audioRecorder.setOutputFile(recordFile);

                //Start the Recorder
                try {
                    audioRecorder.prepare();
                    audioRecorder.start();
                } catch (IllegalStateException ise) {
                    Toast.makeText(getApplicationContext(), "State Error!", Toast.LENGTH_LONG).show();
                } catch (IOException ioe) {
                    Toast.makeText(getApplicationContext(), "IO Error!", Toast.LENGTH_LONG).show();
                }

                //Set button
                record.setEnabled(false);
                stop.setEnabled(true);

                Toast.makeText(getApplicationContext(), "Recording Started", Toast.LENGTH_LONG).show();
            }
        });

        //Stop button function
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Stop the Audio Recorder
                audioRecorder.stop();
                audioRecorder.reset();
                audioRecorder.release();
                audioRecorder = null;

                //Set Button
                record.setEnabled(true);
                stop.setEnabled(false);
                convert.setEnabled(true);
                play_original.setEnabled(true);
                play_enhanced.setEnabled(false);

                Toast.makeText(getApplicationContext(), "Audio Recorded successfully", Toast.LENGTH_LONG).show();
            }
        });

        //Convert button function
        convert.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                //Random number object
                Random rand = new Random();
                int counter;

                Log.i("infoTag", "Audio Enhancer Started!");

                //Load the model into interpreter
                String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
                try {
                    tfLite = new Interpreter(loadModelFile(getAssets(), actualModelFilename));
                } catch (Exception e) {
                    throw new RuntimeException();
                }

                //Convert the audio file into WAV if necessary
                //convertAudio(recordFile);

                //Read the recorded audio file into a byte array
                byte[] inputByte = new byte[0];

                //Load the audio
                try {
                    inputByte = ReadAudio(recordFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Save the audio data


                //Information
                Log.i("infoTag", "Input Byte Length  : " + inputByte.length);
                Log.i("infoTag", "Input Byte         : " + Arrays.toString(inputByte));

                //Convert the byte array into a float array for SeGAN model
                float[] inputFloatArray = new float[inputByte.length/2];

                //Find the magnitude of the audio
                float biggestValue = -32768F;
                float audioSample;

                //Loop through the byte array
                for (int i = 0; i < inputByte.length; i += 2)
                {
                    //Break the loop if the next data pointer is null
                    if( i + 1 >= inputByte.length){
                        break;
                    }

                    //Get the value of byte array
                    audioSample = (float) GetShortFromBytes(inputByte, i, 1);

                    //If the sample value is bigger > change the magnitude
                    if (audioSample > biggestValue){
                        biggestValue = audioSample;
                    }
                }

                //Load the byte array into float array using the magnitude
                float magnitude = 32768F - biggestValue;

                //Loop through the byte array
                for (int i = 0; i < inputByte.length; i += 2)
                {
                    //Break the loop if the next data pointer is null
                    if( i + 1 >= inputByte.length){
                        break;
                    }

                    //Insert the short into the float array
//                    inputFloatArray[i / 2] = (float) GetShortFromBytes(inputByte, i, 1) + magnitude;

                    //Normalize
                    inputFloatArray[i / 2] = (float)GetShortFromBytes(inputByte, i,1) / 32768.0F;
                }

                Log.i("infoTag", "Input Float Length : " + inputFloatArray.length);
                Log.i("infoTag", "Input Float        : " + Arrays.toString(inputFloatArray));

                //Chunk the input and then run the inference
                //Find the number of chunk
                int floatArrayLength = inputFloatArray.length;
                int seganInputShape = 16413 - 29;
                int nChunk = (int) Math.ceil(((float)(floatArrayLength) / (float)(seganInputShape)));

                Log.i("infoTag", "Number of chunk : " + nChunk);

                //Creating segan inference output array
                float[] seganOutputFloat = new float[nChunk*seganInputShape];

                //Converting 1 chunk at a time
                for(int i = 0; i < nChunk; i++){
                    //Run Single Inference
                    int base = i * seganInputShape;

                    //Creating 3D array for segan inference
                    float[][][] seganInputArray = new float[1][1][seganInputShape];
                    float[][][] seganOutputArray = new float[1][1][seganInputShape];

                    //Copy the input 1D array into segan input 3D array with looping through the array
                    for(int j = 0; j < seganInputShape; j++){
                        //If the shape isn't enough fill with zero
                        if(base + j >= floatArrayLength){
                            seganInputArray[0][0][j] = 0;
                        }
                        else{
                            seganInputArray[0][0][j] = inputFloatArray[base+j];
                        }
                    }

                    //Information
                    Log.i("infoTag", "Segan Input Float Array Length  : " + seganInputArray[0][0].length);
                    Log.i("infoTag", "Segan Input Float Array         : " + Arrays.deepToString(seganInputArray));

                    //Run the inference using segan model
                    tfLite.run(seganInputArray, seganOutputArray);

                    //Information
                    Log.i("infoTag", "Segan Output Float Array Length : " + seganOutputArray[0][0].length);
                    Log.i("infoTag", "Segan Output Float Array        : " + Arrays.deepToString(seganOutputArray));

                    //Write the segan inference output array into the final output array
                    for(int k = 0; k < seganInputShape; k++) {
                        seganOutputFloat[base + k] = seganOutputArray[0][0][k];
                    }

                    //For testing byte conversion
                    //System.arraycopy(seganInputArray[0][0], 0, seganOutputFloat, base, seganInputShape);
                }

                //Information
                Log.i("infoTag", "Output Float Length : " + seganOutputFloat.length);
                Log.i("infoTag", "Output Float        : " + Arrays.toString(seganOutputFloat));

                //Create an output byte array
                byte[] outputByte = new byte[nChunk*seganInputShape*2];

                //Reset counter
                counter = 0;

                //Convert the float output array into byte output array
                for (int l = 0; l < outputByte.length; l += 2)
                {
                    //Convert the float into little endian byte
                    //byte[] littleShortTemp = GetBytesFromShort((short)(seganOutputFloat[l/2] - magnitude), 1);

                    //Denormalize
                    byte[] littleShortTemp = GetBytesFromShort((short)((seganOutputFloat[l/2]) * 32768.0F), 1);

                    outputByte[l] = littleShortTemp[0];
                    outputByte[l+1] = littleShortTemp[1];
                }

                //Fixing the Audio Output
                byte[] metaTag = {0, 0, 0, 24, 102, 116, 121, 112, 51, 103, 112, 52, 0, 0, 0, 0, 105, 115, 111, 109, 51, 103, 112, 52, 0, 0, 12, 119, 102, 114, 101, 101};
                for(int m = 0; m < metaTag.length; m++){
                    outputByte[m] = metaTag[m];
                }

                //Information
                Log.i("infoTag", "Output Byte Length  : " + outputByte.length);
                Log.i("infoTag", "Output Byte         : " + Arrays.toString(outputByte));

                //Export the byte array to audio file
                try {
                    FileOutputStream fileoutputstream = new FileOutputStream(outputFile);
                    fileoutputstream.write(outputByte);
                    fileoutputstream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Toast.makeText(getApplicationContext(), "Audio Enhancing Finished!", Toast.LENGTH_SHORT).show();

                record.setEnabled(true);
                stop.setEnabled(false);
                convert.setEnabled(true);
                play_original.setEnabled(true);
                play_enhanced.setEnabled(true);
            }
        });

        //Play original audio function
        play_original.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            try {
                stopMediaPlayer();
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(recordFile);
                mediaPlayer.prepare();
                mediaPlayer.start();
                Toast.makeText(getApplicationContext(), "Playing Original Audio", Toast.LENGTH_LONG).show();
            } catch (Exception e) {

            }
            }
        });

        //Play enhanced audio function
        play_enhanced.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            try {
                stopMediaPlayer();
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(outputFile);
                mediaPlayer.prepare();
                mediaPlayer.start();
                Toast.makeText(getApplicationContext(), "Playing Enhanced Audio", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                // make something
            }
            }
        });
    }
}