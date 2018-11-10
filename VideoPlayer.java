package com.ken.smartguard.video_stream.video_player;

import android.media.MediaCodec;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import com.ken.smartguard.R;
import com.ken.smartguard.video_stream.utils.ScreenUtils;
import com.ken.smartguard.video_stream.video_player.video_decoder.H264Decoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

public class VideoPlayer
{
    private static final String DEBUG_TAG = "Ken-Video-Display";

    private final AppCompatActivity context;
    private final H264Decoder videoDecoder;

    private final View defaultDisplayContainer;
    private final SurfaceView defaultDisplay;
    private final View fullscreenOnToggle;
    private boolean isDefaultDisplayCreated;

    private final View fullscreenDisplayContainer;
    private final SurfaceView fullscreenDisplay;
    private final View fullscreenOffToggle;
    private boolean isFullscreenDisplayCreated;

    private final View initialView;
    private final View loadingView;
    private final View errorView;

    private final VideoPlayerCallbacks videoPlayerCallbacks;

    private boolean isFullscreenMode;

    public VideoPlayer(AppCompatActivity context, VideoPlayerCallbacks videoPlayerCallbacks)
    {
        this.context = context;
        this.videoPlayerCallbacks = videoPlayerCallbacks;

        this.defaultDisplayContainer = context.findViewById(R.id.default_player_container);
        this.defaultDisplay = context.findViewById(R.id.default_video_display);
        this.defaultDisplay.getHolder().addCallback(new VideoDisplayEventHandler(0,
                                                    new SurfaceCreatedEventHandler(),
                                                    holder -> {isDefaultDisplayCreated = false;}));

        this.fullscreenDisplayContainer = context.findViewById(R.id.fullscreen_video_display_holder);
        this.fullscreenDisplay = context.findViewById(R.id.fullscreen_video_display);
        this.fullscreenOffToggle = context.findViewById(R.id.video_fullscreen_toggle_fullscreen);

        fullscreenOnToggle = context.findViewById(R.id.video_fullscreen_toggle);
        fullscreenOnToggle.setOnClickListener( view ->
        {
            /* fullscreenOnToggle onClickListener */
            if(videoPlayerCallbacks.onFullscreenRequest() && !isFullscreenMode)
            {
                //screen switched to fullscreen mode, switch now player also to fullscreen mode
                Log.d(DEBUG_TAG, "Switching to fullscreen mode.");
                isFullscreenMode = true;

                defaultDisplay.setVisibility(View.GONE);
                defaultDisplayContainer.setVisibility(View.GONE);

                ScreenUtils.enableFullscreenMode(context, true);
            }
            else
                Log.d(DEBUG_TAG, "Fullscreen request is rejected.");
        });

        this.initialView = context.findViewById(R.id.stream_video_initializing);
        this.loadingView = context.findViewById(R.id.default_player_loading);
        this.errorView = context.findViewById(R.id.stream_error);

        this.videoDecoder = new H264Decoder(defaultDisplay.getHolder(), () ->
        {
            videoPlayerCallbacks.onVideoLoaded();

            Log.d(DEBUG_TAG, "Channel changed.");
        }, exception ->
        {
            if(exception instanceof IOException)
            {
                Log.e(DEBUG_TAG, "Decoder create error: " + exception.toString());
                Log.d(DEBUG_TAG, "Encountered unrecoverable error. Card crashed.");

                defaultDisplay.setVisibility(View.GONE);

                //set and show message
                //streamErrorText.setText(context.getResources().getString(R.string.stream_fatal_error));
                //error(); //todo replace with onFatalError.run()
                videoPlayerCallbacks.onDecoderFatalError();
            }
            else if(exception instanceof MediaCodec.CodecException)
            {
                Log.e(DEBUG_TAG, "Decoder decode error: " + exception.getLocalizedMessage());
                throw new RuntimeException("Not implemented"); //todo handle this
            }
        });
    }

    /**
         *  Set player in the initial state.
         */
    public void initial()
    {
        Log.d(DEBUG_TAG, "Initial state.");
        this.defaultDisplay.setVisibility(View.GONE);
        this.initialView.setVisibility(View.VISIBLE);

        this.errorView.setVisibility(View.GONE);
    }

    /**
         * Set player in loading state. E.g. waiting data from data-source.
         */
    public void loading()
    {
        this.loadingView.setVisibility(View.VISIBLE);
    }

    public void loaded()
    {
        this.loadingView.setVisibility(View.GONE);
        this.initialView.setVisibility(View.GONE);
        this.fullscreenOnToggle.setVisibility(View.VISIBLE);
    }

    private BlockingQueue<ByteBuffer> dataBuffer;

    /**
     * Start the player in playing mode with a buffer that the data will be written in from the custom data-source, and read out from the H264Decoder
     * @param dataBuffer The buffer contains video frames.
     */
    public void begin(BlockingQueue<ByteBuffer> dataBuffer)
    {
        this.defaultDisplay.setVisibility(View.VISIBLE);
        this.dataBuffer = dataBuffer;
        if(isDefaultDisplayCreated)
            startVideoPlaying();
        else
            defaultDisplay.setVisibility(View.VISIBLE);
    }

    private void startVideoPlaying()
    {
        if(dataBuffer != null)
        {
            videoDecoder.pause();
            videoDecoder.enableRendering(true);
            videoDecoder.start(dataBuffer);
        }
    }

    /**
         * Pause the video player. Triggered normally when activity paused.
         */
    public void pause()
    {
        videoDecoder.pause(); //pause the decoder
    }

    public void error()
    {
        videoDecoder.pause();
        this.initialView.setVisibility(View.GONE);
        this.defaultDisplay.setVisibility(View.GONE);

        this.errorView.setVisibility(View.VISIBLE);
    }

    /**
         *  Close the video player. Triggered normally when activity destroyed.
         */
    public void close()
    {
        videoDecoder.close();
    }

    public boolean handleBackKeyPress()
    {
        return false;
    }

    private class SurfaceCreatedEventHandler implements VideoDisplayEventHandler.OnSurfaceCreatedCallback
    {
        @Override
        public void accept(SurfaceHolder surfaceHolder)
        {
            Log.d(DEBUG_TAG, "Default Surface created");
            isDefaultDisplayCreated = true;

            //todo calculate from current video size
            surfaceHolder.setFixedSize(0, 0); //todo  surfaceHolder.setFixedSize(currentVideoDisplaySize.width(), currentVideoDisplaySize.height());

            startVideoPlaying();
        }
    }

    public interface VideoPlayerCallbacks
    {
        void onVideoLoaded();
        void onDecoderFatalError();
        boolean onFullscreenRequest();
    }
}