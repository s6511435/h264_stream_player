package com.ken.smartguard.video_stream.video_player.video_decoder;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;

import com.ken.smartguard.BuildConfig;
import com.ken.smartguard.interfaces.Closeable;
import com.ken.smartguard.interfaces.Pauseable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

public class H264Decoder implements Pauseable, Closeable
{
    private static final String DEBUG_TAG = "Ken-H264-Decoder";

    private static final String MEDIA_TYPE = "video/avc";
    private static final int DEFAULT_VIDEO_WIDTH = 640;
    private static final int DEFAULT_VIDEO_HEIGHT = 352;

    private final OnVideoAvailableCallback onVideoAvailableCallback;
    private final OnDecoderErrorCallback onDecoderErrorCallback;

    private MediaCodec videoDecoder;
    private final MediaFormat videoFormat;
    private SurfaceHolder currentSurfaceHolder;
    private final SurfaceHolder defaultSurfaceHolder;

    private boolean renderingEnabled = false;
    private boolean isPaused = true;
    private boolean isClosed = true;
    private Rect currentVideoSize;

    public H264Decoder(SurfaceHolder defaultSurfaceHolder, OnVideoAvailableCallback onVideoAvailableCallback, OnDecoderErrorCallback onDecoderErrorCallback)
    {
        this.onVideoAvailableCallback = onVideoAvailableCallback;
        this.onDecoderErrorCallback = onDecoderErrorCallback;

        this.videoFormat = MediaFormat.createVideoFormat(MEDIA_TYPE, DEFAULT_VIDEO_WIDTH, DEFAULT_VIDEO_HEIGHT);
        videoFormat.setByteBuffer("csd-0", ByteBuffer.wrap(H264VideoUtil.DEFAULT_SPS));
        videoFormat.setByteBuffer("csd-1", ByteBuffer.wrap(H264VideoUtil.DEFAULT_PPS));

        this.defaultSurfaceHolder = defaultSurfaceHolder;
        this.currentSurfaceHolder = defaultSurfaceHolder;

        try
        {
            videoDecoder = MediaCodec.createDecoderByType(MEDIA_TYPE); //throws IOException
            isClosed = false; //this will stays false if IOException thrown during creating of videoDecoder
            Log.d(DEBUG_TAG, "Decoder created.");
        }
        catch (IOException exception)
        {
            Log.e(DEBUG_TAG, "Decoder throws error: " + exception.toString());
            onDecoderErrorCallback.accept(exception);
        }
    }

    /**
         * Configure decoder and start decoding with default surfaceHolder
         * @param dataBuffer The data-buffer contains stream-video data.
         */
    public void start(BlockingQueue<ByteBuffer> dataBuffer)
    {
        start(dataBuffer, currentSurfaceHolder);
    }

    /**
         * Configure decoder and start decoding with the given surfaceHolder
         * @param dataBuffer The data-buffer contains stream-video data.
         * @param surfaceHolder The surfaceHolder of the surface that the decoder will render video on.
         */
    public void start(BlockingQueue<ByteBuffer> dataBuffer, SurfaceHolder surfaceHolder)
    {
        if(BuildConfig.DEBUG)
            if(!isPaused || isClosed || videoDecoder == null) // requirement to pass: resumed, paused, not closed
                throw new AssertionError("Decoder was not paused or is already closed.");

        isPaused = false;
        currentSurfaceHolder = surfaceHolder;
        final H264DecodeEventHandler decoderEventHandler = new H264DecodeEventHandler(dataBuffer);

        videoDecoder.configure(videoFormat, surfaceHolder.getSurface(), null, MediaCodec.CRYPTO_MODE_UNENCRYPTED);
        videoDecoder.setCallback(decoderEventHandler);
        Log.d(DEBUG_TAG, "Decoder configs initialized.");

        videoDecoder.start();
    }

