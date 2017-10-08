package com.main.collabar;

/*
* This class renders the image of the 3D-Model of the assembly.
* It is based on a solution to integrate Vuforia with jPCT-AE which can be found here:
* https://github.com/TheMaggieSimpson/Vuforia559_jPCT-AE (class ImageTargetRenderer.java)
*
* */

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.threed.jpct.Camera;
import com.threed.jpct.Config;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Interact2D;
import com.threed.jpct.Light;
import com.threed.jpct.Loader;
import com.threed.jpct.Object3D;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.BitmapHelper;
import com.threed.jpct.util.MemoryHelper;
import com.vuforia.CameraCalibration;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Vec2F;
import com.vuforia.Vuforia;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.vuforia.util.SampleApplicationSession;
import com.vuforia.util.SampleMath;
import com.vuforia.util.LoadingDialogHandler;



public class ARRenderer implements GLSurfaceView.Renderer
{
    private static final String LOGTAG = "ImageTargetRenderer";

    private SampleApplicationSession vuforiaAppSession;
    private ARMain mActivity;
    private Renderer renderer;
    private float[] modelViewMat;
    private float fov;
    private float fovy;

    private World world;
    private Light sun;
    private Camera cam;
    private FrameBuffer frameBuffer;
    private Object3D symbolPlane;
    private Object3D commentPlane;
    boolean mIsActive = false;

    public ARRenderer(ARMain activity, SampleApplicationSession session){

        mActivity = activity;
        vuforiaAppSession = session;

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
            Log.d(LOGTAG, "Loading Textures failed.");
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
                world.addObject(part);

                symbolPlane = part.getSymbolPlane();
                world.addObject(symbolPlane);
                commentPlane = part.getCommentPlane();
                world.addObject(commentPlane);
            }

        }
        catch (IOException ex)
        {
            Log.d(LOGTAG, "Loading Model failed.");
        }

        cam = world.getCamera();

        //sun as main light source is set
        SimpleVector sv = SimpleVector.ORIGIN;
        sv.y += 100;
        sv.z += 100;
        sun.setPosition(sv);
        MemoryHelper.compact();
    }


    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive) {
            return;
        }
        // Call our function to render content from SampleAppRenderer class
        renderFrame();
        updateCamera();

        world.renderScene(frameBuffer);
        world.draw(frameBuffer);
        frameBuffer.display();
    }




    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        initRendering();
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();

    }


    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        if (frameBuffer != null) {
            frameBuffer.dispose();
        }
        frameBuffer = new FrameBuffer(width, height);
        Config.viewportOffsetAffectsRenderTarget = true;


        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);

    }


    // Function for initializing the renderer.
    private void initRendering()
    {
        renderer = Renderer.getInstance();
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);

        // Hide the Loading Dialog
        mActivity.loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);

    }


    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    public void renderFrame()
    {
        frameBuffer.clear();
        // clear color and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        // get the state, and mark the beginning of a rendering section
        State state = renderer.begin();
        // explicitly render the video background
        renderer.drawVideoBackground();

        CameraCalibration camCalibration = com.vuforia.CameraDevice.getInstance().getCameraCalibration();
        Vec2F size = camCalibration.getSize();
        Vec2F focalLength = camCalibration.getFocalLength();
        float fovyRadians = (float) (2 * Math.atan(0.5f * size.getData()[1] / focalLength.getData()[1]));
        float fovRadians = (float) (2 * Math.atan(0.5f * size.getData()[0] / focalLength.getData()[0]));

        setFov(fovRadians);
        setFovy(fovyRadians);


        // did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            // get the trackable
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();
            //printUserData(trackable);

            Matrix44F modelViewMatrix = Tool.convertPose2GLMatrix(result.getPose());
            Matrix44F inverseMV = SampleMath.Matrix44FInverse(modelViewMatrix);
            Matrix44F invTranspMV = SampleMath.Matrix44FTranspose(inverseMV);

            updateModelviewMatrix(invTranspMV.getData());

        }
        // hide the objects when the targets are not detected
        if (state.getNumTrackableResults() == 0) {
            float m [] = {
                    1,0,0,0,
                    0,1,0,0,
                    0,0,1,0,
                    0,0,-10000,1
            };
            updateModelviewMatrix(m);
        }

        renderer.end();

    }

    private void updateModelviewMatrix(float mat[]) {
        modelViewMat = mat;
        mActivity.getThing().setMatrix(mat);
    }


    // The view onto the model (camera) is recalibrated using the data from the trackable
    private void updateCamera() {
        if (modelViewMat != null) {
            float[] m = modelViewMat;

            final SimpleVector camUp = new SimpleVector(-m[4], -m[5], -m[6]);
            final SimpleVector camDirection = new SimpleVector(m[8], m[9], m[10]);
            final SimpleVector camPosition = new SimpleVector(m[12], m[13], m[14]);

            cam.setOrientation(camDirection, camUp);
            cam.setPosition(camPosition);

            sun.setPosition(cam.getPosition());

            cam.setFovAngle(fov);
            cam.setYFovAngle(fovy);
        }
    }

    private void setFov(float fov) {
        this.fov = fov;
    }

    private void setFovy(float fovy) {
        this.fovy = fovy;
    }

    // The two-dimensional coordinates from the user's gesture is transformed into a three-dimensional vector
    //It is then checked if and which building part (Object3D) the vector cuts
    public BuildingPart selectObjectAt( int mouseX, int mouseY){
        SimpleVector ray = Interact2D.reproject2D3DWS(cam, frameBuffer, mouseX, mouseY).normalize();
        Object[] res = world.calcMinDistanceAndObject3D(cam.getPosition(), ray, 10000F);
        if (res==null || res[1] == null || res[0] == (Object)Object3D.RAY_MISSES_BOX) {
            Log.d("SELECTION", "You missed! x="+mouseX+" y="+mouseY);
            return null;
        }

        BuildingPart obj = (BuildingPart) res[1];
        Log.d("SELECTION", "x="+mouseX+" y="+mouseY+" id="+obj.getID()+" name="+obj.getName());

        return obj;
    }



}
