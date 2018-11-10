package com.ken.smartguard.video_stream.video_player;

import android.util.Log;
import android.view.SurfaceHolder;


public class VideoDisplayEventHandler implements SurfaceHolder.Callback
{
    private final static String DEBUG_TAG = "Ken-Surface";

    private final int initialHeight;
    private final OnSurfaceCreatedCallback onSurfaceCreatedCallback;
    private final OnSurfaceDestroyedCallback  onSurfaceDestroyedCallback;

    /**
     *
     * @param initialHeight -1 = original height of the display
     * @param onSurfaceCreatedCallback
     * @param onSurfaceDestroyedCallback
     */
    /* default */ VideoDisplayEventHandler( int initialHeight, OnSurfaceCreatedCallback onSurfaceCreatedCallback, OnSurfaceDestroyedCallback onSurfaceDestroyedCallback)
    {
        this.initialHeight = initialHeight;
        this.onSurfaceCreatedCallback = onSurfaceCreatedCallback;
        this.onSurfaceDestroyedCallback = onSurfaceDestroyedCallback;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        Log.d(DEBUG_TAG, "Surface created. Width: " + holder.getSurfaceFrame().width() + ", Height: " +  holder.getSurfaceFrame().height());
        final int height;
        if(initialHeight == -1)
            height = holder.getSurfaceFrame().height();
        else
            height = initialHeight;

        //holder.setFixedSize(holder.getSurfaceFrame().width(), height);
        onSurfaceCreatedCallback.accept(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        Log.d(DEBUG_TAG, "Surface changed. New width: " + width + " New height: " + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        Log.d(DEBUG_TAG, "Surface destroyed.");
        onSurfaceDestroyedCallback.accept(holder);
    }

    @FunctionalInterface
    public interface OnSurfaceCreatedCallback
    {
        void accept(SurfaceHolder holder);
    }

    @FunctionalInterface
    public interface OnSurfaceDestroyedCallback
    {
        void accept(SurfaceHolder holder);
    }
}