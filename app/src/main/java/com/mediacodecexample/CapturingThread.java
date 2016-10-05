package com.mediacodecexample;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.util.Log;
import android.widget.LinearLayout;

import java.nio.ByteBuffer;

/**
 * Note: frame with EndOfStream (EOS) flag will be ignored by encoder (it's better to send empty frame).
 */
public class CapturingThread extends Thread {

    private static final int NO_FRAMES = 180;
    private int frameCounter = 0;

    private LinearLayout cl;

    private MediaCodec encoder;
    private MediaMuxer muxer;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;

    public CapturingThread(LinearLayout cl, MediaCodec encoder, MediaMuxer muxer) {
        this.cl = cl;
        this.muxer = muxer;

        this.encoder = encoder;
        inputBuffers = encoder.getInputBuffers();
        outputBuffers = encoder.getOutputBuffers();
    }

    @Override
    public void run() {
        Log.i("debug", "Capturing thread starting. I'm going to encode " + NO_FRAMES + " frames.");

        int muxerTrack = -1;
        boolean done = false;
        while (!done) {
            Log.i("debug", "going to sleep.");
            try {
                Thread.sleep(17);
            } catch (InterruptedException ex) {}

            if (frameCounter < NO_FRAMES) {
                int bufferIndex = encoder.dequeueInputBuffer(-1);   // -1 for "infinite timeout"

                Log.i("debug", "Input buffer index received: " + bufferIndex);
                if (bufferIndex < 0) {
                    // either all in use, or we timed out during initial setup
                    Log.e("debug", "Encoder returned negative buffer index!!");
                    continue;
                }

                ByteBuffer buffer = inputBuffers[bufferIndex];
                buffer.clear();

                Bitmap bitmap = Bitmap.createBitmap(MainActivity.TEX_WIDTH, MainActivity.TEX_HEIGHT, Bitmap.Config.ALPHA_8);
                Canvas canvas = new Canvas(bitmap);

                cl.draw(canvas);

                Log.i("debug", "Bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight() + " (=" + bitmap.getByteCount() + " bytes) (buffer size: " + buffer.limit() + ")");
                bitmap.copyPixelsToBuffer(buffer);
                //bitmap.recycle();

                encoder.queueInputBuffer(
                        bufferIndex,
                        0,
                        //bitmap.getByteCount(),
                        bitmap.getAllocationByteCount(),
                        //buffer.limit(),
                        computePresentationTime(frameCounter),
                        frameCounter == (NO_FRAMES - 1) ? BUFFER_FLAG_END_OF_STREAM : BUFFER_FLAG_KEY_FRAME
                );

                frameCounter++;
            }

            ByteBuffer buffer;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            int bufferIndex = encoder.dequeueOutputBuffer(bufferInfo, -1);  // -1 for "infinite timeout"

            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.i("debug", "Encoder is not yet ready to return output buffer, proceeding...");
                continue;
            }
            else if (bufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // Not expected for an encoder.
                Log.d("debug", "video encoder: output buffers changed");
                outputBuffers = encoder.getOutputBuffers();
                continue;
            }
            else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Not expected for an encoder.
                Log.d("debug", "video encoder: output format changed");
                if (muxerTrack >= 0) {
                    Log.d("debug", "video encoder changed its output format again?");
                }
                continue;
            }
            else if (bufferIndex < 0) {
                Log.e("debug", "ERROR: Unexpected result from encoder.dequeueOutputBuffer: " + bufferIndex);
                break;
            }

            buffer = outputBuffers[bufferIndex];
            if (buffer == null) {
                Log.e("debug", "ERROR: Output buffer reference from encoder is null!");
                break;
            }

            // Adjusting the ByteBuffer values to match BufferInfo:
            buffer.position(bufferInfo.offset);
            buffer.limit(bufferInfo.offset + bufferInfo.size);

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.i("debug", "Received EOS from encoder.");
                done = true;
            }

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                Log.i("debug", "Codec config info received. Initializing Muxer...");
                muxerTrack = muxer.addTrack(encoder.getOutputFormat()); // Must be called before .start() !
                muxer.start();
            }
            else if (bufferInfo.size != 0) {
                Log.i("debug", "Output buffer received and validated successfully. I will pass it to Muxer now.");
                muxer.writeSampleData(muxerTrack, buffer, bufferInfo);
            }

            encoder.releaseOutputBuffer(bufferIndex, false);
        }

        Log.i("debug", "All frames passed to Muxer. Finishing...");
        muxer.stop();
        muxer.release();
        encoder.stop();
        encoder.release();
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / MainActivity.OUTPUT_VIDEO_FRAME_RATE;

    }
}