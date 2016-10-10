package com.mediacodecexample;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;

import android.view.View;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    public static final int TEX_WIDTH = 1280;
    public static final int TEX_HEIGHT = 720;

    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final String VIDEO_PATH = "/storage/emulated/0/video.mp4";
    private static final int OUTPUT_VIDEO_BIT_RATE = 2000000; // 2Mbps
    public static final int OUTPUT_VIDEO_FRAME_RATE = 15; // 15fps
    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10; // 10 seconds between I-frames

    private MediaCodec encoder;
    private MediaMuxer muxer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout cl = (LinearLayout) findViewById(R.id.activity_main);


        /* SETTING UP VIDEO FORMAT */
        MediaCodecInfo codecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE);
        int colorFormat = selectColorFormat(codecInfo, OUTPUT_VIDEO_MIME_TYPE);
        printColors(codecInfo, OUTPUT_VIDEO_MIME_TYPE);
        printCodecs();

        MediaFormat outputVideoFormat =
                MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, TEX_WIDTH, TEX_HEIGHT);
        // Set some properties. Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        outputVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE);
        outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE);
        outputVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL);
        //outputVideoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 500000);

        System.out.print(
                "\n------------------------------\n" +
                        "MIME_TYPE: " + OUTPUT_VIDEO_MIME_TYPE + "\n" +
                        "BIT_RATE: " + OUTPUT_VIDEO_BIT_RATE + "\n" +
                        "FRAME_RATE: " + OUTPUT_VIDEO_FRAME_RATE + "\n" +
                        "I_FRAME_INTERVAL: " + OUTPUT_VIDEO_IFRAME_INTERVAL + "\n" +
                        " * SELECTED CODEC: " + codecInfo.getName() + "\n" +
                        " * SELECTED COLOR FORMAT: " + colorFormat + "\n" +
                        "------------------------------\n"
        );

        /* SETTING UP MEDIACODEC */
        try {
            encoder = MediaCodec.createByCodecName(codecInfo.getName());
        }
        catch (IOException ex) {
            Log.e("debug", "I/O ERROR WHILE CREATING MediaCodec!");
            return;
        }
        encoder.configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        /* SETTING UP MUXER */
        try {
            muxer = new MediaMuxer(VIDEO_PATH, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }
        catch (IOException ex) {
            Log.e("MainActivity", "ERROR: Failed to open video output file for write!");
            return;
        }

        /* STARTING CAPTURING THREAD */
        (new CapturingThread(cl, encoder, muxer)).start();
    }

    public void startCapturing(View v) {

    }

    private void printCodecs() {
        Log.i("debug", "PRINTING SUPPORTED CODECS:");
        int codecs = MediaCodecList.getCodecCount();
        for (int i=0; i < codecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            Log.i("debug", i + ": " + codecInfo.getName());
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no match was
     * found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private void printColors(MediaCodecInfo codecInfo, String mimeType) {
        Log.i("debug", "PRINTING SUPPORTED COLOR FORMATS:");
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            Log.i("debug", Integer.toString(colorFormat));
        }
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            //if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            //}
        }
        Log.e("debug", "Couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }
}