    /**
         *  Play the video on the  specified surface of the given surfaceHolder.
         * @param surfaceHolder The surfaceHolder with the specified surface, null to use default surfaceHolder
         */
    public void setSurfaceHolder(SurfaceHolder surfaceHolder)
    {
        if(BuildConfig.DEBUG)
            if(isPaused || isClosed || videoDecoder == null) // requirement to pass: resumed, not paused, not closed
                throw new AssertionError("SetSurfaceHolder, Decoder was paused or is already closed.");

        currentSurfaceHolder = surfaceHolder;
        final SurfaceHolder givenHolder = surfaceHolder == null ? defaultSurfaceHolder : surfaceHolder;
        videoDecoder.setOutputSurface(givenHolder.getSurface());
    }

    @Override
    public void pause()
    {
        if(!isPaused && !isClosed)
        {
            if(BuildConfig.DEBUG)
                if(videoDecoder == null) // requirement to pass: resumed, (not paused: handled), (not closed: handled)
                    throw new AssertionError("Decoder was already paused or closed.");

            isPaused = true;
            Log.d(DEBUG_TAG, "Pausing decoder");
            videoDecoder.stop();
        }
    }

    @Override
    public void close()
    {
        if(!isPaused)
            pause();

        isClosed = true;

        if(videoDecoder != null)
        {
            Log.d(DEBUG_TAG, "Releasing decoder");
            videoDecoder.release();
        }

        videoDecoder = null;
    }

    public boolean isDecoderCreated()
    {
        return videoDecoder != null;
    }

    private class H264DecodeEventHandler extends MediaCodec.Callback
    {
        private final static String DEBUG_TAG = "Ken-HW-Decoder";

        private final BlockingQueue<ByteBuffer> dataBuffer;
        private final byte[] emptyBuffer = new byte[]{};

        private int frameCounts;

        /* default */ H264DecodeEventHandler(BlockingQueue<ByteBuffer> dataBuffer)
        {
            this.dataBuffer = dataBuffer;
        }

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index)
        {
            //get a buffered frame
            final ByteBuffer frame = dataBuffer.poll();
            final ByteBuffer inputBuffer = codec.getInputBuffer(index);
            int dataLength = 0;

            if(inputBuffer != null)
            {
                if(frame == null)
                    inputBuffer.put(emptyBuffer);
                else
                {
                    dataLength = frame.remaining();
                    inputBuffer.put(frame);
                }

                //queue the frame into input buffer waiting for being decoded
                codec.queueInputBuffer(index, 0, dataLength, 0,0);
            }
        }


        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info)
        {
            frameCounts++;
            if(frameCounts == 1)
                onVideoAvailableCallback.run();

            if(!renderingEnabled)
                Log.w(DEBUG_TAG, "Skipped a frame.");
            codec.releaseOutputBuffer(index, renderingEnabled);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException exception)
        {
            Log.e(DEBUG_TAG, "Error:" + exception.getDiagnosticInfo());
            onDecoderErrorCallback.accept(exception);
            //todo test this
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format)
        {
            final int videoNewWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            final int videoNewHeight = format.getInteger(MediaFormat.KEY_HEIGHT);

            currentVideoSize = new Rect(0,0 , videoNewWidth, videoNewHeight);

            Log.d(DEBUG_TAG, "Video format changed. New width: " + videoNewWidth + "; New height: " + videoNewHeight);

            //scale video to current rendering surface
            final double scale = (double) currentSurfaceHolder.getSurfaceFrame().width() / videoNewWidth;
            Log.d(DEBUG_TAG, "Scaling video with width: " + (int)(videoNewWidth * scale) + " height: " + (int)(videoNewHeight * scale));
            currentSurfaceHolder.setFixedSize((int)(videoNewWidth * scale), (int)(videoNewHeight * scale));
        }
    }

    public void enableRendering(boolean enable)
    {
        renderingEnabled = enable;
    }

    public Rect getCurrentVideoSize()
    {
        return currentVideoSize;
    }

    @FunctionalInterface
    public interface OnVideoAvailableCallback
    {
        void run();
    }

    @FunctionalInterface
    public interface OnDecoderErrorCallback
    {
        void accept(Exception exception);
    }
}
