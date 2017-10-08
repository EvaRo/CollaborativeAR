package com.main.collabar;

/*
* This Activity controls the Remote Client.
* The View shows the 3D model of the same assembly that is used in the ARClient. The closely connected RemoteRenderer class
* renders the 3D model of the assembly. The user can manipulate the view onto the assembly through gestures. The user can also
* chose specific building parts and manipulate them. There is a button that turns on the "AR view" which lets an active AR Client
* project its view onto the remote client. In this case, motion through the Remote Client is not possible anymore, manipulation
* of building parts is
*
* */

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.thingworx.RemoteClientThing;
import com.thingworx.ThingworxActivity;
import com.thingworx.communications.client.things.VirtualThing;
import com.thingworx.relationships.RelationshipTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.StringPrimitive;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class RemoteMain extends ThingworxActivity {

    public static final int POLLING_RATE = 1000;

    private RemoteRenderer renderer;

    private GestureDetector gestureDetector;

    private GLSurfaceView mGLView;

    boolean toggleSymbol = false;
    boolean toggleColor = false;
    boolean isChangeMenu = false;

    private BuildingPart selectedObject = null;

    private RelativeLayout mainLayout;
    private LinearLayout mainMenu;
    private LinearLayout symbolMenu;
    private LinearLayout colorMenu;

    private ImageButton btnLeftSide;
    private ImageButton btnMiddleLeft;
    private ImageButton btnMiddleRight;
    private ImageButton btnRightSide;

    private ImageButton btnRight;
    private ImageButton btnWrong;
    private ImageButton btnDanger;
    private ImageButton btnExclaim;
    private ImageButton btnQuestion;
    private ImageButton btnNoSymbol;
    private ImageButton btnRed;
    private ImageButton btnGreen;
    private ImageButton btnYellow;
    private ImageButton btnNoColor;

    private ImageButton btnLogout;
    private ImageButton btnToggleAR;

    private RemoteClientThing thing;
    private ThingDeletionTask thingTask = null;
    private float xpos;
    private float ypos;

    private float touchTurn = 0;
    private float touchTurnUp = 0;

    private ScaleGestureDetector scaleDetector;
    private boolean isScaling = false;

    private String userName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();
        userName = i.getStringExtra("userName");

        connectThing();
        while (!client.isConnected()){
            //stall}
        }
            initView();
            initGUI();
        loadDataFromThingWorx();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLView.onResume();
        //TW Connection
//        if (getConnectionState() == ConnectionState.DISCONNECTED) {
//            try {
//                connect(new VirtualThing[]{thing});
//            } catch (Exception e) {
//                Log.e("MAIN", "Restart with new settings failed.", e);
//            }
//        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLView.onPause();
    }

    private class GestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        //Gesture that points to a specific place in the view is processed,
        // depending on if a building part is selected already and if the gesture met a building part
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (selectedObject == null) {
                selectedObject = renderer.selectObjectAt((int) e.getX(), (int) e.getY());
                //If selection is object, the object is highlighted and the main menu is shown
                if (selectedObject != null) {
                    selectedObject.saveState();
                    selectedObject.setTexture("white");
                    mainMenu.setVisibility(View.VISIBLE);
                    renderer.zoom(1.1f ,selectedObject);
                }
            }
            else {
                //only deselect, when not in ChangeMenu
                if (!isChangeMenu) {
                    selectedObject.restoreFromLastState();
                    selectedObject = null;
                    mainMenu.setVisibility(View.INVISIBLE);
                }
            }

            return true;
        }
    }

    //Movement gestures will rotate the view around the assembly. Actual rotation is performed by renderer
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        scaleDetector.onTouchEvent(event);
        //There should be no Rotation while Zooming in/out
        //Also no rotation in remote-controlled mode
        if (event.getAction() == MotionEvent.ACTION_MOVE && (!isScaling) && renderer.isSelfControled()) {
            float xd = event.getX() - xpos;
            float yd = event.getY() - ypos;

            xpos = event.getX();
            ypos = event.getY();

            touchTurn = xd / -100f;
            touchTurnUp = yd / -100f;

            renderer.moveAssembly(touchTurn, touchTurnUp);

            return true;
        }
        return gestureDetector.onTouchEvent(event);
    }

    //Through the automatically recognized scale gesture the user can make, the view onto the assembly will zoom in and out.
    //Actual zooming is performed by renderer
    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //Gestures only while self-controlled
            if (renderer.isSelfControled()) {
                renderer.zoom(detector.getScaleFactor(), selectedObject);
            }

            return true;
        }
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector){
            isScaling = true;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector){
            isScaling = false;
        }

    }


    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public RemoteClientThing getThing() {
        return thing;
    }

    public void initGUI() {

        mainLayout = (RelativeLayout) View.inflate(this, com.main.collabar.R.layout.activity_remain,
                null);
        addContentView(mainLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mainLayout.setBackgroundColor(Color.TRANSPARENT);
        mainLayout.setVisibility(View.VISIBLE);

        btnLeftSide = (ImageButton) findViewById(com.main.collabar.R.id.changeButton);
        btnMiddleLeft = (ImageButton) findViewById(com.main.collabar.R.id.sensorButton);
        btnMiddleRight = (ImageButton) findViewById(com.main.collabar.R.id.viewButton);
        btnRightSide = (ImageButton) findViewById(com.main.collabar.R.id.resetButton);

        btnRight = (ImageButton) findViewById(com.main.collabar.R.id.btnRight);
        btnWrong = (ImageButton) findViewById(com.main.collabar.R.id.btnWrong);
        btnDanger = (ImageButton) findViewById(com.main.collabar.R.id.btnDanger);
        btnExclaim = (ImageButton) findViewById(com.main.collabar.R.id.btnExclaim);
        btnQuestion = (ImageButton) findViewById(com.main.collabar.R.id.btnQuestion);
        btnNoSymbol = (ImageButton) findViewById(com.main.collabar.R.id.btnNoSymbol);

        btnRed = (ImageButton) findViewById(com.main.collabar.R.id.btnRed);
        btnGreen = (ImageButton) findViewById(com.main.collabar.R.id.btnGreen);
        btnYellow = (ImageButton) findViewById(com.main.collabar.R.id.btnYellow);
        btnNoColor = (ImageButton) findViewById(com.main.collabar.R.id.btnNoColor);

        btnLogout = (ImageButton) findViewById(com.main.collabar.R.id.btnLogout);
        btnToggleAR = (ImageButton) findViewById(com.main.collabar.R.id.btnToggleAR);

        mainMenu = (LinearLayout) findViewById(com.main.collabar.R.id.gui_menu);
        symbolMenu = (LinearLayout) findViewById(com.main.collabar.R.id.gui_symbol);
        colorMenu = (LinearLayout) findViewById(com.main.collabar.R.id.gui_color);

        setMainMenu();

        btnRight.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setSymbol(PartSymbol.RIGHT);
            }
        });
        btnWrong.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setSymbol(PartSymbol.WRONG);
            }
        });
        btnDanger.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setSymbol(PartSymbol.DANGER);
            }
        });
        btnExclaim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setSymbol(PartSymbol.EXCLAIM);
            }
        });
        btnQuestion.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setSymbol(PartSymbol.QUESTION);
            }
        });
        btnNoSymbol.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setSymbol(PartSymbol.NONE);
            }
        });
        btnRed.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setColor(PartColor.RED);
            }
        });
        btnGreen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setColor(PartColor.GREEN);
            }
        });
        btnYellow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setColor(PartColor.YELLOW);
            }
        });
        btnNoColor.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                selectedObject.setColor(PartColor.NONE);
            }
        });

        //Removes the client as listener from the server, Deletes the Remote Thing created on login,
        // empties Object3D-Container and starts login Activity
        btnLogout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ValueCollection params = new ValueCollection();
                    params.put("mac",new StringPrimitive(getMACAddress()));
                    client.invokeService(RelationshipTypes.ThingworxEntityTypes.Things,
                            "Assembly", "RemoveListener", params, 10000);
                    client.shutdown();
                    thingTask = new ThingDeletionTask();
                    thingTask.execute((Void) null);
                    PartContainer.resetContainer();
                    Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                    startActivity(i);
                    finish();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        btnToggleAR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(renderer.isSelfControled()){
                    ((ImageButton) v).setImageResource(com.main.collabar.R.drawable.remote);
                    renderer.setSelfControled(false);
                }
                else {
                    ((ImageButton) v).setImageResource(com.main.collabar.R.drawable.eye);
                    renderer.setSelfControled(true);
                    renderer.setStandardView();
                }
            }
        });

        btnToggleAR.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                if(renderer.isSelfControled()){
                    renderer.setStandardView();
                }
                return true;
            }

        });
    }

    //to set the Buttons to Main menu
    private void setMainMenu() {
        isChangeMenu = false;

        ImageView img = new ImageView(this);
        img.setImageResource(com.main.collabar.R.drawable.change_icon);
        btnLeftSide.setImageDrawable(img.getDrawable());
        img.setImageResource(com.main.collabar.R.drawable.sensor_icon);
        btnMiddleLeft.setImageDrawable(img.getDrawable());
        img.setImageResource(com.main.collabar.R.drawable.view_icon);
        btnMiddleRight.setImageDrawable(img.getDrawable());
        img.setImageResource(com.main.collabar.R.drawable.reset_icon);
        btnRightSide.setImageDrawable(img.getDrawable());


        btnLeftSide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setChangeMenu();
                selectedObject.saveState();
            }
        });
        btnMiddleLeft.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                retrieveSensorData();
            }
        });
        btnMiddleRight.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPartDetailsDialog();
            }
        });
        btnRightSide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAcceptResetDialog();
            }
        });
    }

    //to set the Buttons to Change menu, on which a chosen building part can be manipulated
    private void setChangeMenu() {
        isChangeMenu = true;

        ImageView img = new ImageView(this);
        img.setImageResource(com.main.collabar.R.drawable.color_icon);
        btnLeftSide.setImageDrawable(img.getDrawable());
        img.setImageResource(com.main.collabar.R.drawable.symbol_icon);
        btnMiddleLeft.setImageDrawable(img.getDrawable());
        img.setImageResource(com.main.collabar.R.drawable.comment_icon);
        btnMiddleRight.setImageDrawable(img.getDrawable());
        img.setImageResource(com.main.collabar.R.drawable.accept_icon);
        btnRightSide.setImageDrawable(img.getDrawable());

        btnLeftSide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleColor = !toggleColor;
                if (toggleColor) {
                    colorMenu.setVisibility(View.VISIBLE);
                    symbolMenu.setVisibility(View.INVISIBLE);
                    toggleSymbol = false;
                } else {
                    colorMenu.setVisibility(View.INVISIBLE);
                }

            }
        });
        btnMiddleLeft.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleSymbol = !toggleSymbol;
                if (toggleSymbol) {
                    symbolMenu.setVisibility(View.VISIBLE);
                    colorMenu.setVisibility(View.INVISIBLE);
                    toggleColor = false;
                } else {
                    symbolMenu.setVisibility(View.INVISIBLE);
                }

            }
        });
        btnMiddleRight.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showCommentDialog();
            }
        });
        btnRightSide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAcceptChangesDialog();
                colorMenu.setVisibility(View.INVISIBLE);
                symbolMenu.setVisibility(View.INVISIBLE);
                setMainMenu();
            }
        });
    }

    //Dialog to enter comment about a building part
    private void showCommentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Geben Sie einen Kommentar ein:");

        final EditText input = new EditText(this);
        //comment input can only be 80 chars long
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(80) });
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedObject.setComment(input.getText().toString());
                selectedObject.getCommentPlane().setVisibility(true);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    //Dialog to accept manipulations on a building part.
    // If changes are accepted, changes are saved in the BuildingPart's PartState object,
    //else the old state of the building part is restored.
    private void showAcceptChangesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Änderungen übernehmen?");

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                try {
                    thing.sendChanges(selectedObject, userName);
                    showToast("Änderungen übernommen");
                    selectedObject.saveState();
                    selectedObject.setColor(selectedObject.getColor());
                    selectedObject = null;
                    mainMenu.setVisibility(View.INVISIBLE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedObject.restoreFromLastState();
                selectedObject.setTexture("white");
            }
        });

        builder.show();
    }

    //Dialog to reset all manipulations to a building part
    private void showAcceptResetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bauteil zurücksetzen?");

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedObject.reset();
                try {
                    thing.sendChanges(selectedObject, userName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                selectedObject.saveState();
                selectedObject = null;

                mainMenu.setVisibility(View.INVISIBLE);

            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        builder.show();
    }

    //Dialog shows all details about a building part
    private void showPartDetailsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bauteil Details");
        builder.setMessage(selectedObject.toString());

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.show();
    }

    /**
     * This function will be called from the base class to allow you to set
     * values on your virtual thing that are not configured in your aspect defaults or to perform
     * any other UI changes in response to becoming connected to the server.
     */
    @Override
    protected void onConnectionEstablished() {
        super.onConnectionEstablished();
        try {

        } catch (Exception e) {
            Log.e("MAIN", "Failed to load changes from ThingWorx", e);
        }

    }

    private void connectThing(){
        // Create your Virtual Thing and bind it to your android controls
        try {
            disconnect();
            thing = new RemoteClientThing("RemoteClient_" + userName, "Remote Client Thing", client);

            // You only need to do this once, no matter how many things you add
            startProcessScanRequestThread(POLLING_RATE, new ConnectionStateObserver() {
                @Override
                public void onConnectionStateChanged(final boolean connected) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showToast("connected.");
                        }
                    });
                }
            });


            connect(new VirtualThing[]{thing});

        } catch (Exception e) {
            Log.e("MAIN", "Failed to initalize with error.", e);
            onConnectionFailed("Failed to initalize with error : " + e.getMessage());
        }
    }

    //All manipulations saved on the server will be loaded and applied to the current building parts
    private void loadDataFromThingWorx() {
        try {
            ValueCollection params = new ValueCollection();

            InfoTable dataTable = client.invokeService(RelationshipTypes.ThingworxEntityTypes.Things,
                    "Assembly", "GetAllChangedRows", params, 10000);
            for (int i = 0; i < dataTable.getRowCount(); i++) {
                String partName = dataTable.getRow(i).getStringValue("part");
                BuildingPart currPart = PartContainer.getInstance().getObjByName(partName);


                try {
                    currPart.setColor(PartColor.valueOf(dataTable.getRow(i).getStringValue("color")));
                    currPart.setSymbol(PartSymbol.valueOf(dataTable.getRow(i).getStringValue("symbol")));
                }
                catch (Exception e2){
                    e2.printStackTrace();
                    Log.d("RELOADDATA", "Failed to set Color or Symbol. " + e2.getMessage());
                }

                currPart.setComment(dataTable.getRow(i).getStringValue("comment"));
                currPart.setAuthors(dataTable.getRow(i).getStringValue("colorAuthor"),
                        dataTable.getRow(i).getStringValue("symbolAuthor"), dataTable.getRow(i).getStringValue("commentAuthor"));
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            Log.d("LOADDATA", "Failed. " + e1.getMessage());
        }
    }

    public void initView(){
        mGLView = new GLSurfaceView(this);
        mGLView.setEGLContextClientVersion(2);
        mGLView.setPreserveEGLContextOnPause(true);

        renderer = new RemoteRenderer(this);

        mGLView.setRenderer(renderer);
        setContentView(mGLView);

        gestureDetector = new GestureDetector(this, new GestureListener());
        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

    private String getMACAddress(){
            try {
                List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface intf : interfaces) {
                    if (intf.getName().equalsIgnoreCase("wlan0")) {
                        byte[] mac = intf.getHardwareAddress();
                        if (mac == null) return "";
                        StringBuilder buf = new StringBuilder();
                        for (byte aMac : mac) {
                            buf.append(String.format("%02X:", aMac));
                        }
                        if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                        return buf.toString();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return "";
    }

    //Delete the Remote Thing created for the current session
    public class ThingDeletionTask extends AsyncTask<Void, Void, Boolean> {

        ThingDeletionTask() {}

        @Override
        protected Boolean doInBackground(Void... params) {
            Helper.sendDELETERequest("DELETETHING", "/Thingworx/Things/RemoteClient_" + userName);
            return true;
        }
    }

    //Sensor data has to be retrieved from server. All retrivied data will be shown in a dialog
    public void retrieveSensorData(){
        if (selectedObject != null) {
            try {
                ValueCollection params = new ValueCollection();
                params.put("part", new StringPrimitive(selectedObject.getName()));
                InfoTable dataTable = client.invokeService(RelationshipTypes.ThingworxEntityTypes.Things,
                        "Assembly", "RetrieveSensorData", params, 10000);
                Map<String,String> sensorList = new HashMap<>();
                for (int i = 0; i < dataTable.getRowCount(); i++) {
                    String sensorType = dataTable.getRow(i).getStringValue("sensor");
                    String sensorValue = dataTable.getRow(i).getStringValue("sensorValue");
                    sensorList.put(sensorType, sensorValue);

                }

                String message = "Bauteil Nummer " + selectedObject.getName().replaceAll("[a-zA-Z]","") + "\n";

                if (sensorList.isEmpty()){
                    message = message.concat("Es sind keine Sensordaten vorhanden.");
                }
                else {
                    Iterator it = sensorList.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry pair = (Map.Entry) it.next();
                        message = message.concat(pair.getKey() + " : " + pair.getValue() + "\n");
                        it.remove(); // avoids a ConcurrentModificationException
                    }
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Sensordaten Details");

                builder.setMessage(message);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.show();
            } catch (Exception e1) {
                e1.printStackTrace();
                Log.d("ERROR", "Failed. " + e1.getMessage());
            }
        }
    }


}
