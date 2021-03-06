package com.tri.airr.hero;

import android.Manifest;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Looper;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.VoiceInteractionService;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;

import static com.tri.airr.hero.BluetoothConnect.connected;
import static com.tri.airr.hero.BluetoothConnect.pause;
import static com.tri.airr.hero.RESTTest.BASE_URL;

public class VoiceControl extends Fragment implements RecognitionListener{

    Button voiceListen;
    protected static final int SPEECH_REQUEST_CODE = 100;
    protected static final int REQUEST_AUDIO =1;
    protected int speechRequestCode;
    protected Intent intent;
    protected  AudioManager audioManager;
    protected TextView currAction;
    protected BluetoothConnect blc;
    protected  Thread updateMotor;
    protected long t1;
    protected boolean wordFound = false;
    protected FirebaseAnalytics fbAnalytics;
    //allow for synchronous threads
    protected String text;
    SpeechRecognizer spR;
    VoiceInteractionService voiceService;
    AlwaysOnHotwordDetector hotwordDetector;
    AlwaysOnHotwordDetector.Callback callback;

    String voiceTag = "Hotword";

    public int numGrasps = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_voice_control, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        currAction = (TextView) getView().findViewById(R.id.current_action);
        blc = new BluetoothConnect();
        if (getContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("This app needs acess to mic");
            builder.setMessage("Please grant audio record access so this app");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
                }
            });
            builder.show();
        }

        audioManager=(AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);


        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
       // intent.putExtra(RecognizerIntent.EXTRA)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //durration of quiet before words are done
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10);
        //Used to modify the number of similar sounds words
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        spR = SpeechRecognizer.createSpeechRecognizer(getContext());
        spR.setRecognitionListener(this);
        spR.startListening(intent);

        fbAnalytics = FirebaseAnalytics.getInstance(getContext());
    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {
        Log.i(voiceTag, "ready for speech");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(voiceTag, "beginning of speech");
        //used for getting listen time of robot
        t1 = Calendar.getInstance().getTimeInMillis();
    }

    @Override
    public void onRmsChanged(float v) {
        //Log.i(voiceTag, "rms has changed!!!");
    }

    @Override
    public void onBufferReceived(byte[] bytes) {

    }

    @Override
    public void onEndOfSpeech() {
        //display time it listened for
        Log.i(voiceTag, "Listened for " + (Calendar.getInstance().getTimeInMillis() - t1) + " millis");
    }

    @Override
    public void onError(int i) {
       /* Log.i(voiceTag, "error");*/

        spR.destroy();
        spR = null;
        if (getContext() != null) {
            spR = SpeechRecognizer.createSpeechRecognizer(getContext());
            spR.setRecognitionListener(this);
            spR.startListening(intent);
        } else {
            switchFragment();
        }


    }

    @Override
    public void onResults(Bundle bundle) {
        Log.i(voiceTag, "onResults");
        checkForVoiceMatch(bundle);

    }

    @Override
    public void onPartialResults(Bundle bundle) {

    }

    @Override
    public void onEvent(int i, Bundle bundle) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

  /*  @Override
    public void onBackPressed(){
        super.onBackPressed();
        spR.destroy();
        spR = null;
        logGrasp();
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);

    }*/

    @Override
    public void onDestroy(){
        /*spR.destroy();
        spR = null;*/
        //spR.stopListening();
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        super.onDestroy();
        //logGrasp();
    }

    public void switchFragment(){
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        logGrasp();
    }

   /* @Override
    public void onPause(){
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        super.onPause();
    }*/

 /*   //only activates after a few moments
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            spR.stopListening();
            logGrasp();
        }
    }
*/
    @Override
    public void onResume(){
        spR.startListening(intent);
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
        super.onResume();

    }

    protected void checkForVoiceMatch(Bundle bundle) {
        //this time used for finding processing and write time
        t1 = Calendar.getInstance().getTimeInMillis();
        ArrayList<String> matches = bundle
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        text = "";
        for (String result : matches) {
            text = checkForCommand(result);
            if (text != "")
                break;
        }

        if (connected && !text.equals("")){
            wordFound = true;
            updateMotor = new Thread(){
                public void run() {
                    Looper.prepare();
                    // t1 = Calendar.getInstance().getTimeInMillis();
                    BluetoothConnect blc = new BluetoothConnect();
                    blc.handToggle(text);
                    long t2 = Calendar.getInstance().getTimeInMillis();

                    Log.i(voiceTag,"Process and Write time is : " + (t2-t1) + " milli");

                    //increase on grasp incrementing number of grasps
                   /* RESTMethods rm = new RESTMethods();
                    JSONObject updatedEntry = rm.incrimentGraspDB(MainActivity.dbEntry, 1);
                    RequestQueue req = Volley.newRequestQueue(getApplicationContext());
                    rm.JSONObjectRequest(req, BASE_URL +"/"+ RESTMethods.currID, updatedEntry, Request.Method.PUT);

                    rm.JSONArrayRequest(req, BASE_URL, null, Request.Method.GET);*/
                   // Log.i("dataLog" , "Grasp has been logged to the server");


                }
            };

            Thread updateUI = createUIThread();

            updateUI.start();
            // updateMotor.start();

            try {
                updateUI.join();
                updateMotor.join();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        spR.startListening(intent);

    }

    protected Thread createUIThread() {
        Thread updateUI = new Thread() {
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //changing color
                        currAction.setTextColor(Color.BLACK);
                        Log.i(voiceTag, text + "ing");
                        if (text.equals("open")) {
                            currAction.setText("OPENING");
                            currAction.setBackgroundColor(getResources().getColor(R.color.openHand));
                            updateMotor.start();
                            numGrasps++;
                        } else if (text.equals("close")){
                            currAction.setText("CLOSING");
                            currAction.setBackgroundColor(getResources().getColor(R.color.closeHand));
                            updateMotor.start();
                        }else {
                            currAction.setText("RELAXING");
                            currAction.setBackgroundColor(getResources().getColor(R.color.material_grey_100));
                            updateMotor.start();
                        }
                        //spR.startListening(intent);
                    }
                });
            }
        };

        return updateUI;
    }

    private void logGrasp() {
        Bundle params = new Bundle();
        params.putInt("grasp_num", numGrasps);
        fbAnalytics.logEvent(MainActivity.entryName+"_graps", params);
    }

    protected String checkForCommand(String result) {
        String[] words = result.split(" ");
        for (int i =0; i < words.length; i++){
            if (words[i].equals("open") || words[i].equals("close") || words[i].equals("relax"))
                return words[i];
        }
        return "";
    }

}


