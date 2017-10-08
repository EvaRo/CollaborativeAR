package com.main.collabar;

/*
* In the login activity, the user can input a name for the session and chose between login as AR/Remote Client.
* Note that only one AR Client can be online at a time. In an ansynchrone task, a POST request is sent to the ThingWorx server.
* If there is no error (second AR Client, no network, username in use), the chosen client Activity (RemoteMain, ARMain) is called.
* If the Remote Client is chosen, a respective Thing (RemoteClient_*username*) is created on the server.
* Another asynchrone task is used for that.
*/

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;


public class LoginActivity extends AppCompatActivity {

    private TextView nameField;
    private RadioButton btnARClient;
    private View mProgressView;
    private View mLoginFormView;
    private UserLoginTask mAuthTask = null;
    private ThingCreationTask thingTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.main.collabar.R.layout.activity_login);
        // Set up the login form.
        nameField = (EditText) findViewById(com.main.collabar.R.id.tfName);
        nameField.setFilters(new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(10)});

        btnARClient = (RadioButton) findViewById(com.main.collabar.R.id.btnARClient);

        Button loginButton = (Button) findViewById(com.main.collabar.R.id.btnLogin);
        loginButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(com.main.collabar.R.id.login_form);
        mProgressView = findViewById(com.main.collabar.R.id.login_progress);


    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {

        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        nameField.setError(null);

        // Store values at the time of the login attempt.
        String userName = nameField.getText().toString();
        nameField.setText("");

        boolean isAR = btnARClient.isChecked();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid email address.
        if (TextUtils.isEmpty(userName)) {
            nameField.setError("Bitte Namen eintragen");
            focusView = nameField;
            cancel = true;
        }
        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(userName, isAR);
            mAuthTask.execute((Void) null);

        }
    }


    /**
     * Shows the progress UI and hides the login form.
     */
    private void showProgress(final boolean show) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
    }

    private String getMACAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getName().equalsIgnoreCase("wlan0")) {
                    byte[] mac = intf.getHardwareAddress();
                    if (mac == null) return "";
                    StringBuilder buf = new StringBuilder();
                    for (int idx = 0; idx < mac.length; idx++)
                        buf.append(String.format("%02X:", mac[idx]));
                    if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                    return buf.toString();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }


    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String name;
        private int loginResult;
        private boolean isAR;

        UserLoginTask(String userName, boolean isAR) {
            name = userName;
            this.isAR = isAR;
        }

        @Override
        protected Boolean doInBackground(Void... params) {

            try {
                String result = Helper.sendPOSTRequest("{\"JSONInput\":{\"name\":\"" + name + "\",\"mac\":\"" + getMACAddress() + "\",\"isAR\":\"" + isAR +"\",\"timestamp\":\"" + getTimestamp() + "\"}}",
                        "REGISTERLISTENER", "/Thingworx/Things/Assembly/Services/AddListener");

                Log.d("LOGIN", "Response BODY : " + result);
                JSONObject json = new JSONObject(result);
                JSONArray arr = json.getJSONArray("rows");
                JSONObject resultJSON = arr.getJSONObject(0);
                loginResult = resultJSON.getInt("result");
                Log.d("LOGIN", "Result Number : " + loginResult);
            } catch (Exception e) {
                return false;
            }


            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;

            if (success) {
                if (loginResult == 0) {
                    showProgress(false);
                    showErrorDialog("Es ist bereits ein AR-Client online. Es kann sich kein zweiter einloggen.");
                }
                else if (loginResult == 1) {
                    showProgress(false);
                    showErrorDialog("Dieser Name wird bereits benutzt.");
                    nameField.setError("Name wird benutzt");
                }
                //2 means success and forwarding to Activity
                else if (loginResult == 2) {
                    //starts Activity to control the AR Client
                    if (isAR) {
                        Intent i = new Intent(getApplicationContext(), ARMain.class);
                        i.putExtra("userName", name);
                        startActivity(i);
                        finish();
                    }
                    //starts Activity to control the Remote Client
                    else{
                        thingTask = new ThingCreationTask(name);
                        thingTask.execute((Void) null);
                        Intent i = new Intent(getApplicationContext(), RemoteMain.class);
                        i.putExtra("userName", name);
                        startActivity(i);
                        finish();
                    }
                }
                else{
                    showProgress(false);
                    showErrorDialog("Problem mit dem Server.");
                }
            } else {
                showProgress(false);
                showErrorDialog("Problem mit dem Netzwerk.");
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private void showErrorDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Fehler");
        builder.setMessage(message);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        builder.show();
    }

    //Creates a new Thing on the ThingWorx server for each active session
    private void createRemoteClientThing(String name) {
        String clientName = "RemoteClient_" + name;
        Helper.sendPOSTRequest("{\"name\":\"" + clientName + "\", \"thingTemplateName\":\"RemoteClientTemplate\", \"description\":\"Roddeck\"}",
                "CREATETHING", "/Thingworx/Resources/EntityServices/Services/CreateThing");

        Helper.sendPOSTRequest("", "ENABLETHING", "/Thingworx/Things/" + clientName + "/Services/EnableThing");
        Helper.sendPOSTRequest("", "RESTARTTHING", "/Thingworx/Things/" + clientName + "/Services/RestartThing");
    }

    public class ThingCreationTask extends AsyncTask<Void, Void, Boolean> {

        String name;
        ThingCreationTask(String userName) {
            name = userName;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            createRemoteClientThing(name);
            return true;
        }
    }

}

