/* 
 * Copyright (C) 2008 The Android Open Source Project
 * 			 (C) 2011 William Brown (http://haldean.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.haldean.voicefwd;

import org.haldean.voicefwd.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

/**
 * Sample code that invokes the speech recognition intent API.
 */
public class VoiceForward extends Activity implements OnClickListener {

    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    private BufferedWriter os;
    private ListView mList;
    private SharedPreferences prefs;
    private LinkedList<String> matches;

    /**
     * Called with the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.main);

        // Get display items for later interaction
        Button speakButton = (Button) findViewById(R.id.btn_speak);

        mList = (ListView) findViewById(R.id.list);

        prefs = getPreferences(MODE_PRIVATE);
        if (prefs.contains("hostPort")) {
        	String hostPort = prefs.getString("hostPort", "0.0.0.0:0000");
        	((EditText) findViewById(R.id.host_name)).setText(hostPort);
        }
        
        // Check to see if a recognition activity is present
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() != 0) {
            speakButton.setOnClickListener(this);
        } else {
            speakButton.setEnabled(false);
            speakButton.setText("Recognizer not present");
        }
        
        Button destButton = (Button) findViewById(R.id.btn_dest);
        destButton.setOnClickListener(this);
        
        matches = new LinkedList<String>();
    }

    /**
     * Handle the click on the start recognition button.
     */
    public void onClick(View v) {
        if (v.getId() == R.id.btn_speak) {
            startVoiceRecognitionActivity();
        } else if (v.getId() == R.id.btn_dest) {
        	setNewHost(((EditText) (findViewById(R.id.host_name))).getText().toString());
        }
    }

    /**
     * Fire an intent to start the speech recognition activity.
     */
    private void startVoiceRecognitionActivity() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }
    
    private void showError(String error) {
    	showDialog(error, getString(R.string.close));
    }
    
    private void showDialog(String error, String buttonName) {
    	final AlertDialog errorDialog = 
    		(new AlertDialog.Builder(this))
    			.setMessage(error)
    			.setCancelable(true)
    			.setNeutralButton(buttonName, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
    			}).create();
    	errorDialog.show();
    }
    
    private void onConnect() {
    	((ImageView) findViewById(R.id.big_logo)).setImageResource(R.drawable.large_icon);
    }
    
    private void onDisconnect() {
    	((ImageView) findViewById(R.id.big_logo)).setImageResource(R.drawable.large_icon_nocolor);
    }
    
    private void setNewHost(String hostPortString) {
    	onDisconnect();
    	ProgressDialog dialog = ProgressDialog.show(this, "", "Connecting to server...");
    	dialog.show();
    	
        try {
        	String[] hostPort = hostPortString.split(":");
        	Socket sock = new Socket(hostPort[0], new Integer(hostPort[1]));
        	os = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
        	prefs.edit().putString("hostPort", hostPortString).commit();
        	onConnect();
        	dialog.dismiss();
        } catch (ArrayIndexOutOfBoundsException e) {
        	handleException(R.string.bad_host, dialog);
        } catch (UnknownHostException e) { 
        	handleException(R.string.unknown_host, dialog);
        } catch (NumberFormatException e) {
        	handleException(R.string.bad_port, dialog);
        } catch (IOException e) {
        	handleException(R.string.no_connection, dialog);
        }
    }
    
    private void handleException(int exceptionStringId, ProgressDialog dialog) {
    	dialog.dismiss();
    	showError(getString(exceptionStringId));
    	onDisconnect();
    }

    /**
     * Handle the results from the recognition activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (os == null) {
    		showError("You must set a destination server.");
    		return;
    	}
    	
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Fill the list view with the strings the recognizer thought it could have heard
            matches.addFirst(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0));
            mList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, matches));
            try {
				os.write(matches.get(0) + "\n");
				os.flush();
				Log.i("VoiceForward", "Sent " + matches.get(0) + " to destination.");
			} catch (IOException e) {
				showError("Could not send to destination: " + e.getMessage());
			}
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}