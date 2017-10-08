package com.main.collabar;

/*
* This class renders the image of the 3D-Model of the assembly. Movements that are recorded in the RemoteMain Activity are
* processed here
* */

import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.threed.jpct.Camera;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Interact2D;
import com.threed.jpct.Light;
import com.threed.jpct.Loader;
import com.threed.jpct.Matrix;
import com.threed.jpct.Object3D;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.BitmapHelper;
import com.threed.jpct.util.MemoryHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class RemoteRenderer implements GLSurfaceView.Renderer{

    private RemoteMain mActivity;
    private float[] modelViewMat;
    private int renderCount = 0;
    private boolean selfControl = true;
    final private float[] standardMatrix =  {
            1,0,0,0,
            0,1,0,0,
            0,0,1,0,
            0,0,-10000,1
    };

    //should never exeed 200 or fall under 0
    float zoomdistance = 200;

    private World world;
    private Light sun;
    private Camera cam;
    private FrameBuffer frameBuffer;
    private Object3D symbolPlane;
    private Object3D commentPlane;
    private Object3D wholeModel;
    //Stores the original Position and Direction of the Camera
    private SimpleVector[] camOrigin = new SimpleVector[3];

    private int intColor = Color.parseColor("#0000ff");


    public RemoteRenderer(RemoteMain activity){

        mActivity = activity;

        world = new World();
        world.setAmbientLight(20, 20, 20);
        world.setClippingPlanes(2.0f, 3000.0f);


        sun = new Light(world);
        sun.setIntensity(250, 250, 250);

        // Create the textures
        try {
            TextureManager.getInstance().addTexture("red", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.red)), 64, 64)));
            TextureManager.getInstance().addTexture("yellow", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.yellow)), 64, 64)));
            TextureManager.getInstance().addTexture("green", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.green)), 64, 64)));
            TextureManager.getInstance().addTexture("white", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.white)), 64, 64)));
            TextureManager.getInstance().addTexture("silver", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.silver)), 64, 64)));
            TextureManager.getInstance().addTexture("right", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.right)), 64, 64)));
            TextureManager.getInstance().addTexture("wrong", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.wrong)), 64, 64)));
            TextureManager.getInstance().addTexture("danger", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.settings)), 64, 64)));
            TextureManager.getInstance().addTexture("exclaim", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.exclaim)), 64, 64)));
            TextureManager.getInstance().addTexture("question", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.question)), 64, 64)));
            TextureManager.getInstance().addTexture("comment", new Texture(BitmapHelper.rescale(BitmapHelper.convert(
                    mActivity.getResources().getDrawable(com.main.collabar.R.drawable.comment)), 64, 64)));
        }
        catch (Exception e){
            e.printStackTrace();
        }

        try {
            // Automatic switching between different assemblies is not implemented yet.
            // New vuforia target and 3D Modell must be integrated manually

            //Model serialized through external application (DeSerializer_jPCT, java application as part of project)
            // is loaded into world and two planes for symbols and comments are added to each building part

            //InputStream inputStream = mActivity.getAssets().open("pumpe.ser");
            InputStream inputStream = mActivity.getAssets().open("curiosity.ser");
            Object3D[] model = Loader.loadSerializedObjectArray(inputStream);

            for (int i = 0; i < model.length; i++) {
                BuildingPart part = new BuildingPart(model[i], mActivity);

                part.setName(model[i].getName());
                PartContainer.getInstance().addObject(part);

                part.setLighting(Object3D.LIGHTING_ALL_ENABLED);
                world.addObject(part);
                symbolPlane = part.getSymbolPlane();
                world.addObject(symbolPlane);
                commentPlane = part.getCommentPlane();
                world.addObject(commentPlane);
            }

        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        cam = world.getCamera();
        cam.moveCamera(Camera.CAMERA_MOVEIN, 200);
        cam.lookAt(new SimpleVector(0,0,0));

        //To adjust for the 180° shift in the jPCT coordinate system
        cam.rotateZ((float) (-1f * Math.PI));

        //save the original position of the camera in a vector
        camOrigin[0] = cam.getPosition();
        camOrigin[1] = cam.getDirection();
        camOrigin[2] = cam.getUpVector();

        //sun as main light source is set
        sun.setPosition(new SimpleVector(100,100,100));
        MemoryHelper.compact();
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {

        if (frameBuffer != null) {
            frameBuffer.dispose();
        }
        frameBuffer = new FrameBuffer(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        renderFrame();

        updateCamera();

        world.renderScene(frameBuffer);
        world.draw(frameBuffer);
        frameBuffer.display();
    }


    public void renderFrame()
    {
        frameBuffer.clear();
        // clear color and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        updateModelviewMatrix();
    }

    private void updateCamera() {
        //Camera movement controlled by this client
        if(selfControl){
                //depends on Listener of Main class
            sun.setPosition(cam.getPosition());
        }
        //Camera movement controlled by AR-client
        else {
            if (modelViewMat != null) {
                //If there is no signal from the AR-Client
                if (Arrays.equals(modelViewMat, standardMatrix)) {
                    setStandardView();
                    sun.setPosition(cam.getPosition());
                } else {
                    renderCount++;
                    //each 5th frame
                    if (renderCount >= 5) {
                        float[] m = modelViewMat;

                        //to guarantee a smooth movement between the current camera position and the position received from the
                        //AR client's pose, the difference between the two poses/matrices is calculated and the new camera view
                        //is positioned somewhere in between those two poses, depending on the factor of the functions
                        //calculateNewPoseVector for each vector. Here the factor is 2, meaning the camera is moved 1/4th between old and new position.
                        //The camera is also calibrated to render the movement only every 5th frame, making the movement smoother
                        //to the eye (see above).

                        SimpleVector camUpNew = new SimpleVector(-m[4], -m[5], -m[6]);
                        SimpleVector camDirectionNew = new SimpleVector(m[8], m[9], m[10]);
                        SimpleVector camPositionNew = new SimpleVector(m[12], m[13], m[14]);

                        SimpleVector camUpOld = cam.getUpVector();
                        SimpleVector camDirectionOld = cam.getDirection();
                        SimpleVector camPositionOld = cam.getPosition();

                        final SimpleVector camDirection = calculateNewPoseVector(camDirectionOld, camDirectionNew, 2);
                        final SimpleVector camUp = calculateNewPoseVector(camUpOld, camUpNew, 2);
                        final SimpleVector camPosition = calculateNewPoseVector(camPositionOld, camPositionNew, 2);

                        cam.setOrientation(camDirection, camUp);
                        cam.setPosition(camPosition);

                        sun.setPosition(cam.getPosition());
                        renderCount = 0;
                    }
                }
            }
        }
    }

    //receive pose of ARClient from server
    private void updateModelviewMatrix() {
        float [] tempArray = mActivity.getThing().getMatrix();
        if (tempArray.length > 15) {
            modelViewMat = tempArray;
        }
        else {
            modelViewMat = standardMatrix;
        }


    }

    // The two-dimensional coordinates from the user's gesture is transformed into a three-dimensional vector
    //It is then checked if and which building part (Object3D) the vector cuts
    public BuildingPart selectObjectAt(int mouseX, int mouseY){
        SimpleVector coordinates = Interact2D.reproject2D3DWS(cam, frameBuffer, mouseX, mouseY).normalize();
        Object[] result = world.calcMinDistanceAndObject3D(cam.getPosition(), coordinates, 10000F);
        if (result == null || result[1] == null || result[0] == (Object)Object3D.RAY_MISSES_BOX) {
            return null;
        }
        BuildingPart obj = (BuildingPart) result[1];
        return obj;
    }

    //new vector is calculated taking half of the sum of two vectors. This is done n times, depending
    //on the parameter iterations
    public SimpleVector calculateNewPoseVector(SimpleVector oldVec, SimpleVector newVec, int iterations){
        for (int i = 1; i <= iterations; i++) {
            newVec = oldVec.calcAdd(newVec);
            newVec.scalarMul(0.5f);
        }
        return newVec;
    }


    // The camera is moved around the assembly by rotating it around a vector made from the gesture movement (start and end point)
    // To keep the camera focus on th middle of the assembly, the camera is panning to the middle of the assembly, then the
    //camera rotation is executed and the camera is panning out the same distance again
    public void moveAssembly(float touchTurn, float touchTurnUp){
        SimpleVector line = new SimpleVector();
        Matrix m = new Matrix();
        line.set(-touchTurn, 0, touchTurnUp);
        m = line.normalize(line).getRotationMatrix(m);
        m.rotateAxis(m.getXAxis(), (float) -Math.PI / 2f);
        cam.moveCamera(Camera.CAMERA_MOVEIN, zoomdistance);
        cam.rotateCameraAxis(m.invert3x3().getXAxis(), -line.length() / 25f); //Divide by 25 to slow movement
        cam.moveCamera(Camera.CAMERA_MOVEOUT, zoomdistance);
    }

    //The camera is zoomed in onto a scecific building part, between specific bounds (here 50 and 200)

    public void zoom(float scale, BuildingPart select){
        if (select != null) {
            //Save current UpVector to keep top of the view constant after cam.lookAt
            SimpleVector upVector = cam.getUpVector();
            cam.lookAt(select.getCenter());
            cam.setOrientation(cam.getDirection(), upVector);
        }
        if (scale > 1) {
            if (zoomdistance >= 50) {
                world.checkCameraCollision(Camera.CAMERA_MOVEIN, scale * 3, Camera.DONT_SLIDE);
                if (zoomdistance - scale * 3 > 50) {
                    zoomdistance -= scale * 3;
                }
            }
        } else if (zoomdistance < 200) {
            world.checkCameraCollision(Camera.CAMERA_MOVEOUT, (1 / scale) * 3, Camera.DONT_SLIDE);
            zoomdistance += (1 / scale) * 3;
        }
        Log.d("ZOOM", "Zoomfaktor: " + String.valueOf(scale) + ", zoomdistance: " + String.valueOf(zoomdistance));
    }


    public boolean isSelfControled(){
        return selfControl;
    }

    public void setSelfControled(boolean selfControl) {
        this.selfControl = selfControl;
    }

    //This function resets the camera view onto the assembly
    public void setStandardView(){
        zoomdistance = 200;
        cam.setPosition(camOrigin[0]);
        cam.setOrientation(camOrigin[1],camOrigin[2]);
    }
}
