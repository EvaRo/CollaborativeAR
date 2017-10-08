package com.main.collabar;

/*
* This Activity controls the AR Client.
* It is based on a solution to integrate Vuforia with jPCT-AE which can be found here:
* https://github.com/TheMaggieSimpson/Vuforia559_jPCT-AE (class ImageTargets.java)
* A camera feed is shown. As soon as the Vuforia API recognizes the target on the feed, the closely connected ARRenderer class
* renders the 3D model of the assembly onto the camera feed. The user can then manipulate chosen building part objects on the screen.
*
* */

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.thingworx.ARClientThing;
import com.thingworx.ThingworxActivity;
import com.thingworx.communications.client.things.VirtualThing;
import com.thingworx.relationships.RelationshipTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.StringPrimitive;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.vuforia.util.SampleApplicationControl;
import com.vuforia.util.SampleApplicationSession;
import com.vuforia.util.*;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class ARMain extends ThingworxActivity implements SampleApplicationControl
{
    private static final String LOGTAG = "ImageTargets";
    public static final int POLLING_RATE = 1000;

    SampleApplicationSession vuforiaAppSession;

    private DataSet mCurrentDataset;
    private int mCurrentDatasetSelectionIndex = 0;
    private int mStartDatasetsIndex = 0;
    private int mDatasetsNumber = 0;
    private ArrayList<String> mDatasetStrings = new ArrayList<String>();

    // Our OpenGL view:
    private SampleApplicationGLView mGlView;

    private ARRenderer renderer;

    private GestureDetector gestureDetector;

    private boolean mSwitchDatasetAsap = false;
    private boolean mContAutofocus = false;
    private boolean mExtendedTracking = false;
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

    private ARClientThing thing;

    private String userName = "";


    LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    boolean mIsDroidDevice = false;


    // Called when the activity first starts or the user navigates back to an
    // activity.
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        startLoadingAnimation();

        Intent i = getIntent();
        userName = i.getStringExtra("userName");

        connectThing();
        while (!client.isConnected()){
            //stall
        }
        initView();

    }

    // Process Single Tap event to trigger autofocus
    private class GestureListener extends
            GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();


        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }


        //Gesture that points to a specific place in the view is processed,
        // depending on if a building part is selected already and if the gesture met a building part
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            if (selectedObject == null) {
                selectedObject = renderer.selectObjectAt((int) e.getX(), (int) e.getY());
                //If selection is object, the object is highlighted and the main menu is shown
                if (selectedObject != null) {
                    selectedObject.saveState();
                    selectedObject.setTexture("white");
                    selectedObject.setTransparency(10);
                    mainMenu.setVisibility(View.VISIBLE);
                }
            }
            else{
                //only deselect, when not in ChangeMenu
                if (!isChangeMenu) {
                        selectedObject.restoreFromLastState();
                        selectedObject = null;
                        mainMenu.setVisibility(View.INVISIBLE);

                }
            }

            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    boolean result = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);

                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                }
            }, 1000L);

            return true;
        }
    }


    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        try
        {
            vuforiaAppSession.resumeAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }

        // Resume the GL view:
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }

        //TW Connection
        if(getConnectionState() == ConnectionState.DISCONNECTED) {
            try {
                connect(new VirtualThing[]{thing});
            } catch (Exception e) {
                Log.e(LOGTAG, "Restart with new settings failed.", e);
            }
        }

    }


    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        vuforiaAppSession.onConfigurationChanged();
    }


    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause()
    {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        try
        {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
    }

    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }


        System.gc();
    }

    // Initializes AR application components.
    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        renderer = new ARRenderer(this, vuforiaAppSession);
        mGlView.setRenderer(renderer);
    }


    private void startLoadingAnimation()
    {
        mainLayout = (RelativeLayout) View.inflate(this, com.main.collabar.R.layout.camera_overlay,
                null);

        mainLayout.setVisibility(View.VISIBLE);
        mainLayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mainLayout
                .findViewById(com.main.collabar.R.id.loading_indicator);

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(mainLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

    }


    // Methods to load and destroy tracking data.
    @Override
    public boolean doLoadTrackersData()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset == null)
            mCurrentDataset = objectTracker.createDataSet();

        if (mCurrentDataset == null)
            return false;

        if (!mCurrentDataset.load(
                mDatasetStrings.get(mCurrentDatasetSelectionIndex),
                STORAGE_TYPE.STORAGE_APPRESOURCE))
            return false;

        if (!objectTracker.activateDataSet(mCurrentDataset))
            return false;

        int numTrackables = mCurrentDataset.getNumTrackables();
        for (int count = 0; count < numTrackables; count++)
        {
            Trackable trackable = mCurrentDataset.getTrackable(count);
                trackable.startExtendedTracking();
            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);

        }

        return true;
    }


    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset != null && mCurrentDataset.isActive())
        {
            if (objectTracker.getActiveDataSet().equals(mCurrentDataset)
                    && !objectTracker.deactivateDataSet(mCurrentDataset))
            {
                result = false;
            } else if (!objectTracker.destroyDataSet(mCurrentDataset))
            {
                result = false;
            }

            mCurrentDataset = null;
        }

        return result;
    }


    @Override
    public void onInitARDone(SampleApplicationException exception)
    {

        if (exception == null)
        {
            initApplicationAR();

            renderer.mIsActive = true;

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            mainLayout.bringToFront();

            // Sets the layout background to transparent
            mainLayout.setBackgroundColor(Color.TRANSPARENT);

            mainLayout = (RelativeLayout) View.inflate(this, com.main.collabar.R.layout.activity_armain,
                    null);

            mainLayout.setVisibility(View.VISIBLE);

            // Adds the inflated layout to the view
            addContentView(mainLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));


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

            mainMenu = (LinearLayout) findViewById(com.main.collabar.R.id.gui_menu);
            symbolMenu = (LinearLayout) findViewById(com.main.collabar.R.id.gui_symbol);
            colorMenu = (LinearLayout) findViewById(com.main.collabar.R.id.gui_color);

            btnLogout = (ImageButton) findViewById(com.main.collabar.R.id.btnLogout);

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

            //Removes the client as listener from the server, empties Object3D-Container and starts login Activity
            btnLogout.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        ValueCollection params = new ValueCollection();
                        params.put("mac",new StringPrimitive(getMACAddress()));
                        client.invokeService(RelationshipTypes.ThingworxEntityTypes.Things,
                                "Assembly", "RemoveListener", params, 10000);
                        client.shutdown();
                        PartContainer.resetContainer();
                        Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                        startActivity(i);
                        finish();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });


            try
            {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException e)
            {
                Log.e(LOGTAG, e.getString());
            }

            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            if (result)
                mContAutofocus = true;
            else
                Log.e(LOGTAG, "Unable to enable continuous autofocus");

            loadDataFromThingWorx();

        } else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }


    }


    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        ARMain.this);
                builder
                        .setMessage(errorMessage)
                        .setTitle("Initialization Error")
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int id)
                                    {
                                        finish();
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


    @Override
    public void onVuforiaUpdate(State state)
    {
        if (mSwitchDatasetAsap)
        {
            mSwitchDatasetAsap = false;
            TrackerManager tm = TrackerManager.getInstance();
            ObjectTracker ot = (ObjectTracker) tm.getTracker(ObjectTracker
                    .getClassType());
            if (ot == null || mCurrentDataset == null
                    || ot.getActiveDataSet() == null)
            {
                Log.d(LOGTAG, "Failed to swap datasets");
                return;
            }

            doUnloadTrackersData();
            doLoadTrackersData();
        }
    }


    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;

        // Trying to initialize the image tracker
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
        {
            Log.e(
                    LOGTAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }
        return result;
    }


    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.start();

        return result;
    }


    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();

        return result;
    }


    @Override
    public boolean doDeinitTrackers()
    {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());

        return result;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return gestureDetector.onTouchEvent(event);
    }

    private void showToast(String text)
    {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    //to set the Buttons to Main menu
    private void setMainMenu(){
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
    private void setChangeMenu(){
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
    private void showCommentDialog(){
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
    private void showAcceptChangesDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Änderungen übernehmen?");

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showToast("Änderungen übernommen");
                try {
                    thing.sendChanges(selectedObject, userName);
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
                selectedObject.setTransparency(5);
            }
        });

        builder.show();
    }

    //Dialog to reset all manipulations to a building part
    private void showAcceptResetDialog(){
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
                selectedObject.setTransparency(0);
                selectedObject.setTexture("silver");
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
            Log.e(LOGTAG,"Failed to load changes from ThingWorx",e);
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
                    Log.d("ARLOADDATA", "Failed to set Color or Symbol. " + e2.getMessage());
                }

                currPart.setComment(dataTable.getRow(i).getStringValue("comment"));
                currPart.setAuthors(dataTable.getRow(i).getStringValue("colorAuthor"),
                        dataTable.getRow(i).getStringValue("symbolAuthor"), dataTable.getRow(i).getStringValue("commentAuthor"));
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    //Automatic switching between different assemblies is not implemented yet.
    // New vuforia target and 3D Modell must be integrated manually
    public ARClientThing getThing(){
        return thing;
    }

    public void initView(){
        gestureDetector = new GestureDetector(this, new GestureListener());

        vuforiaAppSession = new SampleApplicationSession(this);

        //Kreuz10 fuer hydraulische Pumpe
        //mDatasetStrings.add("Kreuz10.xml");
        mDatasetStrings.add("CURIOSITY.xml");
        vuforiaAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
                "droid");
    }

    public void connectThing(){
    // Create your Virtual Thing and bind it to your android controls
        try {
            disconnect();
            thing = new ARClientThing("ARClientThing", "Augmented Reality Client Thing", client);

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
            Log.e(LOGTAG, "Failed to initalize with error.", e);
            onConnectionFailed("Failed to initalize with error : " + e.getMessage());
        }
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

    //Sensor data has to be retrieved from server. All retrivied data will be shown in a dialog
    public void retrieveSensorData(){
        if (selectedObject != null) {
            try {
                ValueCollection params = new ValueCollection();
                params.put("part", new StringPrimitive(selectedObject.getName()));
                InfoTable dataTable = client.invokeService(RelationshipTypes.ThingworxEntityTypes.Things,
                        "Assembly", "RetrieveSensorData", params, 10000);
                Map<String,String> sensorList = new HashMap<String,String>();

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

