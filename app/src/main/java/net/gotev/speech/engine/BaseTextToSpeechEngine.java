package net.gotev.speech.engine;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import net.gotev.speech.TextToSpeechCallback;
import net.gotev.speech.TtsProgressListener;
import net.gotev.speech.Logger;

import java.util.*;

public class BaseTextToSpeechEngine implements TextToSpeechEngine {

    private TextToSpeech mTextToSpeech;
    private TextToSpeech.OnInitListener mTttsInitListener;
    private UtteranceProgressListener mTtsProgressListener;
    private float mTtsRate = 1.0f;
    private float mTtsPitch = 1.0f;
    private Locale mLocale = Locale.getDefault();
    private Voice voice;

    private int mTtsQueueMode = TextToSpeech.QUEUE_FLUSH;
    private int mAudioStream = TextToSpeech.Engine.DEFAULT_STREAM;

    private final Map<String, TextToSpeechCallback> mTtsCallbacks = new HashMap<>();

    @Override
    public void initTextToSpeech(Context context) {
        if (mTextToSpeech != null) {
            return;
        }

        mTtsProgressListener = new TtsProgressListener(context, mTtsCallbacks);
        mTextToSpeech = new TextToSpeech(context.getApplicationContext(), mTttsInitListener);
        mTextToSpeech.setOnUtteranceProgressListener(mTtsProgressListener);
        mTextToSpeech.setLanguage(mLocale);
        mTextToSpeech.setPitch(mTtsPitch);
        mTextToSpeech.setSpeechRate(mTtsRate);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (voice == null) {
                voice = mTextToSpeech.getDefaultVoice();
            }
            mTextToSpeech.setVoice(voice);
        }
    }

    @Override
    public boolean isSpeaking() {
        if (mTextToSpeech == null) {
            return false;
        }

        return mTextToSpeech.isSpeaking();
    }

    public void setOnInitListener(TextToSpeech.OnInitListener onInitListener) {
        this.mTttsInitListener = onInitListener;
    }

    @Override
    public void setLocale(Locale locale) {
        mLocale = locale;
        if (mTextToSpeech != null) {
            mTextToSpeech.setLanguage(locale);
        }
    }

    @Override
    public void say(String message, TextToSpeechCallback callback) {
        final String utteranceId = UUID.randomUUID().toString();

        if (callback != null) {
            mTtsCallbacks.put(utteranceId, callback);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(mAudioStream));
            mTextToSpeech.speak(message, mTtsQueueMode, params, utteranceId);
        } else {
            final HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(mAudioStream));
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            mTextToSpeech.speak(message, mTtsQueueMode, params);
        }
    }

    @Override
    public void shutdown() {
        if (mTextToSpeech != null) {
            try {
                mTtsCallbacks.clear();
                mTextToSpeech.stop();
                mTextToSpeech.shutdown();
            } catch (final Exception exc) {
                Logger.error(getClass().getSimpleName(), "Warning while de-initing text to speech", exc);
            }
        }
    }

    @Override
    public void setTextToSpeechQueueMode(int mode) {
        mTtsQueueMode = mode;
    }

    @Override
    public void setAudioStream(int audioStream) {
        mAudioStream = audioStream;
    }

    @Override
    public void stop() {
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
        }
    }

    @Override
    public void setPitch(float pitch) {
        mTtsPitch = pitch;
        if (mTextToSpeech != null) {
            mTextToSpeech.setPitch(pitch);
        }
    }

    @Override
    public void setSpeechRate(float rate) {
        mTtsRate = rate;
        if (mTextToSpeech != null) {
            mTextToSpeech.setSpeechRate(rate);
        }
    }

    @Override
    public void setVoice(Voice voice) {
        this.voice = voice;
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 21) {
            mTextToSpeech.setVoice(voice);
        }
    }

    @Override
    public List<Voice> getSupportedVoices() {
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 23) {
            Set<Voice> voices = mTextToSpeech.getVoices();
            ArrayList<Voice> voicesList = new ArrayList<>(voices.size());
            voicesList.addAll(voices);
            return voicesList;
        }

        return new ArrayList<>(1);
    }

    @Override
    public Voice getCurrentVoice() {
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 23) {
            return mTextToSpeech.getVoice();
        }

        return null;
    }
}
